"""Reject missing/skipped real provider and PostgreSQL evidence contracts."""
from pathlib import Path
from verify_postgres_report import verify

SUITES = (
    "com.cadence.infrastructure.storage.MinioEvidenceObjectStorageTest",
    "com.cadence.infrastructure.storage.ClamdContainerIntegrationTest",
    "com.cadence.persistence.PostgreSqlQuarantineLifecycleTest",
    "com.cadence.persistence.PostgreSqlEvidenceArtifactWorkerTest",
    "com.cadence.persistence.PostgreSqlEvidenceRegistrationQuotaTest",
    "com.cadence.application.PostgreSqlSocialEvidenceQuotaTest",
    "com.cadence.application.PostgreSqlSocialProviderCapacityTest",
    "com.cadence.infrastructure.storage.RendererContainerIntegrationTest",
    "com.cadence.infrastructure.storage.FullEvidenceProviderIntegrationTest",
)


def verify_all(directory: Path) -> None:
    for suite in SUITES:
        verify(directory / f"TEST-{suite}.xml")


if __name__ == "__main__":
    verify_all(Path("target/surefire-reports"))
    print("Required PostgreSQL, MinIO, ClamAV, renderer and full evidence pipeline suites ran without skips or failures")
