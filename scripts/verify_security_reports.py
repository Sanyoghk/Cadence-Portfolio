"""Require completed scans; preserve every finding instead of editing scanner output."""

import json
import re
import subprocess
from collections import Counter
from pathlib import Path


def verify_sast(path: Path) -> int:
    report = json.loads(path.read_text(encoding="utf-8"))
    if report.get("errors") != [] or not report.get("paths", {}).get("scanned"):
        raise ValueError("SAST scan incomplete or failed")
    findings = report.get("results")
    if not isinstance(findings, list):
        raise ValueError("SAST findings missing")
    for finding in findings:
        if not finding.get("check_id") or not finding.get("path"):
            raise ValueError("SAST finding incomplete")
    print(f"SAST completed: {len(report['paths']['scanned'])} files, {len(findings)} findings; human triage required for findings")
    return len(findings)


def verify_container(path: Path, package_proof: dict | None = None) -> Counter:
    report = json.loads(path.read_text(encoding="utf-8"))
    results = report.get("Results")
    if not isinstance(results, list) or not results:
        raise ValueError("Container inventory missing")
    if not any(r.get("Class") == "os-pkgs" and r.get("Packages") for r in results):
        raise ValueError("Container OS package inventory missing")
    counts = Counter()
    reviewed = 0
    if package_proof is not None:
        image = report.get("Metadata", {}).get("ImageID", "")
        if not re.fullmatch(r"sha256:[0-9a-f]{64}", image) or package_proof.get("image") != image:
            raise ValueError("Compiler proof belongs to a different immutable image")
        packages = package_proof.get("packages", {})
        for target in ("usr/local/bin/minio", "usr/local/bin/mc"):
            values = packages.get(target)
            if not isinstance(values, list) or not values or len(values) > 10000:
                raise ValueError("Compiler package proof missing or unbounded")
            if any(not isinstance(v, str) or not re.fullmatch(r"[A-Za-z0-9_./-]+", v) for v in values):
                raise ValueError("Malformed compiler package proof")
    for result in results:
        for finding in result.get("Vulnerabilities", []):
            severity = finding.get("Severity")
            if severity not in {"UNKNOWN", "LOW", "MEDIUM", "HIGH", "CRITICAL"} or not finding.get("VulnerabilityID"):
                raise ValueError("Malformed container finding")
            counts[severity] += 1
            # Go's advisory concerns seven OpenPGP package paths, not all x/crypto packages.
            # Keep the raw UNKNOWN finding; accept only proof these packages were not compiled
            # into this exact local fixture image. Any other UNKNOWN still fails closed.
            target = result.get("Target", "").lstrip("/")
            if (severity == "UNKNOWN" and package_proof is not None
                    and finding["VulnerabilityID"] == "GO-2026-5932"
                    and finding.get("PkgName") == "golang.org/x/crypto"
                    and target in {"usr/local/bin/minio", "usr/local/bin/mc"}
                    and not any(p == "golang.org/x/crypto/openpgp" or p.startswith("golang.org/x/crypto/openpgp/")
                                for p in package_proof["packages"][target])):
                reviewed += 1
    print(f"Container findings (unfiltered): {dict(counts)}")
    if reviewed:
        print(f"Reviewed not-applicable findings: {reviewed}; affected OpenPGP packages absent from exact-image compiler inventory; raw report retained")
    if counts["HIGH"] or counts["CRITICAL"] or counts["UNKNOWN"] != reviewed:
        raise ValueError("Container has HIGH/CRITICAL or unclassified vulnerabilities")
    return counts


def minio_compiler_proof(path: Path) -> dict:
    """Read compiler inventories from a local immutable image, never from a user-supplied URL."""
    report = json.loads(path.read_text(encoding="utf-8"))
    image = report.get("Metadata", {}).get("ImageID", "")
    if not re.fullmatch(r"sha256:[0-9a-f]{64}", image):
        raise ValueError("Immutable local image ID missing")
    proof = {"image": image, "packages": {}}
    for binary, part in (("minio", "server"), ("mc", "client")):
        result = subprocess.run(
            ["docker", "run", "--rm", "--network", "none", "--read-only", "--memory", "64m",
             "--pids-limit", "16", "--cap-drop", "ALL", "--security-opt", "no-new-privileges",
             "--entrypoint", "/bin/busybox", image, "head", "-c", "1048577",
             f"/usr/local/share/cadence-minio/{part}-packages.txt"],
            check=True, capture_output=True, timeout=30,
        )
        if len(result.stdout) > 1024 * 1024:
            raise ValueError("Compiler inventory exceeds bound")
        proof["packages"][f"usr/local/bin/{binary}"] = result.stdout.decode("utf-8", errors="strict").splitlines()
    path.with_suffix(".compiler-packages.json").write_text(json.dumps(proof, indent=2), encoding="utf-8")
    return proof


if __name__ == "__main__":
    import sys
    if sys.argv[1] == "sast":
        findings = verify_sast(Path(sys.argv[2]))
        if findings:
            print("::warning::SAST findings are preserved in artifact; review each finding before accepting source")
    elif sys.argv[1] == "container":
        verify_container(Path(sys.argv[2]))
    elif sys.argv[1] == "minio":
        path = Path(sys.argv[2])
        verify_container(path, minio_compiler_proof(path))
    else:
        raise ValueError("Unknown security report kind")
