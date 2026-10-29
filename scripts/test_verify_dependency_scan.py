import copy
import unittest

from verify_dependency_scan import verify


class DependencyScanTest(unittest.TestCase):
    def setUp(self):
        names = ["spring-boot", "postgresql", "junit-jupiter"]
        self.bom = {"bomFormat": "CycloneDX", "components": [
            {"name": name, "version": "1", "purl": f"pkg:maven/example/{name}@1"}
            for name in names
        ]}
        self.report = {"results": [{"packages": [
            {"package": {"name": name, "version": "1", "ecosystem": "Maven"}}
            for name in names
        ]}]}

    def test_complete_clean_inventory(self):
        self.assertEqual(3, verify(self.bom, self.report))

    def test_rejects_missing_partial_and_vulnerable_reports(self):
        cases = [{}, {"results": []}, copy.deepcopy(self.report), copy.deepcopy(self.report)]
        cases[2]["results"][0]["packages"].pop()
        cases[3]["results"][0]["packages"][0]["vulnerabilities"] = [{"id": "TEST"}]
        for report in cases:
            with self.subTest(report=report), self.assertRaises(ValueError):
                verify(self.bom, report)

    def test_rejects_omitted_tests_and_unrecognized_coordinates(self):
        for field in ["purl", "version"]:
            bom = copy.deepcopy(self.bom)
            del bom["components"][0][field]
            with self.subTest(field=field), self.assertRaises(ValueError):
                verify(bom, self.report)
        self.bom["components"].pop()
        with self.assertRaises(ValueError):
            verify(self.bom, self.report)
