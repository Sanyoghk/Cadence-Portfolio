"""Fail CI if the PostgreSQL suite did not actually run successfully."""

from pathlib import Path
import xml.etree.ElementTree as ET


def verify(report: Path) -> None:
    suite = ET.parse(report).getroot()
    cases = suite.findall("testcase")
    if not cases or int(suite.get("tests", "0")) != len(cases):
        raise ValueError("PostgreSQL test report is empty or inconsistent")
    if any(int(suite.get(key, "0")) for key in ("skipped", "errors", "failures")):
        raise ValueError("PostgreSQL tests were skipped or failed")
    if any(case.find(tag) is not None for case in cases for tag in ("skipped", "error", "failure")):
        raise ValueError("PostgreSQL tests were skipped or failed")


def verify_all(directory: Path) -> None:
    for name in ("PostgreSqlPersistenceTest", "PostgreSqlFileScanWorkerTest", "PostgreSqlFileUploadTransactionTest", "PostgreSqlUtcMigrationTest", "PostgreSqlBrandAnalysisTest", "PostgreSqlStrategyBuildTest", "PostgreSqlStrategyMigrationTest", "PostgreSqlApprovedStrategyMigrationTest", "PostgreSqlReviewMigrationTest", "PostgreSqlReviewConcurrencyTest", "PostgreSqlStrategyApiTest", "PostgreSqlBrandIdentityApiTest", "PostgreSqlBrandIdentityMigrationTest", "PostgreSqlEvidenceAdmissionTest", "PostgreSqlEvidenceAdmissionMigrationTest", "PostgreSqlEvidenceAnalysisTest", "PostgreSqlIdentityEvidenceTest", "PostgreSqlIdentityEvidenceMigrationTest", "PostgreSqlPdfReviewTest", "PostgreSqlPdfAdmissionMigrationTest", "PostgreSqlPdfAnalysisMigrationTest", "PostgreSqlPdfIdentityMigrationTest", "PostgreSqlUrlReviewTest", "PostgreSqlUrlReviewMigrationTest", "PostgreSqlUrlAnalysisTest", "PostgreSqlUrlAnalysisMigrationTest", "PostgreSqlSocialReviewTest", "PostgreSqlSocialReviewMigrationTest", "PostgreSqlAiGenerationTest", "PostgreSqlAiGenerationMigrationTest", "PostgreSqlAiCompletionMigrationTest", "PostgreSqlAiRecoveryProcessTest", "PostgreSqlGenerationBudgetTest", "PostgreSqlPostApiTest", "PostgreSqlPostMigrationTest", "PostgreSqlContentGenerationTest", "PostgreSqlCalendarTest", "PostgreSqlMediaTest", "PostgreSqlCsvExportTest", "PostgreSqlCsvExportMigrationTest", "PostgreSqlMetricImportTest", "PostgreSqlMetricImportMigrationTest", "PostgreSqlPerformanceTest", "PostgreSqlPerformanceMigrationTest", "PostgreSqlRecommendationTest", "PostgreSqlRecommendationMigrationTest", "PostgreSqlCreatorReportTest", "PostgreSqlCreatorReportMigrationTest", "PostgreSqlSecurityAuditMigrationTest"):
        verify(directory / f"TEST-com.cadence.persistence.{name}.xml")


if __name__ == "__main__":
    verify_all(Path("target/surefire-reports"))
    print("PostgreSQL suite ran without skips or failures")
