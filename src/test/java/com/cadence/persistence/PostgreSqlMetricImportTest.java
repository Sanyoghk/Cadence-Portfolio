package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.services.MetricImportParser;
import com.cadence.domain.*;
import com.cadence.support.MetricImportApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlMetricImportTest extends MetricImportApiContract {
  @MockitoSpyBean java.time.Clock publicationClock;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void backwardClockSampleCannotMakeAtomicCompletionIncoherent(boolean expireAfterWrite) {
    var p = create();
    var payload = raw(List.of(row(p.id(), 7L, null)));
    var e = submit(payload);
    doAnswer(
            invocation -> {
              var actual = (java.time.Instant) invocation.callRealMethod();
              var completed =
                  jdbc.queryForObject(
                      "select completed_at from metric_imports where id=?",
                      java.sql.Timestamp.class,
                      e.id());
              // A wall clock need not be monotonic. Trigger the exact SQL seal invariant.
              return completed == null
                  ? actual
                  : expireAfterWrite ? e.deadline() : completed.toInstant().minusNanos(1000);
            })
        .when(publicationClock)
        .instant();
    try {
      assertThat(finish(e).status())
          .isEqualTo(expireAfterWrite ? JobStatus.DEAD : JobStatus.SUCCEEDED);
      if (expireAfterWrite) {
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from metric_observations where import_id=?",
                    Integer.class,
                    e.id()))
            .isZero();
        return;
      }
      assertThat(
              jdbc.queryForObject(
                  "select j.completed_at=e.completed_at from metric_imports e join jobs j on j.id=e.job_id where e.id=?",
                  Boolean.class,
                  e.id()))
          .isTrue();
    } finally {
      reset(publicationClock);
    }
  }

  @Test
  void publicationUsesOneDatabaseTransactionTimestampAndPreservesReportedTimes() {
    var p = create();
    var a = row(p.id(), 7L, null);
    var b =
        new MetricRow(
            a.postId(),
            a.platform(),
            a.windowFrom(),
            a.windowUntil(),
            a.observedAt().plusSeconds(1),
            8L,
            null,
            0L,
            null,
            null,
            null);
    var payload = raw(List.of(a, b));
    var e = submit(payload);
    // An independent DB assertion proves timestamp semantics, without a mocked application clock.
    jdbc.execute(
        """
      CREATE FUNCTION require_metric_transaction_stamp() RETURNS TRIGGER LANGUAGE plpgsql AS $$
      BEGIN IF NEW.ingested_at IS DISTINCT FROM transaction_timestamp() THEN
       RAISE EXCEPTION 'Metric transaction timestamp required' USING ERRCODE='23514';
      END IF; RETURN NEW; END $$;
      CREATE TRIGGER aaa_metric_transaction_stamp BEFORE INSERT ON metric_observations FOR EACH ROW EXECUTE FUNCTION require_metric_transaction_stamp();
      """);
    try {
      var result = finish(e);
      assertThat(result.status()).isEqualTo(JobStatus.SUCCEEDED);
      assertThat(result.rawChecksum()).isEqualTo(MetricRow.sha(payload));
      assertThat(
              jdbc.queryForObject(
                  "select count(distinct ingested_at) from metric_observations where import_id=?",
                  Integer.class,
                  e.id()))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select count(distinct observed_at) from metric_observations where import_id=?",
                  Integer.class,
                  e.id()))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from metric_observations where import_id=? and observed_at in (?,?)",
                  Integer.class,
                  e.id(),
                  a.observedAt().atOffset(java.time.ZoneOffset.UTC),
                  b.observedAt().atOffset(java.time.ZoneOffset.UTC)))
          .isEqualTo(2);
    } finally {
      jdbc.execute("drop trigger aaa_metric_transaction_stamp on metric_observations");
      jdbc.execute("drop function require_metric_transaction_stamp()");
    }
  }

  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @MockitoSpyBean MetricImportParser parser;

  @Test
  void concurrentReplayAndIndependentImportsDeduplicate() throws Exception {
    var p = create();
    var payload = raw(List.of(row(p.id(), 7L, null)));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return metrics.submit(
                      brand.getId(), scope(), "concurrent-metrics-key-001", "MANUAL_JSON", payload);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return metrics.submit(
                      brand.getId(), scope(), "concurrent-metrics-key-001", "MANUAL_JSON", payload);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      start.countDown();
      var first = a.get(20, TimeUnit.SECONDS);
      assertThat(b.get(20, TimeUnit.SECONDS).id()).isEqualTo(first.id());
      var second = submit(payload);
      var ta = worker.claim(first.id());
      var tb = worker.claim(second.id());
      var aa = pool.submit(() -> worker.execute(ta));
      var bb = pool.submit(() -> worker.execute(tb));
      aa.get(20, TimeUnit.SECONDS);
      bb.get(20, TimeUnit.SECONDS);
      assertThat(read(p.id(), true).items()).hasSize(1);
      assertThat(metrics.status(first.id(), scope(), false).observationIds())
          .isEqualTo(metrics.status(second.id(), scope(), false).observationIds());
    }
  }

  @Test
  void twoWorkersAndRedeliveryPublishOnce() throws Exception {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return worker.claim(e.id());
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return worker.claim(e.id());
              });
      start.countDown();
      var tickets = Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
      assertThat(tickets.stream().filter(Objects::nonNull).count()).isEqualTo(1);
      var t = tickets.stream().filter(Objects::nonNull).findFirst().orElseThrow();
      worker.execute(t);
      worker.execute(t);
    }
    assertThat(read(p.id(), true).items()).hasSize(1);
    assertThat(metrics.status(e.id(), scope(), false).attempts()).isEqualTo(1);
  }

  @Test
  void crashExpiredLeaseAndLateWorkerFenced() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var old = worker.claim(e.id());
    doThrow(new AssertionError("simulated process loss outside transaction"))
        .when(parser)
        .parse(anyString(), any());
    assertThatThrownBy(() -> worker.execute(old)).isInstanceOf(AssertionError.class);
    reset(parser);
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        e.jobId());
    var fresh = worker.claim(e.id());
    assertThat(fresh.attempt()).isEqualTo(2);
    worker.execute(fresh);
    worker.execute(old);
    assertThat(read(p.id(), true).items()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "select published_attempt from metric_imports where id=?", Integer.class, e.id()))
        .isEqualTo(2);
  }

  @Test
  void recoverableDatabaseFailureKeepsRawAndRetries() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    doThrow(new org.springframework.dao.TransientDataAccessResourceException("test failure"))
        .when(parser)
        .parse(anyString(), any());
    worker.execute(worker.claim(e.id()));
    assertThat(metrics.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.FAILED);
    assertThat(read(p.id(), true).items()).isEmpty();
    reset(parser);
    jdbc.update("update jobs set next_attempt_at=clock_timestamp() where id=?", e.jobId());
    worker.execute(worker.claim(e.id()));
    assertThat(metrics.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void membershipRevocationDuringParsingDeniesPublicationAndReplay() throws Exception {
    race("membership");
  }

  @Test
  void parentArchiveDuringParsingDeniesPublication() throws Exception {
    race("archive");
  }

  private void race(String change) throws Exception {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var t = worker.claim(e.id());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return call.callRealMethod();
            })
        .when(parser)
        .parse(anyString(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var task = pool.submit(() -> worker.execute(t));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        if (change.equals("membership"))
          jdbc.update("delete from workspace_memberships where user_id=?", actorId);
        else jdbc.update("update brands set status='ARCHIVED' where id=?", brand.getId());
      } finally {
        release.countDown();
      }
      task.get(20, TimeUnit.SECONDS);
    }
    assertThat(jdbc.queryForObject("select status from jobs where id=?", String.class, e.jobId()))
        .isEqualTo("DEAD");
    assertThat(observations.findTopByPostIdAndObservedAtOrderByRevisionNumberDesc(p.id(), AT))
        .isEmpty();
    assertThatThrownBy(() -> metrics.status(e.id(), scope(), false))
        .isInstanceOfAny(
            org.springframework.security.access.AccessDeniedException.class,
            IllegalStateException.class);
  }

  @Test
  void sqlHistoryAndRootBindingGuards() {
    var p = create();
    var e = finish(submit(raw(List.of(row(p.id(), 1L, null)))));
    var id = e.observationIds().getFirst();
    for (var q :
        List.of(
            "update metric_imports set origin='CSV' where id='" + e.id() + "'",
            "delete from metric_import_results where import_id='" + e.id() + "'",
            "update metric_observations set reach=2 where id='" + id + "'",
            "delete from metric_observations where id='" + id + "'",
            "update jobs set type='OTHER' where id='" + e.jobId() + "'",
            "delete from metric_imports where id='" + e.id() + "'"))
      assertThatThrownBy(() -> jdbc.update(q))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void sqlCannotSealSuccessWithoutResults() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    worker.claim(e.id());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update jobs set status='SUCCEEDED',completed_at=clock_timestamp(),lease_owner=null,lease_expires_at=null where id=?",
                    e.jobId()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(read(p.id(), true).items()).isEmpty();
  }

  @Test
  void runtimeRoleCompletesImportButCannotRewriteAudit() throws Exception {
    var p = create();
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
      var e = finish(submit(raw(List.of(row(p.id(), 1L, null)))));
      assertThat(e.status()).isEqualTo(JobStatus.SUCCEEDED);
      assertThatThrownBy(
              () -> jdbc.update("update metric_imports set actor_id=? where id=?", actorId, e.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "delete from metric_observations where id=?", e.observationIds().getFirst()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      setRole(false);
    }
  }

  @Test
  void sqlRejectsObservationWithoutAtomicPublication() {
    var p = create();
    var row = row(p.id(), 1L, null);
    var e = submit(raw(List.of(row)));
    var t = worker.claim(e.id());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into metric_observations(id,created_at,updated_at,lock_version,post_id,import_id,revision_number,window_from,window_until,observed_at,ingested_at,value_checksum,reach,saves) values(?,now(),now(),0,?,?,1,?,?,?,clock_timestamp(),?,1,0)",
                    UUID.randomUUID(),
                    p.id(),
                    e.id(),
                    java.sql.Timestamp.from(FROM),
                    java.sql.Timestamp.from(UNTIL),
                    java.sql.Timestamp.from(AT),
                    row.digest()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(read(p.id(), true).items()).isEmpty();
    worker.execute(t);
    assertThat(metrics.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void threeExpiredAttemptsCannotPublish() {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    for (int i = 1; i <= 3; i++) {
      var t = worker.claim(e.id());
      assertThat(t.attempt()).isEqualTo(i);
      jdbc.update(
          "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
          e.jobId());
    }
    assertThat(worker.claim(e.id())).isNull();
    assertThat(metrics.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThat(read(p.id(), true).items()).isEmpty();
  }

  @Test
  void leaseLossWhileParsingCannotPublishOverReplacement() throws Exception {
    var p = create();
    var e = submit(raw(List.of(row(p.id(), 1L, null))));
    var old = worker.claim(e.id());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var once = new java.util.concurrent.atomic.AtomicBoolean();
    doAnswer(
            call -> {
              if (once.compareAndSet(false, true)) {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              }
              return call.callRealMethod();
            })
        .when(parser)
        .parse(anyString(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var task = pool.submit(() -> worker.execute(old));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        jdbc.update(
            "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
            e.jobId());
        worker.execute(worker.claim(e.id()));
      } finally {
        release.countDown();
      }
      task.get(20, TimeUnit.SECONDS);
    }
    assertThat(read(p.id(), true).items()).hasSize(1);
    assertThat(
            jdbc.queryForObject(
                "select published_attempt from metric_imports where id=?", Integer.class, e.id()))
        .isEqualTo(2);
  }

  @Test
  void concurrentCorrectionsHaveOneWinnerAndRetainedHistory() throws Exception {
    var p = create();
    var original = finish(submit(raw(List.of(row(p.id(), 1L, null)))));
    var prior = original.observationIds().getFirst();
    var a = submit(raw(List.of(row(p.id(), 2L, prior))));
    var b = submit(raw(List.of(row(p.id(), 3L, prior))));
    var ta = worker.claim(a.id());
    var tb = worker.claim(b.id());
    try (var pool = Executors.newFixedThreadPool(2)) {
      var aa = pool.submit(() -> worker.execute(ta));
      var bb = pool.submit(() -> worker.execute(tb));
      aa.get(20, TimeUnit.SECONDS);
      bb.get(20, TimeUnit.SECONDS);
    }
    assertThat(
            List.of(
                metrics.status(a.id(), scope(), false).status(),
                metrics.status(b.id(), scope(), false).status()))
        .containsExactlyInAnyOrder(JobStatus.SUCCEEDED, JobStatus.DEAD);
    assertThat(read(p.id(), true).items()).hasSize(2);
  }

  private void setRole(boolean enabled) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var connections = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) connections.add(pool.getConnection());
      for (var c : connections)
        try (var s = c.createStatement()) {
          s.execute(enabled ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : connections) c.close();
    }
  }
}
