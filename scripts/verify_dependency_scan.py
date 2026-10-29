"""Reject incomplete inventory or a non-clean OSV report, independently of scanner exit codes."""

from collections import Counter
import json
from pathlib import Path
import sys


def verify(bom: dict, report: dict) -> int:
    components = bom.get("components", [])
    if bom.get("bomFormat") != "CycloneDX" or not components:
        raise ValueError("Missing CycloneDX inventory")
    expected = Counter()
    for component in components:
        if not component.get("purl", "").startswith("pkg:maven/"):
            raise ValueError("Unrecognized dependency coordinates")
        name, version = component.get("name"), component.get("version")
        if not name or not version:
            raise ValueError("Missing dependency name or version")
        expected[(name, version)] += 1
    if not {"spring-boot", "postgresql", "junit-jupiter"}.issubset(
        name for name, _ in expected
    ):
        raise ValueError("Runtime or test inventory is missing")
    actual = Counter()
    for result in report.get("results", []):
        for entry in result.get("packages", []):
            package = entry["package"]
            if package.get("ecosystem") != "Maven":
                raise ValueError("Unexpected scanner ecosystem")
            actual[(package["name"], package["version"])] += 1
            if entry.get("vulnerabilities"):
                raise ValueError("Dependency vulnerabilities remain")
    if actual != expected:
        raise ValueError("Scanner inventory differs from Maven SBOM")
    return sum(expected.values())


if __name__ == "__main__":
    count = verify(
        json.loads(Path(sys.argv[1]).read_text(encoding="utf-8")),
        json.loads(Path(sys.argv[2]).read_text(encoding="utf-8")),
    )
    print(f"Verified {count} Maven dependencies; no OSV vulnerabilities reported")
