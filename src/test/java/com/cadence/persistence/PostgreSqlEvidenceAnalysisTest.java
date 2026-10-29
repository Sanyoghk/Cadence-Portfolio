package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.EvidenceAnalysisApiContract;
import org.junit.jupiter.api.*;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlEvidenceAnalysisTest extends EvidenceAnalysisApiContract {
  @org.springframework.beans.factory.annotation.Autowired javax.sql.DataSource datasource;

  @Test
  void completionWaitsForAccountRevocationBeforeTakingParentLocks() throws Exception {
    var snapshot = start();
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
                  return transactions.complete(
                      snapshot, java.util.Set.of(workspace.getId()), result(snapshot.command()));
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
    noPublication();
  }

  @Test
  void deferredSealRejectsMissingOrForgedSourceDigest() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_analysis_attempts(id,brand_id,brand_version,workspace_version,source_checksum,started_at,deadline,status,requested_by,input_brand_name,input_industry,created_at,updated_at) values(?,?,0,0,?,clock_timestamp(),clock_timestamp()+interval '60 seconds','RUNNING',?,'Analysis brand','Creator',now(),now())",
                    java.util.UUID.randomUUID(),
                    brand.getId(),
                    "0".repeat(64),
                    actor.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            attempts.findByBrandIdAndStatus(
                brand.getId(), com.cadence.domain.BrandAnalysisAttempt.Status.RUNNING))
        .isEmpty();
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
  void rawSqlCannotRewriteSourcesOrForgeCitation() {
    var snapshot = start();
    var id = snapshot.command().analysisId();
    assertThatThrownBy(
            () -> jdbc.update("delete from brand_analysis_sources where attempt_id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,created_at,updated_at) values(?,?,?,0,'invented',now(),now())",
                    java.util.UUID.randomUUID(),
                    id,
                    admission))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_analysis_attempts set requested_by=null,input_brand_name=null,input_industry=null,status='FAILED',completed_at=clock_timestamp() where id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void runtimeCanRecordExactQuoteButCannotRewriteHistory() throws Exception {
    var snapshot = start();
    var id = snapshot.command().analysisId();
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
          c.prepareStatement(
              "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,created_at,updated_at) values(?,?,?,0,'Reviewed',clock_timestamp(),clock_timestamp())")) {
        s.setObject(1, java.util.UUID.randomUUID());
        s.setObject(2, id);
        s.setObject(3, admission);
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      for (String table : java.util.List.of("brand_analysis_sources", "brand_analysis_citations"))
        for (String sql :
            java.util.List.of(
                "update " + table + " set position=position where attempt_id=?",
                "delete from " + table + " where attempt_id=?")) {
          try (var s = c.prepareStatement(sql)) {
            s.setObject(1, id);
            assertThatThrownBy(s::executeUpdate)
                .isInstanceOf(java.sql.SQLException.class)
                .extracting(e -> ((java.sql.SQLException) e).getSQLState())
                .isEqualTo("42501");
          }
        }
    }
  }

  @Test
  void rawSqlCannotAttachEvidenceReceiptToLegacyIdentity() {
    var outcome =
        analyze.execute(brand.getId(), java.util.Set.of(workspace.getId()), actor.getId());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_identities(id,brand_id,version,status,analysis_attempt_id,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,1,'DRAFT',?,now(),now(),'#000000','#111111','#222222','#333333','#FFFFFF','Demo')",
                    java.util.UUID.randomUUID(),
                    brand.getId(),
                    outcome.analysisId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("Evidence identity source integration unavailable");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Long.class,
                brand.getId()))
        .isZero();
  }

  @Test
  void rawSqlPublicationCannotBypassWithdrawal() {
    var snapshot = start();
    reviews.withdraw(source.getId(), admission, java.util.Set.of(workspace.getId()), actor.getId());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,created_at,updated_at) values(?,?,?,0,'Reviewed',now(),now())",
                    java.util.UUID.randomUUID(),
                    snapshot.command().analysisId(),
                    admission))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    noPublication();
  }
}
