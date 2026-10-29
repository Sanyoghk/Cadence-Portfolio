package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.results.*;
import com.cadence.domain.*;
import com.cadence.support.CalendarApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlCalendarTest extends CalendarApiContract {
  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void historyCapPreservesFinalCancellationAndSqlRejectsSkippedVersions() {
    var p = approved();
    var e = schedule(p, 0, time());
    var prior = schedules.findById(e.id()).orElseThrow();
    org.springframework.test.util.ReflectionTestUtils.setField(prior, "eventNumber", 999);
    var current = revisions.findById(p.revisionId()).orElseThrow();
    assertThatThrownBy(
            () ->
                new PostScheduleEvent(
                    current,
                    prior,
                    PostScheduleEvent.Action.SCHEDULE,
                    time(),
                    actorId,
                    clock.instant()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            new PostScheduleEvent(
                    current, prior, PostScheduleEvent.Action.CANCEL, null, actorId, clock.instant())
                .getEventNumber())
        .isEqualTo(1000);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into post_schedule_events select gen_random_uuid(),created_at,updated_at,lock_version,brand_id,post_id,revision_id,1001,action,revision_lock_version,content_checksum,timezone,local_time,offset_seconds,scheduled_at,rules_version,actor_id,clock_timestamp() from post_schedule_events where id=?",
                    e.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void concurrentApprovalAndSchedulingNeverSchedulesDraft() throws Exception {
    var p = create();
    var t = time();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var command =
          new com.cadence.application.usecases.PostCalendarUseCase.Change(
              p.id(), p.revisionId(), 1, 0, PostScheduleEvent.Action.SCHEDULE, t);
      var one =
          pool.submit(
              () -> race(start, () -> calendar.change(brand.getId(), scope(), List.of(command))));
      var two =
          pool.submit(() -> race(start, () -> posts.approve(p.id(), p.revisionId(), scope(), 0)));
      start.countDown();
      boolean scheduled = one.get(20, TimeUnit.SECONDS);
      assertThat(two.get(20, TimeUnit.SECONDS)).isTrue();
      assertThat(window(t)).hasSize(scheduled ? 1 : 0);
    }
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void javaSqlChecksumContractIsExact() {
    var draft =
        posts.create(
            brand.getId(),
            scope(),
            source.getId(),
            1,
            PlaybookContent.Channel.INSTAGRAM,
            new PostContent(
                "Бренд 🌿", "Caption\nСтрока", List.of("#привет"), PostFormat.IMAGE, "Идея", null));
    var p = posts.approve(draft.id(), draft.revisionId(), scope(), 0);
    assertThat(
            jdbc.queryForObject("select post_schedule_checksum(?)", String.class, p.revisionId()))
        .isEqualTo(
            PostScheduleEvent.checksum(
                new PostContent(
                    p.title(),
                    p.caption(),
                    p.hashtags(),
                    p.format(),
                    p.mediaPrompt(),
                    p.videoScript())));
  }

  @Test
  void sqlHistoryChecksumTimezoneActorAndDraftBypassesDenied() {
    var p = approved();
    var e = schedule(p, 0, time());
    for (String sql :
        List.of(
            "update post_schedule_events set timezone='UTC' where id=?",
            "delete from post_schedule_events where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, e.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (String override : List.of("repeat('a',64)", "content_checksum")) {
      String timezone = override.equals("content_checksum") ? "'Europe/Paris'" : "timezone";
      assertThatThrownBy(() -> cloneEvent(e.id(), p, override, timezone, "actor_id"))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    var outsider =
        users.saveAndFlush(
            new CadenceUser("schedule-outsider-" + UUID.randomUUID(), UserRole.OWNER, Set.of()));
    assertThatThrownBy(
            () ->
                cloneEvent(
                    e.id(), p, "content_checksum", "timezone", "'" + outsider.getId() + "'::uuid"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var branch =
        posts.edit(p.id(), p.revisionId(), scope(), p.lockVersion(), content("Draft")).details();
    assertThatThrownBy(
            () ->
                cloneEvent(
                    e.id(),
                    branch,
                    "post_schedule_checksum('" + branch.revisionId() + "')",
                    "timezone",
                    "actor_id"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select content_checksum=post_schedule_checksum(revision_id) from post_schedule_events where id=?",
                Boolean.class,
                e.id()))
        .isTrue();
  }

  private void cloneEvent(UUID id, PostDetails p, String checksum, String timezone, String actor) {
    // All fragments are test-owned constants, never request values.
    jdbc.update(
        "insert into post_schedule_events(id,created_at,updated_at,lock_version,brand_id,post_id,revision_id,event_number,action,revision_lock_version,content_checksum,timezone,local_time,offset_seconds,scheduled_at,rules_version,actor_id,occurred_at) select ?,now(),now(),0,brand_id,?,?,coalesce((select max(event_number) from post_schedule_events where post_id=?),0)+1,'SCHEDULE',?,"
            + checksum
            + ","
            + timezone
            + ",local_time,offset_seconds,scheduled_at,rules_version,"
            + actor
            + ",clock_timestamp() from post_schedule_events where id=?",
        UUID.randomUUID(),
        p.id(),
        p.revisionId(),
        p.id(),
        p.lockVersion(),
        id);
  }

  @Test
  void deferredSqlDuplicateSlotGuardAndIndexesArePresent() {
    var a = approved();
    var b = approved();
    var e = schedule(a, 0, time());
    assertThatThrownBy(
            () ->
                cloneEvent(
                    e.id(),
                    b,
                    "post_schedule_checksum('" + b.revisionId() + "')",
                    "timezone",
                    "actor_id"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from pg_indexes where tablename='post_schedule_events' and indexname in ('post_schedule_version_unique','post_schedule_time_idx')",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  void runtimeCanScheduleButCannotRewriteOrDelete() throws Exception {
    var p = approved();
    var e = schedule(p, 0, time());
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute(
          "grant select,insert,update,delete on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      s.execute("set role cadence_runtime");
      for (String sql :
          List.of(
              "update post_schedule_events set timezone='UTC' where id=?",
              "delete from post_schedule_events where id=?"))
        try (var q = c.prepareStatement(sql)) {
          q.setObject(1, e.id());
          assertThatThrownBy(q::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
      s.execute(
          "insert into post_schedule_events(id,created_at,updated_at,lock_version,brand_id,post_id,revision_id,event_number,action,revision_lock_version,content_checksum,timezone,local_time,offset_seconds,scheduled_at,rules_version,actor_id,occurred_at) select '"
              + UUID.randomUUID()
              + "',now(),now(),0,brand_id,post_id,revision_id,2,'CANCEL',revision_lock_version,content_checksum,timezone,local_time,offset_seconds,scheduled_at,rules_version,actor_id,clock_timestamp() from post_schedule_events where id='"
              + e.id()
              + "'");
      s.execute("reset role");
    }
    assertThat(schedules.findTopByPostIdOrderByEventNumberDesc(p.id()).orElseThrow().getAction())
        .isEqualTo(PostScheduleEvent.Action.CANCEL);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void concurrentReplayOrSlotCollisionHasOneWinner(boolean samePost) throws Exception {
    var a = approved();
    var b = samePost ? a : approved();
    var t = time();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> race(start, () -> schedule(a, 0, t)));
      var two = pool.submit(() -> race(start, () -> schedule(b, 0, t)));
      start.countDown();
      assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(window(t)).hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"workspace", "brand", "membership", "account", "source"})
  void revocationCommittedWhileScheduleWaitsIsDenied(String mode) throws Exception {
    var p = approved();
    var t = time();
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      // Hold the first shared mutex needed by scheduling before starting its transaction.
      String sql =
          mode.equals("account") || mode.equals("membership")
              ? "select id from cadence_users where id=? for update"
              : "select id from workspaces where id=? for update";
      try (var q = c.prepareStatement(sql)) {
        q.setObject(
            1, mode.equals("account") || mode.equals("membership") ? actorId : workspace.getId());
        q.executeQuery().close();
      }
      var started = new CountDownLatch(1);
      var result = pool.submit(() -> race(started, () -> schedule(p, 0, t)));
      started.countDown();
      // Prove the worker is waiting on a PostgreSQL lock, rather than relying on a timing sleep.
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      boolean waiting = false;
      while (System.nanoTime() < end) {
        try (var q =
                c.prepareStatement(
                    "select exists(select 1 from pg_stat_activity where datname=current_database() and pid<>pg_backend_pid() and wait_event_type='Lock')");
            var rows = q.executeQuery()) {
          rows.next();
          if (rows.getBoolean(1)) {
            waiting = true;
            break;
          }
        }
        Thread.sleep(10);
      }
      assertThat(waiting).isTrue();
      String mutation =
          switch (mode) {
            case "account" -> "update cadence_users set active=false where id='" + actorId + "'";
            case "membership" ->
                "delete from workspace_memberships where user_id='" + actorId + "'";
            case "brand" ->
                "update brands set status='ARCHIVED',lock_version=lock_version+1 where id='"
                    + brand.getId()
                    + "'";
            case "source" ->
                "update strategies set status='LOCKED',lock_version=lock_version+1 where id='"
                    + source.getId()
                    + "'";
            default ->
                "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id='"
                    + workspace.getId()
                    + "'";
          };
      try (var s = c.createStatement()) {
        s.executeUpdate(mutation);
      }
      c.commit();
      assertThat(result.get(20, TimeUnit.SECONDS)).isFalse();
    }
    assertThat(schedules.findTopByPostIdOrderByEventNumberDesc(p.id())).isEmpty();
  }

  @Test
  void concurrentApprovedEditLeavesScheduleIneffectiveRegardlessOfOrdering() throws Exception {
    var p = approved();
    var t = time();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var one = pool.submit(() -> race(start, () -> schedule(p, 0, t)));
      var two =
          pool.submit(
              () ->
                  race(
                      start,
                      () ->
                          posts.edit(
                              p.id(),
                              p.revisionId(),
                              scope(),
                              p.lockVersion(),
                              content("New draft"))));
      start.countDown();
      one.get(20, TimeUnit.SECONDS);
      assertThat(two.get(20, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(window(t).getContent())
        .allSatisfy(
            e ->
                assertThat(e.eligibility()).isEqualTo(ScheduleDetails.Eligibility.CONTENT_CHANGED));
  }

  private boolean race(CountDownLatch start, Runnable action) throws Exception {
    start.await(10, TimeUnit.SECONDS);
    SecurityContextHolder.getContext().setAuthentication(owner);
    try {
      action.run();
      return true;
    } catch (IllegalStateException
        | org.springframework.security.access.AccessDeniedException expected) {
      return false;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }
}
