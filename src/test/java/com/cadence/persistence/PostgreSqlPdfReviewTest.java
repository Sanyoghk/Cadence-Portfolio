package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.PdfReviewApiContract;
import org.junit.jupiter.api.*;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlPdfReviewTest extends PdfReviewApiContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource datasource;

  @Test
  void sqlAnalysisGuardRejectsLatestErrorEvenIfApplicationCheckIsBypassed() throws Exception {
    var a = admit();
    var captured = analysisTx.startEvidence(brand.getId(), scope(), actor.getId());
    try (var c = datasource.getConnection();
        var s = c.prepareStatement("select validate_analysis_admission(?,?,false)")) {
      s.setObject(1, captured.command().analysisId());
      s.setObject(2, a.id());
      assertThat(s.execute()).isTrue();
      appendScan(com.cadence.domain.ScanVerdict.ERROR);
      assertThatThrownBy(s::execute)
          .isInstanceOf(java.sql.SQLException.class)
          .satisfies(
              ex -> assertThat(((java.sql.SQLException) ex).getSQLState()).isEqualTo("23514"));
    }
    noAnalysisPublication();
    org.mockito.Mockito.verifyNoInteractions(analyzer);
  }

  @Test
  void runtimePdfIdentityApprovalAndInheritedSqlGuard() throws Exception {
    var admission = admit();
    var draft = copyPdf();
    try (var c = datasource.getConnection()) {
      try (var s = c.createStatement()) {
        s.execute(
            "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
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
          c.prepareStatement(
              "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?")) {
        s.setObject(1, actor.getId());
        s.setObject(2, draft.id());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
    }
    var approved = identityLifecycle.findById(draft.id(), scope());
    var branch =
        identityLifecycle
            .update(approved.id(), scope(), approved.lockVersion(), approved.content())
            .details();
    appendScan(com.cadence.domain.ScanVerdict.ERROR);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?",
                    actor.getId(),
                    branch.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.queryForObject(
                    "select validate_identity_evidence_sources(?,?)",
                    Object.class,
                    evidenceIdentity.read(draft.id(), scope(), actor.getId()).analysisId(),
                    brand.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void committedPdfWithdrawalWinsOverConcurrentApproval() throws Exception {
    var admission = admit();
    var draft = copyPdf();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, admission.id());
        s.setObject(3, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return identityLifecycle.approve(
                      draft.id(), scope(), draft.lockVersion(), actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
                  Integer.class)
              > 0) {
            waiting = true;
            break;
          }
          Thread.sleep(20);
        }
        assertThat(waiting).isTrue();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(identityLifecycle.findById(draft.id(), scope()).status())
        .isEqualTo(com.cadence.domain.ApprovalStatus.DRAFT);
  }

  private String cloneReceiptSql() {
    return "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at,artifact_id,original_scan_id,latest_scan_id,renderer_version,scan_expires_at) select ?,a.evidence_id,a.source_version,b.lock_version,a.workspace_version,a.source_checksum,a.content_checksum,a.reviewed_text,a.reviewed_by,clock_timestamp(),a.policy_version,clock_timestamp(),clock_timestamp(),a.artifact_id,a.original_scan_id,a.latest_scan_id,a.renderer_version,a.scan_expires_at from evidence_admissions a join brand_evidence e on e.id=a.evidence_id join brands b on b.id=e.brand_id where a.id=?";
  }

  @Test
  void sqlAndApplicationEnforcePdfHistoryCap1000() throws Exception {
    var a = admit();
    try (var c = datasource.getConnection();
        var rename =
            c.prepareStatement("update brands set lock_version=lock_version+1 where id=?");
        var insert = c.prepareStatement(cloneReceiptSql())) {
      c.setAutoCommit(false);
      for (int i = 1; i < 1000; i++) {
        rename.setObject(1, brand.getId());
        rename.executeUpdate();
        insert.setObject(1, java.util.UUID.randomUUID());
        insert.setObject(2, a.id());
        insert.executeUpdate();
      }
      c.commit();
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1000);
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(() -> jdbc.update(cloneReceiptSql(), java.util.UUID.randomUUID(), a.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var t = token();
    assertThatThrownBy(() -> preview.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    org.mockito.Mockito.verifyNoInteractions(storage);
  }

  @Test
  void sqlRejectsForgedPdfContentScansPolicyAndExpiry() {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    for (var replacement :
        java.util.List.of(
            new String[] {"a.reviewed_text", "'Forged'"},
            new String[] {"a.content_checksum", "repeat('0',64)"},
            new String[] {"a.original_scan_id", "null"},
            new String[] {"a.latest_scan_id", "null"},
            new String[] {"a.renderer_version", "'other-renderer'"},
            new String[] {"a.policy_version", "'text-review-v1'"},
            new String[] {"a.scan_expires_at", "clock_timestamp()-interval '1 second'"},
            new String[] {"a.scan_expires_at", "clock_timestamp()+interval '8 days'"})) {
      String sql = cloneReceiptSql().replace(replacement[0], replacement[1]);
      assertThatThrownBy(() -> jdbc.update(sql, java.util.UUID.randomUUID(), a.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }

  @Test
  void runtimeCanInsertPdfReceiptAndWithdrawButCannotRewrite() throws Exception {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var id = java.util.UUID.randomUUID();
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var s = c.createStatement()) {
        s.execute(
            "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
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
      try (var s = c.prepareStatement(cloneReceiptSql())) {
        s.setObject(1, id);
        s.setObject(2, a.id());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      for (var sql :
          java.util.List.of(
              "update evidence_admissions set artifact_id=artifact_id where id=?",
              "delete from evidence_admissions where id=?"))
        try (var s = c.prepareStatement(sql)) {
          s.setObject(1, id);
          assertThatThrownBy(s::executeUpdate)
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  ex -> assertThat(((java.sql.SQLException) ex).getSQLState()).isEqualTo("42501"));
        }
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, id);
        s.setObject(3, actor.getId());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
    }
  }

  @Test
  void pendingWithdrawalBlocksAdmissionUntilCommittedThenRejects() throws Exception {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var t = token();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, a.id());
        s.setObject(3, actor.getId());
        s.executeUpdate();
      }
      var f =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return preview.admit(source.getId(), scope(), actor.getId(), t);
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        assertAccountWaiting();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> f.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
  }

  @Test
  void pendingAdmissionCommitsBeforeWaitingWithdrawal() throws Exception {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var id = java.util.UUID.randomUUID();
    try (var c = datasource.getConnection();
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s = c.prepareStatement(cloneReceiptSql())) {
        s.setObject(1, id);
        s.setObject(2, a.id());
        s.executeUpdate();
      }
      var f =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return textReviews.withdraw(source.getId(), id, scope(), actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        assertAccountWaiting();
      } finally {
        c.commit();
      }
      assertThat(f.get(15, java.util.concurrent.TimeUnit.SECONDS).admissionId()).isEqualTo(id);
    }
    assertThatThrownBy(() -> preview.snapshot(source.getId(), id, scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  private void assertAccountWaiting() throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
              Integer.class)
          > 0) return;
      Thread.sleep(20);
    }
    fail("Expected Account lock wait");
  }

  @Test
  void sqlRejectsPdfSelectorForgeryAndHistoryRewrite() {
    var a = admit();
    for (var sql :
        java.util.List.of(
            "update evidence_admissions set artifact_id=artifact_id where id=?",
            "delete from evidence_admissions where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, a.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    for (var expression :
        java.util.List.of("null", "'00000000-0000-0000-0000-000000000001'::uuid")) {
      String sql =
          "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at,artifact_id,original_scan_id,latest_scan_id,renderer_version,scan_expires_at) select ?,evidence_id,source_version,brand_version+1,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,clock_timestamp(),policy_version,clock_timestamp(),clock_timestamp(),"
              + expression
              + ",original_scan_id,latest_scan_id,renderer_version,scan_expires_at from evidence_admissions where id=?";
      assertThatThrownBy(() -> jdbc.update(sql, java.util.UUID.randomUUID(), a.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }

  @Test
  void concurrentPdfAdmissionCommitsExactlyOneReceipt() throws Exception {
    var t = token();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Boolean> call =
          () -> {
            org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(owner);
            try {
              start.await();
              preview.admit(source.getId(), scope(), actor.getId(), t);
              return true;
            } catch (IllegalStateException conflict) {
              return false;
            } finally {
              org.springframework.security.core.context.SecurityContextHolder.clearContext();
            }
          };
      var first = pool.submit(call);
      var second = pool.submit(call);
      start.countDown();
      assertThat(
              java.util.List.of(
                  first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(15, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
  }

  @Test
  void captureWaitsForActualAccountRevocationBeforeParentLocks() throws Exception {
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
                  return execute();
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        boolean waiting = false;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
                  Integer.class)
              > 0) {
            waiting = true;
            break;
          }
          Thread.sleep(20);
        }
        assertThat(waiting).isTrue();
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
  }
}
