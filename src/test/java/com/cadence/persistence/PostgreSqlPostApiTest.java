package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.PostApiContract;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlPostApiTest extends PostApiContract {
  @Test
  void committedApprovalHasExactlyOneImmutableContentFreeAuditEvent() throws Exception {
    var p = create();
    posts.approve(p.id(), p.revisionId(), scope(), 0);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_audit_events where resource_id=? and action='APPROVE' and actor_id=? and workspace_id=? and result='COMMITTED'",
                Integer.class,
                p.revisionId(),
                actorId,
                workspace.getId()))
        .isEqualTo(1);
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), 0))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from security_audit_events where resource_id=?",
                Integer.class,
                p.revisionId()))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select to_jsonb(a)::text from security_audit_events a where resource_id=?",
                String.class,
                p.revisionId()))
        .doesNotContain("Caption", "Original", "post-fixture", "Media concept");
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin;end if;end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute(
          "grant select,insert,update,delete on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      s.execute("set role cadence_runtime");
      for (var sql :
          java.util.List.of(
              "select * from security_audit_events",
              "insert into security_audit_events default values",
              "update security_audit_events set action='FORGED'",
              "delete from security_audit_events",
              "truncate security_audit_events",
              "select security_capture_audit()"))
        assertThatThrownBy(() -> s.execute(sql)).isInstanceOf(java.sql.SQLException.class);
      s.execute("reset role");
      for (var sql :
          java.util.List.of(
              "update security_audit_events set action='FORGED'",
              "delete from security_audit_events",
              "truncate security_audit_events"))
        assertThatThrownBy(() -> s.execute(sql)).isInstanceOf(java.sql.SQLException.class);
    }
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void databaseHistoryAndPlatformBypassesDenied() {
    var p = create();
    assertThatThrownBy(
            () -> jdbc.update("update content_posts set platform='TIKTOK' where id=?", p.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revisions set format='TEXT',lock_version=lock_version+1 where id=?",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    posts.approve(p.id(), p.revisionId(), scope(), 0);
    for (String sql :
        java.util.List.of(
            "update post_revisions set title='Tampered' where id=?",
            "delete from post_revisions where id=?",
            "update post_revisions set status='DRAFT',approved_by=null,approved_at=null where id=?",
            "delete from post_revision_hashtags where revision_id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, p.revisionId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void tagOnlyEditRequiresActualParentVersionAndAudit() {
    var p = create();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revision_hashtags set hashtag='#untracked' where revision_id=? and hashtag_order=0",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(revisions.findById(p.revisionId()).orElseThrow().getLockVersion()).isZero();
    var c = content("Original");
    var edited =
        posts
            .edit(
                p.id(),
                p.revisionId(),
                scope(),
                0,
                new com.cadence.domain.PostContent(
                    c.title(),
                    c.caption(),
                    java.util.List.of("#changed"),
                    c.format(),
                    c.mediaPrompt(),
                    c.videoScript()))
            .details();
    assertThat(edited.lockVersion()).isEqualTo(1);
    assertThat(edited.hashtags()).containsExactly("#changed");
    assertThat(edited.editedBy()).isEqualTo(actorId);
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), 0))
        .isInstanceOf(IllegalStateException.class);
    assertThat(posts.approve(p.id(), p.revisionId(), scope(), 1).hashtags())
        .containsExactly("#changed");
  }

  @Test
  void actorAndAuditBypassesDenied() {
    var p = create();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revisions set status='APPROVED',lock_version=lock_version+1 where id=?",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var outsider =
        users.saveAndFlush(
            new com.cadence.domain.CadenceUser(
                "outsider-" + java.util.UUID.randomUUID(),
                com.cadence.domain.UserRole.OWNER,
                java.util.Set.of()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revisions set status='APPROVED',approved_by=?,approved_at=now(),lock_version=lock_version+1 where id=?",
                    outsider.getId(),
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revisions set revised_from_id=?,lock_version=lock_version+1 where id=?",
                    p.revisionId(),
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revision_hashtags set hashtag='invalid' where revision_id=?",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void revokedMembershipDeniedDespiteStalePrincipal() {
    var p = create();
    jdbc.update("delete from workspace_memberships where user_id=?", actorId);
    assertThatThrownBy(() -> posts.read(p.id(), scope()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), 0))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void staleOwnerClaimsCannotBypassDisabledAccountOrReservedActualRole() {
    var p = create();
    jdbc.update("update cadence_users set active=false where id=?", actorId);
    assertThatThrownBy(() -> posts.edit(p.id(), p.revisionId(), scope(), 0, content("Denied")))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    jdbc.update("update cadence_users set active=true,role='EDITOR' where id=?", actorId);
    assertThatThrownBy(() -> posts.approve(p.id(), p.revisionId(), scope(), 0))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void historyCapAndInvalidHashtagOrderAreIndependentSqlGuards() {
    var p = create();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update post_revisions set revision_number=1001 where id=?", p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into post_revision_hashtags(revision_id,hashtag_order,hashtag) values (?,3,'#third')",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into post_revision_hashtags(revision_id,hashtag_order,hashtag) values (?,2,'#cadence')",
                    p.revisionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void runtimeCanEditApproveButCannotEraseIdentityOrHistory() throws Exception {
    var p = create();
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
      s.execute("set role cadence_runtime");
      try (var q =
          c.prepareStatement(
              "update post_revisions set title='Runtime edit',edited_by=?,edited_at=now(),lock_version=lock_version+1 where id=?")) {
        q.setObject(1, actorId);
        q.setObject(2, p.revisionId());
        assertThat(q.executeUpdate()).isEqualTo(1);
      }
      try (var q =
          c.prepareStatement(
              "update post_revisions set status='APPROVED',approved_by=?,approved_at=now(),lock_version=lock_version+1 where id=?")) {
        q.setObject(1, actorId);
        q.setObject(2, p.revisionId());
        assertThat(q.executeUpdate()).isEqualTo(1);
      }
      for (String sql :
          java.util.List.of(
              "delete from content_posts where id=?",
              "update content_posts set source_checksum=repeat('a',64) where id=?",
              "update content_posts set lock_version=lock_version+1 where id=?")) {
        try (var q = c.prepareStatement(sql)) {
          q.setObject(1, p.id());
          assertThatThrownBy(q::executeUpdate).isInstanceOf(java.sql.SQLException.class);
        }
      }
      try (var q = c.prepareStatement("update post_revisions set title='Tampered' where id=?")) {
        q.setObject(1, p.revisionId());
        assertThatThrownBy(q::executeUpdate).isInstanceOf(java.sql.SQLException.class);
      }
      s.execute("reset role");
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void concurrentEditsApprovalAndBranchCannotPublishTwoResults(boolean branch) throws Exception {
    var initial = create();
    var p = branch ? posts.approve(initial.id(), initial.revisionId(), scope(), 0) : initial;
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      var first = pool.submit(() -> race(start, p, true));
      var second = pool.submit(() -> race(start, p, branch));
      start.countDown();
      assertThat(
              java.util.List.of(
                  first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(20, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            revisions.findAllByPostId(
                p.id(), org.springframework.data.domain.PageRequest.of(0, 10)))
        .hasSize(branch ? 2 : 1);
  }

  private boolean race(
      java.util.concurrent.CountDownLatch start,
      com.cadence.application.results.PostDetails p,
      boolean edit)
      throws Exception {
    start.await(10, java.util.concurrent.TimeUnit.SECONDS);
    org.springframework.security.core.context.SecurityContextHolder.getContext()
        .setAuthentication(owner);
    try {
      if (edit) posts.edit(p.id(), p.revisionId(), scope(), p.lockVersion(), content("Concurrent"));
      else posts.approve(p.id(), p.revisionId(), scope(), p.lockVersion());
      return true;
    } catch (IllegalStateException expected) {
      return false;
    } finally {
      org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void archiveWinsWhileMutationWaitsOnParent(boolean approval) throws Exception {
    var p = create();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var q =
          c.prepareStatement(
              "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?")) {
        q.setObject(1, workspace.getId());
        q.executeUpdate();
      }
      var result =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  assertThatThrownBy(
                          () -> {
                            if (approval) posts.approve(p.id(), p.revisionId(), scope(), 0);
                            else posts.edit(p.id(), p.revisionId(), scope(), 0, content("Denied"));
                          })
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("archived");
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
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
        assertThat(waiting).isTrue();
        try (var q =
            c.prepareStatement("select id from post_revisions where id=? for update nowait")) {
          q.setObject(1, p.revisionId());
          assertThat(q.executeQuery().next()).isTrue();
        }
      } finally {
        c.commit();
      }
      result.get(15, java.util.concurrent.TimeUnit.SECONDS);
    }
    assertThat(revisions.findById(p.revisionId()).orElseThrow().getLockVersion()).isZero();
  }
}
