package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.EvidenceRegistrationQuotaContract;
import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.domain.Brand;
import com.cadence.domain.EvidenceType;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "cadence.evidence.registration.max-workspace-evidence=2",
      "cadence.evidence.registration.max-workspace-registrations-per-hour=1",
      "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
    })
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class PostgreSqlEvidenceRegistrationQuotaTest extends EvidenceRegistrationQuotaContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Autowired DataSource datasource;
  @Autowired JdbcTemplate jdbc;

  @Test
  void joiningAnExistingRepeatableReadTransactionIsRejectedWithoutCreatingRowsOrJobs() {
    var inherited = new TransactionTemplate(manager);
    inherited.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    long beforeJobs = jobs.count();
    assertThatThrownBy(
            () ->
                inherited.execute(
                    status -> register(brand, EvidenceType.TEXT, "Rejected stale snapshot")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Evidence operation requires READ_COMMITTED isolation");
    assertThat(evidence.countInWorkspace(workspace.getId())).isZero();
    assertThat(jobs.count()).isEqualTo(beforeJobs);
  }

  @Test
  void registrationsRefreshQuotaAfterWaitingDespiteRepeatableReadDatasourceDefault()
      throws Exception {
    Brand other = brands.saveAndFlush(new Brand("Other isolation brand", workspace));
    long beforeJobs = jobs.count();
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
      var first =
          executor.submit(() -> quotaRegistrationAccepted(brand, "First competing registration"));
      var second =
          executor.submit(() -> quotaRegistrationAccepted(other, "Second competing registration"));
      try {
        awaitWorkspaceLockWaiters();
      } finally {
        blocker.commit();
      }
      assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(1);
    assertThat(jobs.count()).isEqualTo(beforeJobs + 1);
  }

  private boolean quotaRegistrationAccepted(Brand target, String text) {
    authenticate(workspace.getId());
    try {
      register(target, EvidenceType.TEXT, text);
      return true;
    } catch (EvidenceQuotaExceededException expected) {
      return false;
    } finally {
      SecurityContextHolder.clearContext();
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

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
