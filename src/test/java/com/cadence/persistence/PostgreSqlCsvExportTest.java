package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.services.CsvExportRenderer;
import com.cadence.domain.*;
import com.cadence.support.CsvExportApiContract;
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
class PostgreSqlCsvExportTest extends CsvExportApiContract {
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void atomicCompletionUsesOneStampButRechecksExpiryAfterWrite(boolean expireAfterWrite) {
    var e = submit(scheduled());
    doAnswer(
            invocation -> {
              var actual = (java.time.Instant) invocation.callRealMethod();
              var completed =
                  jdbc.queryForObject(
                      "select completed_at from csv_exports where id=?",
                      java.sql.Timestamp.class,
                      e.id());
              return completed == null
                  ? actual
                  : expireAfterWrite ? e.deadline() : completed.toInstant().minusNanos(1000);
            })
        .when(exportClock)
        .instant();
    try {
      worker.execute(worker.claim(e.id()));
      assertThat(exports.status(e.id(), scope(), false).status())
          .isEqualTo(expireAfterWrite ? JobStatus.DEAD : JobStatus.SUCCEEDED);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from security_audit_events where resource_kind='csv_exports' and resource_id=? and action='PUBLISH'",
                  Integer.class,
                  e.id()))
          .isEqualTo(expireAfterWrite ? 0 : 1);
      if (!expireAfterWrite)
        assertThat(
                jdbc.queryForObject(
                    "select j.completed_at=e.completed_at from csv_exports e join jobs j on j.id=e.job_id where e.id=?",
                    Boolean.class,
                    e.id()))
            .isTrue();
    } finally {
      reset(exportClock);
    }
  }

  @Test
  void schedulingCaptureAndPublicationHaveBoundAuditWithoutPayload() {
    var s = scheduled();
    var e = submit(s);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_audit_events where workspace_id=? and actor_id=? and action='SCHEDULE_SCHEDULE'",
                Integer.class,
                workspace.getId(),
                actorId))
        .isPositive();
    worker.execute(worker.claim(e.id()));
    assertThat(
            jdbc.queryForList(
                "select action from security_audit_events where resource_kind='csv_exports' and resource_id=? order by id",
                String.class,
                e.id()))
        .containsExactly("CAPTURE", "PUBLISH");
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
  @MockitoSpyBean CsvExportRenderer renderer;
  @MockitoSpyBean java.time.Clock exportClock;
  @MockitoSpyBean com.cadence.application.ports.PrivateMediaStorage mediaStorage;
  @Autowired com.cadence.application.usecases.MediaAssetUseCase media;

  @Test
  void forgedStaleMembershipTokenCannotPollReplayOrDownload() {
    var s = scheduled();
    var key = "membership-export-key";
    var e = exports.submit(brand.getId(), scope(), key, s.request());
    worker.execute(worker.claim(e.id()));
    jdbc.update("delete from workspace_memberships where user_id=?", actorId);
    for (var call :
        List.<org.assertj.core.api.ThrowableAssert.ThrowingCallable>of(
            () -> exports.status(e.id(), scope(), false),
            () -> exports.submit(brand.getId(), scope(), key, s.request()),
            () -> exports.download(e.id(), scope())))
      assertThatThrownBy(call)
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void independentConcurrentWorkersClaimOnlyOneLease() throws Exception {
    var e = submit(scheduled());
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
      var results = Arrays.asList(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
      assertThat(results.stream().filter(Objects::nonNull).count()).isEqualTo(1);
      worker.execute(results.stream().filter(Objects::nonNull).findFirst().orElseThrow());
    }
    assertThat(exports.status(e.id(), scope(), false).attempts()).isEqualTo(1);
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void sqlIndependentlyRejectsCapturingAStaleRevision() throws Exception {
    var s = scheduled();
    var e = submit(s);
    posts.edit(
        s.post().id(),
        s.post().revisionId(),
        scope(),
        s.post().lockVersion(),
        content("New draft"));
    var id = UUID.randomUUID();
    var job = UUID.randomUUID();
    var key = "forged-export-key";
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      try (var q =
          c.prepareStatement(
              "insert into jobs(id,type,payload_reference,idempotency_key,status,attempts,max_attempts,next_attempt_at,created_at,updated_at,lock_version) values (?,'CSV_EXPORT',?,?,'PENDING',0,3,clock_timestamp(),clock_timestamp(),clock_timestamp(),0)")) {
        q.setObject(1, job);
        q.setString(2, "csv-export:" + id);
        q.setString(3, "csv:" + actorId + ":" + brand.getId() + ":" + key);
        q.executeUpdate();
      }
      try (var q =
          c.prepareStatement(
              "insert into csv_exports(id,created_at,updated_at,lock_version,brand_id,job_id,actor_id,client_key,request_digest,from_at,until_at,timezone,captured_at,deadline,expires_at,snapshot_checksum) select ?,clock_timestamp(),clock_timestamp(),0,brand_id,?,actor_id,?,request_digest,from_at,until_at,timezone,captured_at,deadline,expires_at,snapshot_checksum from csv_exports where id=?")) {
        q.setObject(1, id);
        q.setObject(2, job);
        q.setString(3, key);
        q.setObject(4, e.id());
        q.executeUpdate();
      }
      try (var q =
          c.prepareStatement(
              "insert into csv_export_items(export_id,item_order,revision_id,schedule_id,revision_version,content_checksum) select ?,item_order,revision_id,schedule_id,revision_version,content_checksum from csv_export_items where export_id=?")) {
        q.setObject(1, id);
        q.setObject(2, e.id());
        q.executeUpdate();
      }
      assertThatThrownBy(c::commit)
          .isInstanceOfSatisfying(
              java.sql.SQLException.class, x -> assertThat(x.getSQLState()).isEqualTo("23514"));
      c.rollback();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from csv_exports where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(1);
  }

  @Test
  void archivedAssetDuringPrivateDownloadReadDeniesBytes() throws Exception {
    var p = create();
    var a = media.create(brand.getId(), scope(), DemoMediaFixture.VIDEO_V1);
    a = media.review(a.id(), scope(), a.lockVersion(), true);
    var linked = posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var e = submit(scheduled(linked, 1));
    worker.execute(worker.claim(e.id()));
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
        .when(mediaStorage)
        .readMedia(any(), anyInt());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var task =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return exports.download(e.id(), scope());
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        media.archive(a.id(), scope(), a.lockVersion());
      } finally {
        release.countDown();
      }
      assertThatThrownBy(() -> task.get(20, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
  }

  @Test
  void retainedQuotaIncludesPendingOperationsAndDoesNotPreventExistingDownload() {
    var s = scheduled();
    var first = submit(s);
    for (int i = 1; i < 100; i++) submit(s);
    assertThatThrownBy(() -> submit(s))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("quota");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from csv_exports where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(100);
    worker.execute(worker.claim(first.id()));
    assertThat(exports.download(first.id(), scope()).bytes()).isNotEmpty();
  }

  @Test
  void privateMediaReferencesAreVerifiedAtCaptureWorkerAndDownload() {
    var p = create();
    var a = media.create(brand.getId(), scope(), DemoMediaFixture.VIDEO_V1);
    a = media.review(a.id(), scope(), a.lockVersion(), true);
    var linked = posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var s = scheduled(linked, 1);
    var e = submit(s);
    worker.execute(worker.claim(e.id()));
    assertThat(
            new String(
                exports.download(e.id(), scope()).bytes(), java.nio.charset.StandardCharsets.UTF_8))
        .contains(a.checksum(), "DEMO_FIXTURE", "APPROVED_DEMO");
    media.archive(a.id(), scope(), a.lockVersion());
    assertThatThrownBy(() -> exports.download(e.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select artifact is not null from csv_exports where id=?", Boolean.class, e.id()))
        .isTrue();
  }

  @Test
  void unavailableMediaRetriesBoundedlyAndNeverPublishesSuccessfulAssetOrPartialCsv() {
    var p = create();
    var a = media.create(brand.getId(), scope(), DemoMediaFixture.VIDEO_V1);
    a = media.review(a.id(), scope(), a.lockVersion(), true);
    var linked = posts.replaceMedia(p.id(), p.revisionId(), scope(), 0, List.of(a.id())).details();
    var e = submit(scheduled(linked, 1));
    doThrow(new IllegalStateException("Private path secret must never escape"))
        .when(mediaStorage)
        .readMedia(any(), anyInt());
    for (int attempt = 1; attempt <= 3; attempt++) {
      worker.execute(worker.claim(e.id()));
      var state = exports.status(e.id(), scope(), false);
      assertThat(state.status()).isEqualTo(attempt == 3 ? JobStatus.DEAD : JobStatus.FAILED);
      assertThat(state.errorCode()).isEqualTo("CSV_INPUT_UNAVAILABLE");
      jdbc.update("update jobs set next_attempt_at=clock_timestamp() where id=?", e.jobId());
    }
    assertThat(
            jdbc.queryForObject(
                "select artifact is null from csv_exports where id=?", Boolean.class, e.id()))
        .isTrue();
  }

  @Test
  void exclusiveDownloadExpiryAndJobDeadlineDoNotReleaseQuota() {
    var s = scheduled();
    var e = submit(s);
    worker.execute(worker.claim(e.id()));
    doReturn(e.downloadExpiresAt()).when(exportClock).instant();
    assertThatThrownBy(() -> exports.download(e.id(), scope()))
        .isInstanceOf(IllegalStateException.class);
    reset(exportClock);
    var pending = submit(s);
    var t = worker.claim(pending.id());
    doReturn(pending.deadline()).when(exportClock).instant();
    worker.execute(t);
    reset(exportClock);
    // Deadline after lease expiry is recovered by another bounded claim, without late publication.
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        pending.jobId());
    doReturn(pending.deadline()).when(exportClock).instant();
    var retry = worker.claim(pending.id());
    worker.execute(retry);
    reset(exportClock);
    assertThat(exports.status(pending.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from csv_exports where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(2);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"account", "membership", "brand", "workspace", "strategy"})
  void revocationDuringRenderingDeniesEntirePublication(String kind) throws Exception {
    var s = scheduled();
    var e = submit(s);
    var t = worker.claim(e.id());
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return call.callRealMethod();
            })
        .when(renderer)
        .render(any(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var task = pool.submit(() -> worker.execute(t));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        switch (kind) {
          case "account" ->
              jdbc.update("update cadence_users set active=false where id=?", actorId);
          case "membership" ->
              jdbc.update("delete from workspace_memberships where user_id=?", actorId);
          case "brand" ->
              jdbc.update("update brands set status='ARCHIVED' where id=?", brand.getId());
          case "workspace" ->
              jdbc.update("update workspaces set status='ARCHIVED' where id=?", workspace.getId());
          case "strategy" -> {
            var src = strategies.findById(source.getId()).orElseThrow();
            src.lock();
            strategies.saveAndFlush(src);
          }
          default -> throw new AssertionError();
        }
      } finally {
        release.countDown();
      }
      task.get(20, TimeUnit.SECONDS);
    }
    assertThat(jdbc.queryForObject("select status from jobs where id=?", String.class, e.jobId()))
        .isEqualTo("DEAD");
    assertThat(
            jdbc.queryForObject(
                "select artifact is null from csv_exports where id=?", Boolean.class, e.id()))
        .isTrue();
  }

  @Test
  void lostLeaseDuringRenderingCannotOverwriteNewWorkerPublication() throws Exception {
    var e = submit(scheduled());
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
        .when(renderer)
        .render(any(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var task = pool.submit(() -> worker.execute(old));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        jdbc.update(
            "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
            e.jobId());
        var replacement = worker.claim(e.id());
        assertThat(replacement.attempt()).isEqualTo(2);
        worker.execute(replacement);
      } finally {
        release.countDown();
      }
      task.get(20, TimeUnit.SECONDS);
    }
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(
            jdbc.queryForObject(
                "select published_attempt from csv_exports where id=?", Integer.class, e.id()))
        .isEqualTo(2);
  }

  @Test
  void runtimeRoleCanCompleteExportButCannotRewriteCapturedLinks() throws Exception {
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
    }
    setRuntimeRole(true);
    try {
      var e = submit(scheduled());
      worker.execute(worker.claim(e.id()));
      assertThat(exports.download(e.id(), scope()).bytes()).isNotEmpty();
      assertThatThrownBy(
              () -> jdbc.update("update csv_exports set actor_id=? where id=?", actorId, e.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(
              () -> jdbc.update("delete from csv_export_items where export_id=?", e.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      setRuntimeRole(false);
    }
  }

  private void setRuntimeRole(boolean enabled) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var held = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) held.add(datasource.getConnection());
      for (var c : held)
        try (var s = c.createStatement()) {
          s.execute(enabled ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : held) c.close();
    }
  }

  @Test
  void concurrentReplayCreatesExactlyOneOperation() throws Exception {
    var s = scheduled();
    var key = "concurrent-export-key";
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Future<UUID>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () -> {
                  SecurityContextHolder.getContext().setAuthentication(owner);
                  try {
                    start.await();
                    return exports.submit(brand.getId(), scope(), key, s.request()).id();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      assertThat(tasks.get(0).get(20, TimeUnit.SECONDS))
          .isEqualTo(tasks.get(1).get(20, TimeUnit.SECONDS));
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from csv_exports where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(1);
  }

  @Test
  void crashAfterCaptureRecoversFromPersistedLinksAndRejectsOldWorker() {
    var e = submit(scheduled());
    var first = worker.claim(e.id());
    doThrow(new AssertionError("Simulated process crash")).when(renderer).render(any(), any());
    assertThatThrownBy(() -> worker.execute(first)).isInstanceOf(AssertionError.class);
    reset(renderer);
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.RUNNING);
    jdbc.update(
        "update jobs set lease_expires_at=clock_timestamp()-interval '1 second' where id=?",
        e.jobId());
    var next = worker.claim(e.id());
    assertThat(next.attempt()).isEqualTo(2);
    worker.execute(first);
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.RUNNING);
    worker.execute(next);
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(exports.download(e.id(), scope()).bytes()).isNotEmpty();
    assertThat(
            jdbc.queryForObject(
                "select published_attempt from csv_exports where id=?", Integer.class, e.id()))
        .isEqualTo(2);
  }

  @Test
  void lateRendererCannotPublishAfterEditWhileNoDatabaseTransactionHeld() throws Exception {
    var s = scheduled();
    var e = submit(s);
    var ticket = worker.claim(e.id());
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
        .when(renderer)
        .render(any(), any());
    try (var pool = Executors.newSingleThreadExecutor()) {
      var result = pool.submit(() -> worker.execute(ticket));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        posts.edit(
            s.post().id(),
            s.post().revisionId(),
            scope(),
            s.post().lockVersion(),
            content("Concurrent draft"));
      } finally {
        release.countDown();
      }
      result.get(20, TimeUnit.SECONDS);
    }
    assertThat(exports.status(e.id(), scope(), false).status()).isEqualTo(JobStatus.DEAD);
    assertThat(
            jdbc.queryForObject(
                "select artifact is null from csv_exports where id=?", Boolean.class, e.id()))
        .isTrue();
  }

  @Test
  void sqlRejectsHistoryRewriteAndForgedSuccess() {
    var s = scheduled();
    var e = submit(s);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update csv_exports set snapshot_checksum=repeat('a',64) where id=?", e.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("delete from csv_export_items where export_id=?", e.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update jobs set status='SUCCEEDED',completed_at=clock_timestamp() where id=?",
                    e.jobId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    worker.execute(worker.claim(e.id()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update csv_exports set artifact=decode('00','hex'),lock_version=lock_version+1 where id=?",
                    e.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("delete from csv_exports where id=?", e.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
