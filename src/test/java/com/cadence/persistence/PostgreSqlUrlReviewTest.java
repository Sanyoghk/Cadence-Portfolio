package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.UrlReviewApiContract;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlUrlReviewTest extends UrlReviewApiContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  private String cloneSql() {
    return "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at,url_snapshot_id,original_scan_id,latest_scan_id,scan_expires_at) select ?,a.evidence_id,a.source_version,b.lock_version,w.lock_version,a.source_checksum,a.content_checksum,a.reviewed_text,a.reviewed_by,clock_timestamp(),a.policy_version,clock_timestamp(),clock_timestamp(),a.url_snapshot_id,a.original_scan_id,a.latest_scan_id,a.scan_expires_at from evidence_admissions a join brand_evidence e on e.id=a.evidence_id join brands b on b.id=e.brand_id join workspaces w on w.id=b.workspace_id where a.id=?";
  }

  @Test
  void sqlGuardsRejectForgedReceiptAndHistoryRewrite() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_evidence set fetched_reference=? where id=?",
                    "quarantine:quarantine/writes/"
                        + ws.getId()
                        + "/"
                        + java.util.UUID.randomUUID()
                        + ".html",
                    source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    for (var pair :
        java.util.List.of(
            new String[] {"a.reviewed_text", "'forged'"},
            new String[] {"a.source_checksum", "repeat('0',64)"},
            new String[] {"a.url_snapshot_id", "null"},
            new String[] {"a.original_scan_id", "null"},
            new String[] {"a.scan_expires_at", "clock_timestamp()+interval '8 days'"},
            new String[] {"a.policy_version", "'text-review-v1'"}))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      cloneSql().replace(pair[0], pair[1]), java.util.UUID.randomUUID(), a.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update url_text_snapshots set warnings='changed' where evidence_id=?",
                    source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("delete from url_text_snapshots where evidence_id=?", source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update quarantine_objects set state='TOMBSTONED' where evidence_id=?",
                    source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    appendScan(com.cadence.domain.ScanVerdict.ERROR);
    assertThatThrownBy(() -> jdbc.update(cloneSql(), java.util.UUID.randomUUID(), a.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void sqlAndApplicationHistoryCap1000() throws Exception {
    var a = admit();
    try (var c =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var bump = c.prepareStatement("update brands set lock_version=lock_version+1 where id=?");
        var insert = c.prepareStatement(cloneSql())) {
      c.setAutoCommit(false);
      for (int i = 1; i < 1000; i++) {
        bump.setObject(1, brand.getId());
        bump.executeUpdate();
        insert.setObject(1, java.util.UUID.randomUUID());
        insert.setObject(2, a.id());
        insert.executeUpdate();
      }
      c.commit();
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1000);
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(() -> jdbc.update(cloneSql(), java.util.UUID.randomUUID(), a.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var t = token();
    org.mockito.Mockito.clearInvocations(storage);
    assertThatThrownBy(() -> reviews.admit(source.getId(), scope(), actor.getId(), t))
        .isInstanceOf(IllegalStateException.class);
    org.mockito.Mockito.verifyNoInteractions(storage);
  }

  @Test
  void restrictedRuntimeCanAdmitWithdrawButCannotRewrite() throws Exception {
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
      try (var s = c.prepareStatement(cloneSql())) {
        s.setObject(1, id);
        s.setObject(2, a.id());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, id);
        s.setObject(3, actor.getId());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      for (String table :
          java.util.List.of(
              "url_text_snapshots", "evidence_admissions", "evidence_admission_withdrawals"))
        try (var s = c.createStatement()) {
          assertThatThrownBy(
                  () -> s.executeUpdate("update " + table + " set lock_version=lock_version"))
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  ex -> assertThat(((java.sql.SQLException) ex).getSQLState()).isEqualTo("42501"));
        }
    }
    assertThatThrownBy(() -> reviews.snapshot(source.getId(), id, scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void committedWithdrawalWinsAgainstConcurrentRereview() throws Exception {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var t = token();
    try (var c =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
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
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return reviews.admit(source.getId(), scope(), actor.getId(), t);
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitLock();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
  }

  @Test
  void rereviewCommittedBeforeWithdrawalIsStillRevoked() throws Exception {
    var a = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var id = java.util.UUID.randomUUID();
    try (var c =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s = c.prepareStatement(cloneSql())) {
        s.setObject(1, id);
        s.setObject(2, a.id());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return common.withdraw(source.getId(), a.id(), scope(), actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitLock();
      } finally {
        c.commit();
      }
      assertThat(future.get(15, java.util.concurrent.TimeUnit.SECONDS)).isNotNull();
    }
    assertThatThrownBy(() -> reviews.snapshot(source.getId(), id, scope(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  private void awaitLock() throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
              Integer.class)
          > 0) return;
      Thread.sleep(20);
    }
    fail("Expected actual PostgreSQL account lock wait");
  }
}
