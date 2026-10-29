import json
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory
from verify_security_reports import verify_sast, verify_container


class SecurityReportTests(unittest.TestCase):
    def test_openpgp_review_needs_exact_image_and_absent_compiled_packages(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "minio.json"
            finding = {"Severity": "UNKNOWN", "VulnerabilityID": "GO-2026-5932", "PkgName": "golang.org/x/crypto"}
            image = "sha256:" + "a" * 64
            report = {"Metadata": {"ImageID": image}, "Results": [
                {"Class": "os-pkgs", "Packages": [{}]},
                {"Target": "usr/local/bin/minio", "Vulnerabilities": [finding]}]}
            path.write_text(json.dumps(report), encoding="utf-8")
            proof = {"image": image, "packages": {
                "usr/local/bin/minio": ["net/http", "golang.org/x/crypto/ssh"],
                "usr/local/bin/mc": ["net/http"]}}
            with self.assertRaises(ValueError):
                verify_container(path)
            self.assertEqual(verify_container(path, proof), {"UNKNOWN": 1})
            for invalid in (
                {**proof, "image": "different"},
                {**proof, "packages": {}},
                {**proof, "packages": {**proof["packages"], "usr/local/bin/minio": ["golang.org/x/crypto/openpgp/packet"]}},
            ):
                with self.assertRaises(ValueError):
                    verify_container(path, invalid)
            original = finding.copy()
            for change in ({"VulnerabilityID": "GO-future-unknown"}, {"PkgName": "another-module"}, {"Severity": "HIGH"}):
                finding.clear()
                finding.update(original | change)
                path.write_text(json.dumps(report), encoding="utf-8")
                with self.assertRaises(ValueError):
                    verify_container(path, proof)

    def test_sast_cannot_claim_a_missing_or_failed_scan_clean(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "sast.json"
            for report in ({}, {"errors": [], "results": []}, {"errors": [{}], "paths": {"scanned": ["a.java"]}, "results": []}):
                path.write_text(json.dumps(report), encoding="utf-8")
                with self.assertRaises(ValueError):
                    verify_sast(path)
            path.write_text(json.dumps({"errors": [], "paths": {"scanned": ["a.java"]}, "results": [{"check_id": "audit", "path": "a.java"}]}), encoding="utf-8")
            self.assertEqual(verify_sast(path), 1)

    def test_container_requires_inventory_and_rejects_even_unfixed_high(self):
        with TemporaryDirectory() as directory:
            path = Path(directory) / "image.json"
            for results in ([], [{"Class": "os-pkgs"}], [{"Class": "os-pkgs", "Packages": [{}], "Vulnerabilities": [{"Severity": "HIGH", "VulnerabilityID": "CVE-example"}]}]):
                path.write_text(json.dumps({"Results": results}), encoding="utf-8")
                with self.assertRaises(ValueError):
                    verify_container(path)
            path.write_text(json.dumps({"Results": [{"Class": "os-pkgs", "Packages": [{}], "Vulnerabilities": []}]}), encoding="utf-8")
            self.assertEqual(verify_container(path), {})
