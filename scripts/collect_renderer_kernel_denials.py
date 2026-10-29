"""Bounded CI diagnostics: retain reviewed kernel categories, never arbitrary audit data."""
from collections import deque
import json
import re
import sys

PROFILE = "cadence-evidence-renderer"
MAX_LINE = 8192
ALLOWED = {
    "operation": {"mount", "umount", "capable", "userns_create", "signal", "ptrace"},
    "info": {"failed mntpnt match", "failed srcname match", "failed type match",
             "failed flags match", "failed data match", "failed perms check",
             "failed pair match"},
    "fstype": {"proc", "none", "tmpfs"},
    "flags": {"rw, nosuid, nodev, noexec", "rw,nosuid,nodev,noexec", "rw, rprivate",
              "rw,rprivate", "rw, private, rec", "rw, rprivate, rec"},
    "name": {"/", "/proc", "/proc/"},
    "srcname": {"proc", "none", "/"},
    "capname": {"sys_admin", "sys_ptrace", "net_admin", "setuid", "setgid"},
    "denied_mask": {"mount", "umount", "create", "read", "write", "send", "receive"},
    "requested_mask": {"mount", "umount", "create", "read", "write", "send", "receive"},
}


def parse_line(line):
    if len(line) > MAX_LINE:
        return None
    fields = dict(re.findall(r'(\w+)="([^"\n]*)"', line))
    profile = fields.get("profile", "")
    if (fields.get("apparmor") != "DENIED"
            or not (profile == PROFILE or profile.startswith(PROFILE + "//&"))):
        return None
    result = {"profile": PROFILE}
    for key, values in ALLOWED.items():
        if key in fields:
            result[key] = fields[key] if fields[key] in values else "<redacted>"
    error = re.search(r'\berror=(-?[0-9]{1,3})(?:\s|$)', line)
    if error:
        result["error"] = int(error.group(1))
    return result


def collect(stream):
    results = deque(maxlen=20)
    while True:
        line = stream.readline(MAX_LINE + 1)
        if not line:
            break
        if len(line) > MAX_LINE:
            while line and not line.endswith(b"\n"):
                line = stream.readline(MAX_LINE + 1)
            continue
        result = parse_line(line.decode("utf-8", errors="replace"))
        if result is not None:
            results.append(result)
    return list(results)


if __name__ == "__main__":
    print(json.dumps(collect(sys.stdin.buffer), ensure_ascii=True))
