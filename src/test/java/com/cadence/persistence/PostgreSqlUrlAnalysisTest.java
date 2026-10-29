package com.cadence.persistence;

import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlUrlAnalysisTest extends com.cadence.support.UrlAnalysisApiContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
    p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @org.springframework.beans.factory.annotation.Autowired
  com.cadence.application.services.BrandAnalysisTransactions analysisTx;

  @org.junit.jupiter.api.Test
  void sqlRejectsWrongFieldAndRuntimeCannotRewriteHistory() throws Exception {
    var a = admit();
    var snap = analysisTx.startEvidence(brand.getId(), scope(), actor.getId());
    String sql =
        "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,field_path,created_at,updated_at) values(?,?,?,0,?,?,clock_timestamp(),clock_timestamp())";
    for (String path : java.util.Arrays.asList(null, "description", "format"))
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  jdbc.update(
                      sql,
                      java.util.UUID.randomUUID(),
                      snap.command().analysisId(),
                      a.id(),
                      "Māori",
                      path))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var st = c.createStatement()) {
        st.execute(
            "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin; end if; end $$");
        st.execute("grant usage on schema public to cadence_runtime");
        st.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      }
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      try (var st = c.createStatement()) {
        st.execute("set role cadence_runtime");
      }
      try (var st = c.prepareStatement(sql)) {
        st.setObject(1, java.util.UUID.randomUUID());
        st.setObject(2, snap.command().analysisId());
        st.setObject(3, a.id());
        st.setString(4, "Māori");
        st.setString(5, "title");
        org.assertj.core.api.Assertions.assertThat(st.executeUpdate()).isEqualTo(1);
      }
      for (String table :
          java.util.List.of(
              "brand_analysis_sources", "brand_analysis_citations", "url_text_snapshots"))
        try (var st = c.createStatement()) {
          org.assertj.core.api.Assertions.assertThatThrownBy(
                  () -> st.executeUpdate("update " + table + " set lock_version=lock_version"))
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  ex ->
                      org.assertj.core.api.Assertions.assertThat(
                              ((java.sql.SQLException) ex).getSQLState())
                          .isEqualTo("42501"));
        }
    }
    analysisTx.fail(snap, scope());
  }

  @org.junit.jupiter.api.Test
  void withdrawalCommitWinsAgainstConcurrentApproval() throws Exception {
    var a = admit();
    configure();
    var o = generate();
    var d = identity.createFromAnalysis(brand.getId(), scope(), o.analysisId(), 1, actor.getId());
    try (var c =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var st =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())")) {
        st.setObject(1, java.util.UUID.randomUUID());
        st.setObject(2, a.id());
        st.setObject(3, actor.getId());
        st.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .setAuthentication(owner);
                try {
                  return lifecycle.approve(d.id(), scope(), d.lockVersion(), actor.getId());
                } finally {
                  org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitLock();
      } finally {
        c.commit();
      }
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .hasCauseInstanceOf(IllegalStateException.class);
    }
  }

  @org.junit.jupiter.api.Test
  void approvalCommitWinsThenWithdrawalPreventsFutureApproval() throws Exception {
    var a = admit();
    configure();
    var o = generate();
    var d = identity.createFromAnalysis(brand.getId(), scope(), o.analysisId(), 1, actor.getId());
    try (var c =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var st =
          c.prepareStatement(
              "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?")) {
        st.setObject(1, actor.getId());
        st.setObject(2, d.id());
        org.assertj.core.api.Assertions.assertThat(st.executeUpdate()).isEqualTo(1);
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
      org.assertj.core.api.Assertions.assertThat(
              future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .isNotNull();
    }
    var approved = lifecycle.findById(d.id(), scope());
    org.assertj.core.api.Assertions.assertThat(approved.status())
        .isEqualTo(com.cadence.domain.ApprovalStatus.APPROVED);
    var next =
        lifecycle
            .update(approved.id(), scope(), approved.lockVersion(), approved.content())
            .details();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> lifecycle.approve(next.id(), scope(), next.lockVersion(), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp() where id=?",
                    actor.getId(),
                    next.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
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
    org.assertj.core.api.Assertions.fail("Expected actual PostgreSQL account lock wait");
  }
}
