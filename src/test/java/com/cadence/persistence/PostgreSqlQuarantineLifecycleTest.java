package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.QuarantineLifecycleContract;
import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.services.QuarantineLifecycle;
import com.cadence.domain.*;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "cadence.evidence.lifecycle.max-workspace-bytes=100",
      "cadence.evidence.lifecycle.max-workspace-objects=2",
      "cadence.evidence.lifecycle.max-workspace-writes-per-hour=3",
      "cadence.evidence.lifecycle.write-deadline=PT10S",
      "cadence.evidence.lifecycle.orphan-retention=PT10S",
      "cadence.evidence.lifecycle.deletion-drain=PT10S",
      "cadence.evidence.lifecycle.tombstone-sweep=PT10S",
      "cadence.evidence.lifecycle.cleanup-batch=100",
      "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
    })
@ActiveProfiles("postgres-test")
@Import(QuarantineLifecycleContract.Fixtures.class)
class PostgreSqlQuarantineLifecycleTest extends QuarantineLifecycleContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource datasource;

  @Test
  void reservationsRefreshQuotaAfterWaitingDespiteRepeatableReadDatasourceDefault()
      throws Exception {
    Brand other = brands.saveAndFlush(new Brand("Other isolation brand", workspace));
    try (var executor = Executors.newFixedThreadPool(2);
        Connection blocker = datasource.getConnection()) {
      assertThat(blocker.getTransactionIsolation())
          .isEqualTo(Connection.TRANSACTION_REPEATABLE_READ);
      blocker.setAutoCommit(false);
      try (var statement =
          blocker.prepareStatement("SELECT id FROM workspaces WHERE id = ? FOR UPDATE")) {
        statement.setObject(1, workspace.getId());
        try (var result = statement.executeQuery()) {
          assertThat(result.next()).isTrue();
        }
      }
      var first = executor.submit(() -> quotaReservationAccepted(brand));
      var second = executor.submit(() -> quotaReservationAccepted(other));
      try {
        awaitWorkspaceLockWaiters();
      } finally {
        blocker.commit();
      }
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(60);
    assertThat(objects.retainedObjects(workspace.getId())).isEqualTo(1);
  }

  private boolean quotaReservationAccepted(Brand target) {
    try {
      lifecycle.reserve(workspace.getId(), target.getId(), 60, "pdf");
      return true;
    } catch (EvidenceQuotaExceededException expected) {
      return false;
    }
  }

  private void awaitWorkspaceLockWaiters() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    do {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock' AND query ILIKE '%workspaces%'",
              Integer.class);
      if (waiting != null && waiting >= 2) return;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Both quota transactions must actually wait on the workspace lock");
  }

  @Test
  void databaseRejectsPublicationAfterDeletionEvenWhenJavaValidationIsBypassed() {
    var reservation = reserve(10);
    clock.advance(Duration.ofSeconds(21));
    cleanup.runOnce();
    assertThatThrownBy(() -> rawInsert(reservation, brand.getId()))
        .hasStackTraceContaining("Quarantine publication is not authorized");
    assertThat(objects.evidenceReferences(reservation.reference())).isZero();
  }

  @Test
  void databaseRejectsCrossBrandReferenceAndUntrackedManagedKeys() {
    var reservation = reserve(10);
    Brand other = brands.saveAndFlush(new Brand("Other lifecycle brand", workspace));
    assertThatThrownBy(() -> rawInsert(reservation, other.getId()))
        .hasStackTraceContaining("Quarantine publication is not authorized");
    var missing =
        new QuarantineLifecycle.Reservation(
            java.util.UUID.randomUUID(),
            "quarantine/writes/" + workspace.getId() + "/" + java.util.UUID.randomUUID() + ".pdf",
            clock.instant().plusSeconds(10));
    assertThatThrownBy(() -> rawInsert(missing, brand.getId()))
        .hasStackTraceContaining("Quarantine publication is not authorized");
  }

  @Test
  void databaseRejectsDeletingOrReopeningGenerationTombstones() {
    var reservation = reserve(10);
    clock.advance(Duration.ofSeconds(21));
    cleanup.runOnce();
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM quarantine_objects WHERE id = ?", reservation.id()))
        .hasStackTraceContaining("Quarantine generation tombstones must be retained");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE quarantine_objects SET state = 'RESERVED' WHERE id = ?",
                    reservation.id()))
        .hasStackTraceContaining("Quarantine tombstone cannot be reopened");
  }

  @Test
  void databaseRejectsReclaimingReferencedObjectsAndForgedSizes() {
    var reservation = reserve(10);
    publish(reservation, brand);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE quarantine_objects SET state = 'DELETING' WHERE id = ?",
                    reservation.id()))
        .hasStackTraceContaining("Referenced quarantine data cannot be reclaimed");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE quarantine_objects SET retained_bytes = 1000 WHERE id = ?",
                    reservation.id()))
        .hasStackTraceContaining("Referenced quarantine data cannot be reclaimed");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE quarantine_objects SET object_key = 'quarantine/../escape' WHERE id = ?",
                    reservation.id()))
        .hasStackTraceContaining("Quarantine generation identity is immutable");
  }

  private void rawInsert(QuarantineLifecycle.Reservation reservation, java.util.UUID targetBrand) {
    jdbc.update(
        """
        INSERT INTO brand_evidence(id,created_at,updated_at,lock_version,brand_id,type,status,
            source_reference,checksum,captured_at,original_file_name,media_type)
        VALUES (?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0,?,'FILE','PENDING',?,?,CURRENT_TIMESTAMP,'source.pdf','application/pdf')
        """,
        java.util.UUID.randomUUID(),
        targetBrand,
        reservation.reference(),
        java.util.UUID.randomUUID().toString().replace("-", "").repeat(2));
  }
}
