"""Security regressions for same-UID proc access and preserved Docker proc barriers."""
import importlib.util
import ctypes
import errno
import io
import os
from pathlib import Path
import subprocess
import sys
import time
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("namespace_probe", Path(__file__).with_name("namespace_probe.py"))
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

PARENT_FIXTURE = """
import ctypes, resource, sys, time
libc = ctypes.CDLL(None, use_errno=True)
assert libc.prctl(4, int(sys.argv[1]), 0, 0, 0) == 0
assert libc.prctl(3, 0, 0, 0, 0) == int(sys.argv[1])
resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
print('ready', flush=True)
time.sleep(30)
"""


@unittest.skipUnless(sys.platform.startswith("linux"), "Linux proc security requires the renderer image")
class ProcSecurityTest(unittest.TestCase):
    def fixture(self, dumpable):
        process = subprocess.Popen([sys.executable, "-I", "-c", PARENT_FIXTURE, str(dumpable)],
                                   stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                   stderr=subprocess.DEVNULL, close_fds=True,
                                   env={"PATH": "/usr/local/bin:/usr/bin:/bin", "LANG": "C"})
        self.addCleanup(self.stop, process)
        self.assertEqual(process.stdout.readline(), b"ready\n")
        return process.pid

    @staticmethod
    def stop(process):
        process.terminate()
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=2)
        process.stdout.close()

    def test_dumpable_same_uid_parent_is_rejected_by_preflight(self):
        pid = self.fixture(1)
        with self.assertRaisesRegex(probe.ProbeFailure, "parent-access-allowed"):
            probe.verify_parent_protection(pid)

    def test_nondumpable_parent_rejects_all_sensitive_proc_access(self):
        probe.verify_parent_protection(self.fixture(0))

    def test_current_docker_proc_masks_and_readonly_paths_are_preserved(self):
        probe.verify_proc_protection()

    def test_replacing_proc_without_child_masks_is_rejected(self):
        with patch.object(probe, "proc_mounts", return_value={
                "/proc": ("/", {"rw", "nosuid", "nodev", "noexec"}, "proc")}):
            with self.assertRaises(probe.ProbeFailure):
                probe.verify_proc_protection()

    def test_current_workspace_uses_readonly_root_and_bounded_tmpfs(self):
        probe.verify_workspace_filesystem()

    def test_mqueue_create_is_denied(self):
        probe.verify_mqueue_protection()

    def test_workspace_rejects_missing_unsafe_oversized_and_malformed_mounts(self):
        valid = ("1 0 0:1 / / ro - overlay overlay rw\n"
                 "2 1 0:2 / /tmp rw,nosuid,nodev,noexec - tmpfs tmpfs rw,size=131072k\n"
                 "3 1 0:3 / /dev/shm rw,nosuid,nodev,noexec - tmpfs shm rw,size=262144k\n"
                 "4 1 0:4 / /dev/mqueue ro,nosuid,nodev,noexec - tmpfs tmpfs ro,size=4k,mode=0\n")
        with patch("builtins.open", return_value=io.StringIO(valid)):
            probe.verify_workspace_filesystem()
        invalid = (
            valid.replace("/ / ro", "/ / rw"),
            valid.replace("/tmp rw,nosuid", "/tmp ro,nosuid"),
            valid.replace("- tmpfs tmpfs rw", "- ext4 host rw"),
            valid.replace("0:2 / /tmp", "0:2 /host-data /tmp"),
            valid.replace("rw,nosuid,nodev,noexec", "rw,nosuid,nodev"),
            valid.replace("size=131072k", "size=131073k"),
            valid.replace("size=262144k", "size=262145k"),
            valid.replace("size=4k", "size=5k"),
            valid.replace("mode=0", "mode=777"),
            valid.replace("size=131072k", "size=50%"),
            valid.replace("size=131072k", "size=0"),
            valid.replace("size=131072k", "size=999999999999999999999999999999999k"),
            valid.replace("size=131072k", "size=131072k,size=1k"),
            valid.replace("rw,size=131072k", "rw"),
            "\n".join(line for line in valid.splitlines() if "/dev/shm" not in line),
            valid + "2 1 0:2 / /tmp rw,nosuid,nodev,noexec - tmpfs tmpfs rw,size=1k\n",
            "invalid mountinfo\n",
            "x" * 262145,
        )
        for index, content in enumerate(invalid):
            with self.subTest(case=index), patch("builtins.open", return_value=io.StringIO(content)):
                with self.assertRaises(probe.ProbeFailure):
                    probe.verify_workspace_filesystem()

    def test_native_process_inspection_cannot_access_nondumpable_parent(self):
        pid = self.fixture(0)
        libc = ctypes.CDLL(None, use_errno=True)

        class Iovec(ctypes.Structure):
            _fields_ = (("iov_base", ctypes.c_void_p), ("iov_len", ctypes.c_size_t))

        libc.process_vm_readv.argtypes = (ctypes.c_int, ctypes.POINTER(Iovec), ctypes.c_ulong,
                                         ctypes.POINTER(Iovec), ctypes.c_ulong, ctypes.c_ulong)
        libc.process_vm_readv.restype = ctypes.c_ssize_t
        local_buffer = ctypes.create_string_buffer(1)
        local = Iovec(ctypes.cast(local_buffer, ctypes.c_void_p), 1)
        remote = Iovec(1, 1)  # Invalid address: no parent data can be returned even if protection fails.
        ctypes.set_errno(0)
        result = libc.process_vm_readv(pid, ctypes.byref(local), 1, ctypes.byref(remote), 1, 0)
        self.assertEqual(result, -1)
        self.assertEqual(ctypes.get_errno(), errno.EPERM)

        libc.ptrace.argtypes = (ctypes.c_ulong, ctypes.c_ulong, ctypes.c_void_p, ctypes.c_void_p)
        libc.ptrace.restype = ctypes.c_long
        ctypes.set_errno(0)
        result = libc.ptrace(16, pid, None, None)  # PTRACE_ATTACH
        observed_errno = ctypes.get_errno()
        if result == 0:
            # A failing regression must not leave its fixture stopped/traced.
            try:
                deadline = time.monotonic() + 2
                while time.monotonic() < deadline:
                    observed, _state = os.waitpid(pid, os.WUNTRACED | os.WNOHANG)
                    if observed == pid:
                        break
                    time.sleep(0.01)
            finally:
                libc.ptrace(17, pid, None, None)  # PTRACE_DETACH
        self.assertEqual(result, -1)
        self.assertEqual(observed_errno, errno.EPERM)


if __name__ == "__main__":
    unittest.main()
