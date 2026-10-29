"""Fixed sandbox preflight. Never print tokens, request bytes or raw process diagnostics."""
import ctypes
import errno
import os
from pathlib import Path
import re
import resource
import signal
import socket
import stat
import subprocess
import sys

PROFILE = "cadence-evidence-renderer"
NAMESPACES = ("user", "net", "pid", "mnt", "ipc")
READONLY_PROC = ("/proc/bus", "/proc/fs", "/proc/irq", "/proc/sys", "/proc/sysrq-trigger")
MASKED_FILES = ("/proc/interrupts", "/proc/kcore", "/proc/keys", "/proc/latency_stats",
                "/proc/timer_list", "/proc/timer_stats", "/proc/sched_debug")
MASKED_DIRECTORIES = ("/proc/asound", "/proc/acpi", "/proc/scsi")
PARENT_PROTECTED_FILES = ("environ", "mem")
PARENT_PROTECTED_DIRECTORIES = ("fd", "fdinfo", "map_files")
PARENT_PROTECTED_LINKS = ("root", "cwd", "exe")
PARENT_NAMESPACE_FILES = ("ns/net", "ns/user", "ns/pid", "ns/ipc", "ns/mnt")


class ProbeFailure(Exception):
    pass


def protect_parent():
    # Child capabilities never grant CAP_SYS_PTRACE in the outer server user namespace.
    libc = ctypes.CDLL(None, use_errno=True)
    libc.prctl.argtypes = (ctypes.c_int, ctypes.c_ulong, ctypes.c_ulong,
                          ctypes.c_ulong, ctypes.c_ulong)
    libc.prctl.restype = ctypes.c_int
    if libc.prctl(4, 0, 0, 0, 0) != 0 or libc.prctl(3, 0, 0, 0, 0) != 0:
        raise ProbeFailure("parent-protection")
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def profile():
    try:
        return Path("/proc/self/attr/current").read_text().strip(" \t\r\n\x00")
    except OSError:
        # AppArmor is absent on Docker Desktop. An explicitly requested profile fails below.
        return ""


def status():
    return dict(line.split(":", 1) for line in Path("/proc/self/status").read_text().splitlines()
                if ":" in line)


def namespaces():
    return ",".join(os.readlink("/proc/self/ns/" + namespace) for namespace in NAMESPACES)


def failed(reason, exit_code=None):
    suffix = "" if exit_code is None else " exit=" + str(exit_code)
    print("namespace-probe failed: " + reason + suffix, file=sys.stderr)
    return 1


def denied(operation):
    # Close an unexpectedly successful open without reading even one sensitive byte.
    try:
        result = operation()
    except OSError as error:
        if error.errno in (errno.EACCES, errno.EPERM):
            return
        raise ProbeFailure("parent-access-check") from None
    if isinstance(result, int):
        os.close(result)
    raise ProbeFailure("parent-access-allowed")


def verify_parent_protection(parent_pid):
    # Thread-directory aliases must enforce the same protected address-space checks.
    prefixes = ("/proc/" + str(parent_pid) + "/",
                "/proc/" + str(parent_pid) + "/task/" + str(parent_pid) + "/")
    for index, prefix in enumerate(prefixes):
        for filename in PARENT_PROTECTED_FILES:
            denied(lambda name=filename: os.open(prefix + name, os.O_RDONLY | os.O_CLOEXEC))
        directories = PARENT_PROTECTED_DIRECTORIES if index == 0 else ("fd", "fdinfo")
        for directory in directories:
            denied(lambda name=directory: os.listdir(prefix + name))
        for link in PARENT_PROTECTED_LINKS:
            denied(lambda name=link: os.readlink(prefix + name))
        # Namespace readlink exposes only IDs on some kernels. Opening the namespace
        # handle is the security-sensitive operation and must be denied.
        for namespace in PARENT_NAMESPACE_FILES:
            denied(lambda name=namespace: os.open(prefix + name, os.O_RDONLY | os.O_CLOEXEC))


def filesystem_mounts():
    with open("/proc/self/mountinfo", encoding="ascii") as source:
        content = source.read(262145)
    if len(content) > 262144:
        raise ProbeFailure("mountinfo-size")
    mounts = {}
    for line in content.splitlines():
        fields = line.split()
        separator = fields.index("-")
        if separator < 6 or len(fields) < separator + 4:
            raise ProbeFailure("mountinfo-format")
        if fields[4] in ("/", "/tmp", "/dev/shm", "/dev/mqueue") and fields[4] in mounts:
            raise ProbeFailure("workspace-filesystem")
        mounts[fields[4]] = (fields[3], set(fields[5].split(",")),
                             fields[separator + 1], fields[separator + 3].split(","))
    return mounts


def proc_mounts():
    return {path: mounted[:3] for path, mounted in filesystem_mounts().items()
            if path == "/proc" or path.startswith("/proc/")}


def tmpfs_size(options):
    sizes = [value[5:] for value in options if value.startswith("size=")]
    if len(sizes) != 1 or not re.fullmatch(r"[1-9][0-9]{0,12}[kKmMgG]?", sizes[0]):
        raise ProbeFailure("workspace-filesystem")
    size = sizes[0]
    factor = {"k": 1024, "m": 1024 * 1024, "g": 1024 * 1024 * 1024}
    return int(size[:-1]) * factor[size[-1].lower()] if size[-1].isalpha() else int(size)


def verify_workspace_filesystem():
    # The launch contract forbids host binds. Filesystem metadata also rejects normal
    # host filesystems and subdirectory binds before any destructive cleanup can start.
    try:
        mounts = filesystem_mounts()
        root = mounts.get("/")
        if root is None or "ro" not in root[1] or "rw" in root[1]:
            raise ProbeFailure("workspace-filesystem")
        for path, mode, maximum in (("/tmp", "rw", 128 * 1024 * 1024),
                                    ("/dev/shm", "rw", 256 * 1024 * 1024),
                                    ("/dev/mqueue", "ro", 4096)):
            mounted = mounts.get(path)
            opposite = "ro" if mode == "rw" else "rw"
            if (mounted is None or mounted[0] != "/" or mounted[2] != "tmpfs"
                    or not {mode, "noexec", "nosuid", "nodev"} <= mounted[1]
                    or opposite in mounted[1] or tmpfs_size(mounted[3]) > maximum):
                raise ProbeFailure("workspace-filesystem")
            if path == "/dev/mqueue":
                modes = [option[5:] for option in mounted[3] if option.startswith("mode=")]
                if len(modes) != 1 or modes[0] not in ("0", "000", "0000"):
                    raise ProbeFailure("workspace-filesystem")
    except (OSError, ValueError, IndexError):
        raise ProbeFailure("workspace-filesystem") from None


def verify_mqueue_protection():
    path = "/dev/mqueue/cadence-probe-blocked"
    try:
        descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_RDWR | os.O_CLOEXEC, 0o600)
    except OSError as error:
        if error.errno in (errno.EROFS, errno.EACCES, errno.EPERM):
            return
        raise ProbeFailure("mqueue-protection") from None
    os.close(descriptor)
    try:
        os.unlink(path)
    finally:
        raise ProbeFailure("mqueue-protection")


def verify_proc_protection():
    mounts = proc_mounts()
    root = mounts.get("/proc")
    if (root is None or root[0] != "/" or root[2] != "proc"
            or not {"nosuid", "nodev", "noexec"} <= root[1]):
        raise ProbeFailure("proc-root-protection")
    for path in READONLY_PROC:
        if path in mounts or os.path.lexists(path):
            mounted = mounts.get(path)
            if mounted is None or mounted[2] != "proc" or "ro" not in mounted[1]:
                raise ProbeFailure("proc-readonly-protection")
    null_device = os.stat("/dev/null").st_rdev
    for path in MASKED_FILES:
        if path in mounts or os.path.lexists(path):
            metadata = os.stat(path)
            if (path not in mounts or not stat.S_ISCHR(metadata.st_mode)
                    or metadata.st_rdev != null_device):
                raise ProbeFailure("proc-file-mask")
    for path in MASKED_DIRECTORIES:
        if path in mounts or os.path.lexists(path):
            mounted = mounts.get(path)
            if (mounted is None or mounted[2] != "tmpfs"
                    or "ro" not in mounted[1] or os.listdir(path)):
                raise ProbeFailure("proc-directory-mask")


def child(expected_namespaces, expected_profile, parent_pid):
    state = status()
    if os.getuid() != 10001 or os.getpid() != 1 or os.getppid() != 0:
        raise ProbeFailure("child-identity")
    if {name for _, name in socket.if_nameindex()} != {"lo"}:
        raise ProbeFailure("network-boundary")
    if any(before == after for before, after in zip(expected_namespaces.split(","),
                                                   namespaces().split(","))):
        raise ProbeFailure("namespace-inheritance")
    if (state["NoNewPrivs"].strip() != "1"
            or any(int(state[name].strip(), 16) != 0 for name in ("CapEff", "CapPrm", "CapInh", "CapAmb"))):
        raise ProbeFailure("child-privileges")
    if profile() != expected_profile:
        raise ProbeFailure("apparmor-inheritance")
    if "CADENCE_RENDERER_TOKEN" in os.environ or "CADENCE_PROBE_CANARY" in os.environ:
        raise ProbeFailure("child-environment")
    for protected_pid in {parent_pid, 1}:
        verify_parent_protection(protected_pid)
    verify_workspace_filesystem()
    verify_mqueue_protection()
    verify_proc_protection()
    # If outer parent PID1, syscall PID1 denotes this child, not the outer server.
    # For exec-in-container probes, the outer probe PID is absent from the child namespace.
    if parent_pid != 1:
        try:
            os.kill(parent_pid, 0)
        except ProcessLookupError:
            pass
        else:
            raise ProbeFailure("pid-signal-boundary")
    print("namespace-probe ok")
    return 0


def _preflight(require_profile=False):
    """Run before server admission/token access; returns 0 on verified boundaries, otherwise 1."""
    if not (require_profile is False or require_profile is True or require_profile == PROFILE):
        return failed("probe-configuration")
    expected_profile = profile()
    if require_profile and expected_profile != PROFILE + " (enforce)":
        return failed("apparmor-not-enforced")
    state = status()
    if (os.getuid() != 10001 or int(state["CapEff"].strip(), 16) != 0
            or int(state["CapBnd"].strip(), 16) != 0 or state["NoNewPrivs"].strip() != "1"):
        return failed("container-privileges")
    verify_workspace_filesystem()
    verify_mqueue_protection()
    protect_parent()
    # Fresh canary is absent from the child environment and every diagnostic.
    os.environ["CADENCE_PROBE_CANARY"] = os.urandom(32).hex()
    command = ["unshare", "--user", "--map-current-user", "--net", "--pid", "--mount", "--ipc",
               "--fork", "--kill-child", "--", "python3", "-I",
               "/opt/evidence-renderer/namespace_probe.py", "--child", namespaces(),
               expected_profile, str(os.getpid())]
    process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, start_new_session=True, close_fds=True,
                               env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C", "TZ": "UTC"})
    try:
        output, diagnostic = process.communicate(timeout=5)
        if process.returncode == 0 and output == b"namespace-probe ok\n" and not diagnostic:
            print("namespace-probe ok")
            return 0
        reason = "namespace-command"
        for marker, category in ((b"cannot change root filesystem propagation", "mount-propagation"),
                                 (b"unshare failed", "userns-or-seccomp"),
                                 (b"namespace-probe failed:", "child-boundary")):
            if marker in diagnostic[:1024]:
                reason = category
                break
        # Preserve only allowlisted fixed child failure categories, never raw stderr.
        for category in ("child-identity", "network-boundary", "namespace-inheritance",
                         "child-privileges", "apparmor-inheritance", "child-environment",
                         "parent-access-check", "parent-access-allowed", "proc-root-protection",
                         "proc-readonly-protection", "proc-file-mask", "proc-directory-mask",
                         "pid-signal-boundary", "mountinfo-size", "mountinfo-format", "probe-environment",
                         "workspace-filesystem", "mqueue-protection"):
            if diagnostic == ("namespace-probe failed: " + category + "\n").encode("ascii"):
                reason = category
                break
        if b"Operation not permitted" in diagnostic[:1024]:
            reason += "-eperm"
        elif b"Permission denied" in diagnostic[:1024]:
            reason += "-eacces"
        return failed(reason, process.returncode)
    except subprocess.TimeoutExpired:
        return failed("probe-deadline")
    finally:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        process.communicate(timeout=2)


def preflight(require_profile=False):
    try:
        return _preflight(require_profile)
    except ProbeFailure as error:
        return failed(str(error))
    except (OSError, ValueError, KeyError):
        return failed("probe-environment")


def main():
    if len(sys.argv) == 5 and sys.argv[1] == "--child":
        return child(sys.argv[2], sys.argv[3], int(sys.argv[4]))
    if len(sys.argv) not in (1, 2) or (len(sys.argv) == 2 and sys.argv[1] != PROFILE):
        return failed("probe-configuration")
    return preflight(require_profile=len(sys.argv) == 2)


if __name__ == "__main__":
    try:
        result = main()
    except ProbeFailure as error:
        result = failed(str(error))
    except (OSError, ValueError, KeyError):
        result = failed("probe-environment")
    raise SystemExit(result)
