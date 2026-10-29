package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.IdentityEvidenceApiContract;
import org.junit.jupiter.api.*;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlIdentityEvidenceTest extends IdentityEvidenceApiContract {
  @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource datasource;

  private void awaitAccountWait() throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
              Integer.class)
          > 0) return;
      Thread.sleep(20);
    }
    throw new AssertionError("Operation did not wait for Account lock");
  }

  @Test
  void copyWaitsForAccountRevocationBeforeTakingWorkspaceLock() throws Exception {
    generate();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s = c.prepareStatement("update cadence_users set active=false where id=?")) {
        s.setObject(1, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return copies.createFromAnalysis(
                      brand.getId(), scope(), receipt.analysisId(), 1, actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitAccountWait();
        try (var s = c.prepareStatement("select id from workspaces where id=? for update nowait")) {
          s.setObject(1, workspace.getId());
          assertThat(s.executeQuery().next()).isTrue();
        }
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    emptyIdentities();
  }

  @Test
  void committedWithdrawalWinsOverConcurrentApproval() throws Exception {
    var draft = copy();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, admission);
        s.setObject(3, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return identities.approve(draft.id(), scope(), 0, actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitAccountWait();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(identities.findById(draft.id(), scope()).status())
        .isEqualTo(com.cadence.domain.ApprovalStatus.DRAFT);
  }

  @Test
  void committedApprovalRemainsHistoryAfterConcurrentWithdrawal() throws Exception {
    var draft = copy();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?")) {
        s.setObject(1, actor.getId());
        s.setObject(2, draft.id());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  withdraw();
                  return true;
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitAccountWait();
      } finally {
        c.commit();
      }
      assertThat(future.get(15, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    assertThat(identities.findById(draft.id(), scope()).status())
        .isEqualTo(com.cadence.domain.ApprovalStatus.APPROVED);
    assertThat(copies.read(draft.id(), scope(), actor.getId()).citations().getFirst().withdrawn())
        .isTrue();
  }

  @Test
  void runtimeMayApproveValidEvidenceButCannotRewriteCopyActor() throws Exception {
    var draft = copy();
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var s = c.createStatement()) {
        s.execute("create role cadence_runtime nologin");
        s.execute("grant usage on schema public to cadence_runtime");
        s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      }
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      try (var s = c.createStatement()) {
        s.execute("set role cadence_runtime");
      }
      try (var s =
          c.prepareStatement("update brand_identities set analysis_copied_by=null where id=?")) {
        s.setObject(1, draft.id());
        assertThatThrownBy(s::executeUpdate)
            .isInstanceOf(java.sql.SQLException.class)
            .extracting(e -> ((java.sql.SQLException) e).getSQLState())
            .isEqualTo("42501");
      }
      try (var s =
          c.prepareStatement(
              "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?")) {
        s.setObject(1, actor.getId());
        s.setObject(2, draft.id());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
    }
  }

  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Test
  void sqlCannotRewriteCopyActor() {
    var d = copy();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set analysis_copied_by=null where id=?", d.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void sqlApprovalCannotBypassWithdrawalOrInheritedLineage() {
    var d = copy();
    identities.approve(d.id(), scope(), 0, actor.getId());
    var b = identities.update(d.id(), scope(), 1, manual()).details();
    withdraw();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?",
                    actor.getId(),
                    b.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("Evidence identity source changed");
  }

  @Test
  void sqlCopyCannotBypassSourceWithdrawal() {
    generate();
    withdraw();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_identities(id,brand_id,version,status,analysis_attempt_id,analysis_copied_by,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,1,'DRAFT',?,?,now(),now(),'#000000','#111111','#222222','#333333','#FFFFFF','Demo')",
                    java.util.UUID.randomUUID(),
                    brand.getId(),
                    receipt.analysisId(),
                    actor.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("Evidence identity source changed");
    emptyIdentities();
  }
}
