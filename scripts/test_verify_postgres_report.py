from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from verify_postgres_report import verify, verify_all


class PostgresReportTest(unittest.TestCase):
    def test_requires_persistence_worker_and_upload_suites(self):
        with TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "TEST-com.cadence.persistence.PostgreSqlPersistenceTest.xml").write_text(
                '<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                verify_all(root)
            worker = root / "TEST-com.cadence.persistence.PostgreSqlFileScanWorkerTest.xml"
            worker.write_text('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', encoding="utf-8")
            with self.assertRaises(ValueError):
                verify_all(root)
            worker.write_text('<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                verify_all(root)
            upload = root / "TEST-com.cadence.persistence.PostgreSqlFileUploadTransactionTest.xml"
            upload.write_text('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', encoding="utf-8")
            with self.assertRaises(ValueError):
                verify_all(root)
            upload.write_text('<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                verify_all(root)
            utc = root / "TEST-com.cadence.persistence.PostgreSqlUtcMigrationTest.xml"
            utc.write_text('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', encoding="utf-8")
            with self.assertRaises(ValueError):
                verify_all(root)
            utc.write_text('<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            with self.assertRaises(FileNotFoundError):
                verify_all(root)
            analysis = root / "TEST-com.cadence.persistence.PostgreSqlBrandAnalysisTest.xml"
            analysis.write_text('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', encoding="utf-8")
            with self.assertRaises(ValueError):
                verify_all(root)
            analysis.write_text('<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            for name in ("PostgreSqlStrategyBuildTest", "PostgreSqlStrategyMigrationTest", "PostgreSqlApprovedStrategyMigrationTest", "PostgreSqlReviewMigrationTest", "PostgreSqlReviewConcurrencyTest", "PostgreSqlStrategyApiTest", "PostgreSqlBrandIdentityApiTest", "PostgreSqlBrandIdentityMigrationTest", "PostgreSqlEvidenceAdmissionTest", "PostgreSqlEvidenceAdmissionMigrationTest", "PostgreSqlEvidenceAnalysisTest", "PostgreSqlIdentityEvidenceTest", "PostgreSqlIdentityEvidenceMigrationTest", "PostgreSqlPdfReviewTest", "PostgreSqlPdfAdmissionMigrationTest", "PostgreSqlPdfAnalysisMigrationTest", "PostgreSqlPdfIdentityMigrationTest", "PostgreSqlUrlReviewTest", "PostgreSqlUrlReviewMigrationTest", "PostgreSqlUrlAnalysisTest", "PostgreSqlUrlAnalysisMigrationTest", "PostgreSqlSocialReviewTest", "PostgreSqlSocialReviewMigrationTest", "PostgreSqlAiGenerationTest", "PostgreSqlAiGenerationMigrationTest", "PostgreSqlAiCompletionMigrationTest", "PostgreSqlAiRecoveryProcessTest", "PostgreSqlGenerationBudgetTest", "PostgreSqlPostApiTest", "PostgreSqlPostMigrationTest", "PostgreSqlContentGenerationTest", "PostgreSqlCalendarTest", "PostgreSqlMediaTest", "PostgreSqlCsvExportTest", "PostgreSqlCsvExportMigrationTest", "PostgreSqlMetricImportTest", "PostgreSqlMetricImportMigrationTest", "PostgreSqlPerformanceTest", "PostgreSqlPerformanceMigrationTest", "PostgreSqlRecommendationTest", "PostgreSqlRecommendationMigrationTest", "PostgreSqlCreatorReportTest", "PostgreSqlCreatorReportMigrationTest", "PostgreSqlSecurityAuditMigrationTest"):
                with self.assertRaises(FileNotFoundError):
                    verify_all(root)
                report = root / f"TEST-com.cadence.persistence.{name}.xml"
                report.write_text('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', encoding="utf-8")
                with self.assertRaises(ValueError):
                    verify_all(root)
                report.write_text('<testsuite tests="1"><testcase/></testsuite>', encoding="utf-8")
            verify_all(root)

    def test_accepts_success_and_rejects_false_green_reports(self):
        cases = [
            ('<testsuite tests="1"><testcase/></testsuite>', True),
            ('<testsuite tests="1" skipped="1"><testcase><skipped/></testcase></testsuite>', False),
            ('<testsuite tests="1"><testcase><failure/></testcase></testsuite>', False),
            ('<testsuite tests="1"><testcase><error/></testcase></testsuite>', False),
            ('<testsuite tests="0"/>', False),
            ('<testsuite tests="2"><testcase/></testsuite>', False),
        ]
        with TemporaryDirectory() as directory:
            report = Path(directory) / "report.xml"
            for xml, valid in cases:
                with self.subTest(xml=xml):
                    report.write_text(xml, encoding="utf-8")
                    if valid:
                        verify(report)
                    else:
                        with self.assertRaises(ValueError):
                            verify(report)
            with self.assertRaises(FileNotFoundError):
                verify(Path(directory) / "missing.xml")


if __name__ == "__main__":
    unittest.main()
