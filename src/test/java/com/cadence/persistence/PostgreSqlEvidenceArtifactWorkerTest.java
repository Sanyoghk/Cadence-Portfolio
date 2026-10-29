package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.EvidenceArtifactWorkerContract;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles("postgres-test")
class PostgreSqlEvidenceArtifactWorkerTest extends EvidenceArtifactWorkerContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void artifactHistoryIsAppendOnlyAndCannotBeReclaimedByRawSql() {
    prepareSource(true, com.cadence.domain.ScanVerdict.CLEAN);
    worker.runOnce();
    var artifact = sourceArtifacts().getFirst();
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM evidence_artifacts WHERE id = ?", artifact.getId()))
        .hasStackTraceContaining("Evidence artifact provenance is append-only");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE evidence_artifacts SET renderer_version = 'forged' WHERE id = ?",
                    artifact.getId()))
        .hasStackTraceContaining("Evidence artifact provenance is append-only");
    var generation =
        objects.findAll().stream()
            .filter(q -> q.getWorkspaceId().equals(workspace.getId()))
            .findFirst()
            .orElseThrow();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE quarantine_objects SET state = 'DELETING' WHERE id = ?",
                    generation.getId()))
        .hasStackTraceContaining("Referenced quarantine data cannot be reclaimed");
  }

  @Test
  void databaseRejectsForgedSourceChecksumWithoutApplicationValidation() {
    prepareSource(false, com.cadence.domain.ScanVerdict.CLEAN);
    claimExtraction();
    assertThatThrownBy(() -> rawPdfArtifact("b".repeat(64)))
        .hasStackTraceContaining("Invalid evidence artifact provenance");
    assertThat(sourceArtifacts()).isEmpty();
  }

  @Test
  void databaseRejectsPublicationAfterExtractionLeaseExpiry() {
    prepareSource(false, com.cadence.domain.ScanVerdict.CLEAN);
    claimExtraction();
    jdbc.update(
        "UPDATE jobs SET lease_expires_at = clock_timestamp() - INTERVAL '1 second' WHERE id = ?",
        extractionJob.getId());
    assertThatThrownBy(() -> rawPdfArtifact(checksum(sourceBytes)))
        .hasStackTraceContaining("Invalid evidence artifact provenance");
    assertThat(sourceArtifacts()).isEmpty();
  }

  private void claimExtraction() {
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              var job = jobRepository.findLockedById(extractionJob.getId()).orElseThrow();
              job.claim("raw-sql-fixture", clock.instant(), Duration.ofMinutes(2));
            });
  }

  private void rawPdfArtifact(String sourceChecksum) {
    jdbc.update(
        """
        INSERT INTO evidence_artifacts(id,created_at,updated_at,lock_version,evidence_id,
            scan_result_id,job_id,source_checksum,source_version,attempt_number,lease_owner,kind,
            extracted_text,output_checksum,renderer_version,produced_at)
        VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?,?,?,?,?,1,'raw-sql-fixture','PDF_TEXT',
            'text',?,'isolated-v1',clock_timestamp())
        """,
        java.util.UUID.randomUUID(),
        source.getId(),
        observation.getId(),
        extractionJob.getId(),
        sourceChecksum,
        source.getLockVersion(),
        checksum("text".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }
}
