package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.domain.*;
import com.cadence.support.StrategyApiContract;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlStrategyApiTest extends StrategyApiContract {
  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @Autowired com.cadence.application.usecases.BuildStrategyUseCase build;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void waitsForWorkspaceArchiveBeforeLockingStrategy(boolean approval) throws Exception {
    var id = generate();
    try (var connection = datasource.getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?")) {
        statement.setObject(1, workspace.getId());
        statement.executeUpdate();
      }
      var attempt =
          executor.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  assertThatThrownBy(
                          () -> {
                            if (approval) manage.approve(id, Set.of(workspace.getId()), 0, actorId);
                            else
                              manage.updatePlaybook(
                                  id,
                                  Set.of(workspace.getId()),
                                  0,
                                  content("Denied"),
                                  com.cadence.support.StrategyFixtures.editPlaybook());
                          })
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("archived");
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%workspaces%'",
                  Integer.class)
              > 0) {
            waiting = true;
            break;
          }
          Thread.sleep(20);
        }
        assertThat(waiting).as("Workspace lock precedes Strategy lock").isTrue();
        try (var statement =
            connection.prepareStatement("select id from strategies where id=? for update nowait")) {
          statement.setObject(1, id);
          try (var row = statement.executeQuery()) {
            assertThat(row.next()).isTrue();
          }
        }
      } finally {
        connection.commit();
      }
      attempt.get(15, TimeUnit.SECONDS);
    }
    assertThat(strategies.findById(id).orElseThrow().getLockVersion()).isZero();
  }

  @Test
  void generationAndApprovedEditShareVersionAllocationLock() throws Exception {
    var id = approveGenerated();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var edit = executor.submit(() -> revision(start, id, "Edited"));
      var generated =
          executor.submit(
              () -> {
                assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return build.execute(brand.getId(), Set.of(workspace.getId())).version();
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      start.countDown();
      assertThat(List.of(edit.get(15, TimeUnit.SECONDS), generated.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(2, 3);
    }
  }

  private StrategyContent content(String name) {
    return new StrategyContent(
        List.of(new StrategyContent.Pillar(name, 100, "Useful content")),
        4,
        "UTC",
        List.of(LocalTime.NOON));
  }

  private UUID approveGenerated() throws Exception {
    var id = generate();
    SecurityContextHolder.getContext().setAuthentication(owner);
    manage.approve(id, Set.of(workspace.getId()), 0, actorId);
    return id;
  }

  @Test
  void databaseRejectsMissingForgedAndMutableApprovalAudit() throws Exception {
    var id = generate();
    assertThatThrownBy(() -> jdbc.update("update strategies set status='APPROVED' where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var outsider =
        users.saveAndFlush(
            new CadenceUser("outsider-" + UUID.randomUUID(), UserRole.OWNER, Set.of()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategies set status='APPROVED',approved_by=?,approved_at=now() where id=?",
                    outsider.getId(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    SecurityContextHolder.getContext().setAuthentication(owner);
    manage.approve(id, Set.of(workspace.getId()), 0, actorId);
    assertThatThrownBy(() -> jdbc.update("update strategies set approved_at=now() where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategies set approved_by=null,approved_at=null where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsForgedLineageAndDeferredInvalidRevisionContent() throws Exception {
    var id = approveGenerated();
    var branch =
        manage
            .updatePlaybook(
                id,
                Set.of(workspace.getId()),
                1,
                content("Edited"),
                com.cadence.support.StrategyFixtures.editPlaybook())
            .details();
    assertThat(branch.revisedFromId()).isEqualTo(id);
    assertThatThrownBy(
            () -> jdbc.update("update strategies set revised_from_id=null where id=?", branch.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategy_content_pillars set pillar_weight=99 where strategy_id=?",
                    branch.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "delete from strategy_best_publishing_times where strategy_id=?", branch.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at,revised_from_id) values(?,?,99,'DRAFT',3,'UTC',now(),now(),?)",
                    UUID.randomUUID(),
                    brand.getId(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select pillar_weight from strategy_content_pillars where strategy_id=?",
                Integer.class,
                branch.id()))
        .isEqualTo(100);
  }

  @Test
  void concurrentEditAndApprovalAcceptExactlyOneExpectedRevision() throws Exception {
    var id = generate();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var a =
          executor.submit(
              () ->
                  attempt(
                      start,
                      () ->
                          manage.updatePlaybook(
                              id,
                              Set.of(workspace.getId()),
                              0,
                              content("Updated"),
                              com.cadence.support.StrategyFixtures.editPlaybook())));
      var b =
          executor.submit(
              () ->
                  attempt(start, () -> manage.approve(id, Set.of(workspace.getId()), 0, actorId)));
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(strategies.findById(id).orElseThrow().getLockVersion()).isEqualTo(1);
  }

  @Test
  void concurrentApprovedEditsAllocateDistinctVersionsWithoutChangingSource() throws Exception {
    var id = approveGenerated();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var a = executor.submit(() -> revision(start, id, "First"));
      var b = executor.submit(() -> revision(start, id, "Second"));
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(2, 3);
    }
    assertThat(strategies.findById(id).orElseThrow().getLockVersion()).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from strategies where revised_from_id=?", Integer.class, id))
        .isEqualTo(2);
  }

  @Test
  void archivedParentRejectsUpdateAndApprovalAndLockedSourceBranches() throws Exception {
    var id = approveGenerated();
    jdbc.update("update strategies set status='LOCKED',lock_version=lock_version+1 where id=?", id);
    var branch =
        manage
            .updatePlaybook(
                id,
                Set.of(workspace.getId()),
                2,
                content("Locked revision"),
                com.cadence.support.StrategyFixtures.editPlaybook())
            .details();
    jdbc.update("update workspaces set status='ARCHIVED' where id=?", workspace.getId());
    assertThatThrownBy(
            () ->
                manage.updatePlaybook(
                    branch.id(),
                    Set.of(workspace.getId()),
                    0,
                    content("Denied"),
                    com.cadence.support.StrategyFixtures.editPlaybook()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> manage.approve(branch.id(), Set.of(workspace.getId()), 0, actorId))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at,revised_from_id) values(?,?,3,'DRAFT',3,'UTC',now(),now(),?)",
                    UUID.randomUUID(),
                    brand.getId(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private boolean attempt(CountDownLatch start, Runnable action) throws Exception {
    start.await(10, TimeUnit.SECONDS);
    SecurityContextHolder.getContext().setAuthentication(owner);
    try {
      action.run();
      return true;
    } catch (IllegalStateException conflict) {
      return false;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private int revision(CountDownLatch start, UUID id, String name) throws Exception {
    start.await(10, TimeUnit.SECONDS);
    SecurityContextHolder.getContext().setAuthentication(owner);
    try {
      return manage
          .updatePlaybook(
              id,
              Set.of(workspace.getId()),
              1,
              content(name),
              com.cadence.support.StrategyFixtures.editPlaybook())
          .details()
          .version();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void fullPlaybookDatabaseReferencesHistoryAndRuntimePrivileges() throws Exception {
    var id = generate();
    for (String sql :
        List.of(
            "update strategy_playbook_slots set pillar_id='foreign' where strategy_id=?",
            "update strategy_playbook_slots set audience_id='foreign' where strategy_id=?",
            "update strategy_playbook_slots set channel='TIKTOK' where strategy_id=?",
            "update strategy_playbook_slots set week_number=1 where strategy_id=?",
            "update strategy_playbook_channels set share_percent=99 where strategy_id=?",
            "delete from strategy_playbook_goals where strategy_id=?",
            "update strategies set content_format_version=1,whitespace_status=null where id=?",
            "update strategies set generation_output_checksum=repeat('b',64) where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    SecurityContextHolder.getContext().setAuthentication(owner);
    manage.approve(id, Set.of(workspace.getId()), 0, actorId);
    for (String table :
        List.of(
            "strategy_playbook_pillar_ids",
            "strategy_playbook_audience",
            "strategy_playbook_channels",
            "strategy_playbook_goals",
            "strategy_playbook_tones",
            "strategy_playbook_slots"))
      assertThatThrownBy(() -> jdbc.update("delete from " + table + " where strategy_id=?", id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    try (var c = datasource.getConnection();
        var st = c.createStatement()) {
      st.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
      st.execute("grant usage on schema public to cadence_runtime");
      st.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      st.execute("set role cadence_runtime");
      try (var q =
          c.prepareStatement(
              "update strategies set generation_output_checksum=repeat('b',64) where id=?")) {
        q.setObject(1, id);
        assertThatThrownBy(q::executeUpdate).isInstanceOf(java.sql.SQLException.class);
      }
      try (var q = c.prepareStatement("delete from strategy_playbook_slots where strategy_id=?")) {
        q.setObject(1, id);
        assertThatThrownBy(q::executeUpdate).isInstanceOf(java.sql.SQLException.class);
      }
      st.execute("reset role");
    }
  }

  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
