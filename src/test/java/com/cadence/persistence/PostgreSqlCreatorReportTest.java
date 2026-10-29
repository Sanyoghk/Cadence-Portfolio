package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlCreatorReportTest extends com.cadence.support.CreatorReportApiContract {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void atomicCompletionUsesOneStampButRechecksExpiryAfterWrite(boolean expireAfterWrite) {
    var r = submitReport(List.of(), "atomic-clock-report");
    doAnswer(
            invocation -> {
              var actual = (java.time.Instant) invocation.callRealMethod();
              var completed =
                  jdbc.queryForObject(
                      "select completed_at from creator_reports where id=?",
                      java.sql.Timestamp.class,
                      r.id());
              return completed == null
                  ? actual
                  : expireAfterWrite ? r.deadline() : completed.toInstant().minusNanos(1000);
            })
        .when(reportClock)
        .instant();
    try {
      finishReport(r);
      assertThat(reportApi.status(r.id(), scope(), false).status())
          .isEqualTo(expireAfterWrite ? JobStatus.DEAD : JobStatus.SUCCEEDED);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from security_audit_events where resource_kind='creator_reports' and resource_id=? and action='PUBLISH'",
                  Integer.class,
                  r.id()))
          .isEqualTo(expireAfterWrite ? 0 : 1);
      if (!expireAfterWrite)
        assertThat(
                jdbc.queryForObject(
                    "select j.completed_at=e.completed_at from creator_reports e join jobs j on j.id=e.job_id where e.id=?",
                    Boolean.class,
                    r.id()))
            .isTrue();
    } finally {
      reset(reportClock);
    }
  }

  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @Autowired org.springframework.transaction.PlatformTransactionManager reportTransactions;

  @Test
  void genericIngestQueueCannotClaimOrRecoverReportJobs() {
    var r = submitReport(List.of(), "isolated-report-queue");
    var tx = new org.springframework.transaction.support.TransactionTemplate(reportTransactions);
    tx.executeWithoutResult(
        t ->
            assertThat(
                    reportJobs.findClaimable(
                        List.of(JobStatus.PENDING, JobStatus.FAILED),
                        clock.instant(),
                        org.springframework.data.domain.PageRequest.of(0, 10000)))
                .noneMatch(j -> j.getType().equals("CREATOR_REPORT")));
    reportWorker.claim(r.id());
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        r.jobId());
    tx.executeWithoutResult(
        t ->
            assertThat(
                    reportJobs.findExpiredLeases(
                        JobStatus.RUNNING,
                        clock.instant(),
                        org.springframework.data.domain.PageRequest.of(0, 10000)))
                .noneMatch(j -> j.getType().equals("CREATOR_REPORT")));
    finishReport(r);
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void runtimeRoleCanRenderAndReadButCannotChangeSnapshot() throws Exception {
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin;end if;end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
    }
    setRole(true);
    try {
      var r = submitReport(List.of(), "runtime-report");
      finishReport(r);
      assertThat(reportApi.result(r.id(), scope()).selectedRows()).isZero();
      assertThat(reportApi.download(r.id(), scope()).bytes()).isNotEmpty();
      assertThatThrownBy(
              () -> jdbc.update("update creator_reports set snapshot='{}' where id=?", r.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(() -> jdbc.update("delete from creator_reports where id=?", r.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      setRole(false);
    }
  }

  private void setRole(boolean enabled) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var cs = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) cs.add(pool.getConnection());
      for (var c : cs)
        try (var s = c.createStatement()) {
          s.execute(enabled ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : cs) c.close();
    }
  }

  @Test
  void retainedQuotaIncludesPendingAndExpiredReports() {
    for (int i = 0; i < 100; i++) submitReport(List.of(), "quota-" + i);
    assertThatThrownBy(() -> submitReport(List.of(), "quota-overflow"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(reportRows.countByBrandId(brand.getId())).isEqualTo(100);
  }

  @Test
  void revocationDuringRenderingPreventsPublication() {
    var r = submitReport(List.of(), "render-revocation");
    org.mockito.Mockito.doAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actorId);
              return i.callRealMethod();
            })
        .when(reportRenderer)
        .render(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    finishReport(r);
    assertThat(jdbc.queryForObject("select status from jobs where id=?", String.class, r.jobId()))
        .isEqualTo("DEAD");
    assertThat(
            jdbc.queryForObject(
                "select artifact is null from creator_reports where id=?", Boolean.class, r.id()))
        .isTrue();
  }

  @Test
  void rendererRetriesAreBoundedAndDeadlineIsExclusive() {
    var r = submitReport(List.of(), "bounded-render-retry");
    org.mockito.Mockito.doThrow(new IllegalStateException("private payload"))
        .when(reportRenderer)
        .render(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    for (int attempt = 1; attempt <= 3; attempt++) {
      finishReport(r);
      var state = reportApi.status(r.id(), scope(), false);
      assertThat(state.attempts()).isEqualTo(attempt);
      assertThat(state.status()).isEqualTo(attempt == 3 ? JobStatus.DEAD : JobStatus.FAILED);
      jdbc.update("update jobs set next_attempt_at=clock_timestamp() where id=?", r.jobId());
    }
    assertThat(reportRows.findById(r.id()).orElseThrow().getArtifactChecksum()).isNull();
    org.mockito.Mockito.reset(reportRenderer);
    var expired = submitReport(List.of(), "exclusive-deadline");
    reportWorker.claim(expired.id());
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        expired.jobId());
    org.mockito.Mockito.doReturn(expired.deadline()).when(reportClock).instant();
    try {
      reportWorker.execute(reportWorker.claim(expired.id()));
      assertThat(reportApi.status(expired.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
      assertThat(reportApi.status(expired.id(), scope(), false).errorCode())
          .isEqualTo("REPORT_DEADLINE_EXCEEDED");
    } finally {
      org.mockito.Mockito.reset(reportClock);
    }
  }

  @Test
  void sqlForgedSupportingIdIsRejectedByEvidenceGuardNotAnUnrelatedConstraint() throws Exception {
    seedRecommendation();
    var advice = generate("sql-report-advice");
    var r = submitReport(List.of(advice.id()), "sql-capture");
    var id = UUID.randomUUID();
    var job = UUID.randomUUID();
    String key = "d".repeat(64);
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      try (var q =
          c.prepareStatement(
              "insert into jobs(id,type,payload_reference,idempotency_key,status,attempts,max_attempts,next_attempt_at,created_at,updated_at,lock_version) values(?,'CREATOR_REPORT',?,?,'PENDING',0,3,clock_timestamp(),clock_timestamp(),clock_timestamp(),0)")) {
        q.setObject(1, job);
        q.setString(2, "report:" + id);
        q.setString(3, "report:" + actorId + ":" + workspace.getId() + ":" + key);
        q.executeUpdate();
      }
      try (var q =
          c.prepareStatement(
              "with x as(select r.*,clock_timestamp() as t from creator_reports r where id=?), y as(select x.*,jsonb_set(jsonb_set(snapshot::jsonb,'{capturedAt}',to_jsonb(t)),'{recommendations,0,supportingObservations,0,id}',to_jsonb(?::text))::text as changed from x) insert into creator_reports select (jsonb_populate_record(null::creator_reports,to_jsonb(y)||jsonb_build_object('id',?::uuid,'job_id',?::uuid,'key_digest',?::text,'created_at',t,'updated_at',t,'captured_at',t,'deadline',t+interval '300 seconds','expires_at',t+interval '86400 seconds','snapshot',changed,'snapshot_checksum',encode(sha256(convert_to(changed,'UTF8')),'hex')))).* from y")) {
        q.setObject(1, r.id());
        q.setString(2, UUID.randomUUID().toString());
        q.setObject(3, id);
        q.setObject(4, job);
        q.setString(5, key);
        q.executeUpdate();
      }
      assertThatThrownBy(c::commit)
          .isInstanceOf(java.sql.SQLException.class)
          .satisfies(
              e -> {
                assertThat(((java.sql.SQLException) e).getSQLState()).isEqualTo("23514");
                assertThat(e.getMessage()).contains("Forged report supporting observation");
              });
      c.rollback();
    }
    assertThat(reportRows.findById(id)).isEmpty();
  }

  @Test
  void concurrentReplayAndClaimsProduceOneOperationAndResult() throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Future<com.cadence.application.results.CreatorReportDetails>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () -> {
                  SecurityContextHolder.getContext().setAuthentication(owner);
                  try {
                    start.await();
                    return submitReport(List.of(), "concurrent-report");
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      var a = tasks.getFirst().get(20, TimeUnit.SECONDS);
      var b = tasks.getLast().get(20, TimeUnit.SECONDS);
      assertThat(a.id()).isEqualTo(b.id());
      var c = pool.submit(() -> reportWorker.claim(a.id()));
      var d = pool.submit(() -> reportWorker.claim(a.id()));
      var claims = Arrays.asList(c.get(20, TimeUnit.SECONDS), d.get(20, TimeUnit.SECONDS));
      assertThat(claims.stream().filter(Objects::nonNull).count()).isEqualTo(1);
      reportWorker.execute(claims.stream().filter(Objects::nonNull).findFirst().orElseThrow());
      assertThat(reportApi.status(a.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
    }
  }

  @Test
  void expiredLeaseRecoveredByReplacementWorkerRejectsLateAttempt() {
    var r = submitReport(List.of(), "restart-report");
    var stale = reportWorker.claim(r.id());
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        r.jobId());
    var fresh = reportWorker.claim(r.id());
    assertThat(fresh.attempt()).isEqualTo(2);
    reportWorker.execute(stale);
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.RUNNING);
    reportWorker.execute(fresh);
    assertThat(reportApi.status(r.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
    reportWorker.execute(stale);
    assertThat(reportApi.status(r.id(), scope(), false).attempts()).isEqualTo(2);
  }

  @Test
  void revokedMembershipDeniesWorkerResultAndDownloadWithoutErasingSnapshot() {
    var r = submitReport(List.of(), "revoked-report");
    var ticket = reportWorker.claim(r.id());
    jdbc.update("delete from workspace_memberships where user_id=?", actorId);
    reportWorker.execute(ticket);
    assertThat(jdbc.queryForObject("select status from jobs where id=?", String.class, r.jobId()))
        .isEqualTo("DEAD");
    assertThatThrownBy(() -> reportApi.download(r.id(), scope()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(
            jdbc.queryForObject(
                "select snapshot_checksum from creator_reports where id=?", String.class, r.id()))
        .isEqualTo(r.snapshotChecksum());
  }

  @Test
  void immutableCaptureAndTerminalArtifactCannotBeRewrittenInSql() {
    var r = submitReport(List.of(), "immutable-report");
    finishReport(r);
    assertThatThrownBy(
            () -> jdbc.update("update creator_reports set snapshot='{}' where id=?", r.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("delete from creator_reports where id=?", r.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update creator_reports set artifact=convert_to('forged','UTF8') where id=?",
                    r.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("update jobs set type='CSV_EXPORT' where id=?", r.jobId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
