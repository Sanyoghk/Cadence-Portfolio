import tempfile
import unittest
from pathlib import Path
from verify_evidence_reports import SUITES, verify_all


class EvidenceReportGateTest(unittest.TestCase):
    def test_all_real_suites_required(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for suite in SUITES:
                (root / f"TEST-{suite}.xml").write_text(
                    '<testsuite tests="1" failures="0" errors="0" skipped="0"><testcase/></testsuite>',
                    encoding="utf-8")
            verify_all(root)
            missing = root / f"TEST-{SUITES[-1]}.xml"
            missing.unlink()
            with self.assertRaises(FileNotFoundError):
                verify_all(root)

    def test_skipped_provider_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for suite in SUITES:
                (root / f"TEST-{suite}.xml").write_text(
                    '<testsuite tests="1" failures="0" errors="0" skipped="1"><testcase><skipped/></testcase></testsuite>',
                    encoding="utf-8")
            with self.assertRaises(ValueError):
                verify_all(root)
