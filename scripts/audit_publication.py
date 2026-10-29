"""Redacted privacy checks for tracked files or complete reachable Git history.

This complements Gitleaks; it is not a complete PII detector or a publication approval.
No source content, email addresses or matched values are included in the report.
"""

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path, PurePosixPath


PERSONAL_PATH = re.compile(
    r"(?:[A-Za-z]:[\\/]+Users[\\/]+|/mnt/[a-z]/Users/|/Users/|/home/)"
    r"(?P<user>[\w.-]{1,128})",
    re.IGNORECASE,
)
GENERIC_USERS = {"user", "username", "developer", "example", "runner", "runneradmin", "root"}
PERSONAL_EMAIL = re.compile(
    r"[A-Za-z0-9._%+\-]{1,64}@(?:gmail|googlemail|hotmail|outlook|live|yahoo|icloud|mail|yandex)\.[A-Za-z]{2,24}",
    re.IGNORECASE,
)
CHAT_ID = re.compile(r"\b(?:01a[0-9a-f]{5}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}|g-p-[0-9a-f]{20,})\b")
PHONE = re.compile(r"(?<![\w])\+[1-9][0-9 ()-]{8,18}[0-9](?![\w])")
PRIVATE_FILE = re.compile(r"(?:id_rsa|id_ed25519|credentials|.*\.(?:p12|pfx|jks|key|pem|dump|sqlite3?))$", re.I)


def inspect_text(text):
    """Return only rule and line; never echo a matched value."""
    results = []
    for rule, pattern in (("personal-email", PERSONAL_EMAIL), ("private-chat-id", CHAT_ID),
                          ("international-phone-candidate", PHONE)):
        results.extend((rule, text.count("\n", 0, match.start()) + 1) for match in pattern.finditer(text))
    results.extend(
        ("personal-home-path", text.count("\n", 0, match.start()) + 1)
        for match in PERSONAL_PATH.finditer(text)
        if match.group("user").lower() not in GENERIC_USERS
    )
    return sorted(set(results))


def private_filename(name):
    leaf = PurePosixPath(name).name
    return (leaf == ".env" or (leaf.startswith(".env.") and leaf != ".env.example")) or bool(PRIVATE_FILE.fullmatch(leaf))


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], stderr=subprocess.PIPE)


def scan(root, refs=None, history=False, worktree=False):
    root = Path(root)
    refs = refs or ["HEAD"]
    resolved = [git(root, "rev-parse", "--verify", ref + "^{commit}").decode().strip() for ref in refs]
    findings = []
    objects = {}
    scanned = 0

    def inspect(data, path, obj):
        nonlocal scanned
        scanned += 1
        if private_filename(path):
            findings.append({"rule": "private-file", "path": path, "object": obj, "line": 1})
        for rule, line in inspect_text(data.decode("utf-8", errors="replace")):
            findings.append({"rule": rule, "path": path, "object": obj, "line": line})

    if worktree:
        for name in git(root, "ls-files", "-z").decode().split("\0"):
            if not name:
                continue
            path = root / name
            if path.is_symlink():
                findings.append({"rule": "symlink-needs-review", "path": name, "object": "worktree", "line": 1})
            elif path.exists():
                inspect(path.read_bytes(), name, "worktree")
    else:
        # ls-tree for every commit also covers renamed sensitive filenames sharing the same blob.
        commits = git(root, "rev-list", *resolved).decode().splitlines() if history else resolved
        for commit in commits:
            if history:
                metadata = git(root, "show", "-s", "--format=%an <%ae>%n%cn <%ce>%n%B", commit)
                inspect(metadata, "[commit metadata]", commit)
            for entry in git(root, "ls-tree", "-r", "-z", commit).split(b"\0"):
                if not entry:
                    continue
                header, raw_name = entry.split(b"\t", 1)
                mode, kind, sha = header.decode().split()
                name = raw_name.decode("utf-8", errors="replace")
                if kind != "blob" or mode == "120000":
                    findings.append({"rule": "external-or-symlink-needs-review", "path": name, "object": sha, "line": 1})
                    continue
                objects.setdefault(sha, set()).add(name)
        for sha, names in objects.items():
            data = git(root, "cat-file", "blob", sha)
            # Scan content once, but report every historical path to avoid hiding a rename.
            for name in sorted(names):
                inspect(data, name, sha)
    return {"mode": "worktree" if worktree else "history" if history else "snapshot",
            "refs": resolved, "scanned_items": scanned, "findings": findings,
            "limitations": "Heuristic privacy checks only; run Gitleaks and inspect media, GitHub discussions, logs and artifacts separately."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--ref", action="append", dest="refs")
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--history", action="store_true")
    modes.add_argument("--worktree", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = scan(args.root, args.refs, args.history, args.worktree)
        encoded = json.dumps(result, indent=2, ensure_ascii=True) + "\n"
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(encoded, encoding="utf-8")
        else:
            print(encoded, end="")
        print(f"Publication privacy: {result['scanned_items']} items, {len(result['findings'])} findings", file=sys.stderr)
        return 1 if result["findings"] else 0
    except (OSError, subprocess.CalledProcessError, ValueError):
        # Git errors may contain local home paths or source content. Fail closed without echoing them.
        print("Publication privacy scan could not complete; no clean result.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
