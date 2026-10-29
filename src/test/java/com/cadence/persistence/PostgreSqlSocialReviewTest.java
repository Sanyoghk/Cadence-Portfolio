package com.cadence.persistence;

import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlSocialReviewTest extends com.cadence.support.SocialReviewApiContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @org.springframework.beans.factory.annotation.Autowired
  com.cadence.application.services.BrandAnalysisTransactions analysisTx;

  @org.junit.jupiter.api.Test
  void sqlRejectsWrongFieldAndRuntimeCannotRewriteHistory() throws Exception {
    var a = admit();
    var snap = analysisTx.startEvidence(brand.getId(), scope(), actor.getId());
    String sql =
        "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,field_path,created_at,updated_at) values(?,?,?,0,?,?,clock_timestamp(),clock_timestamp())";
    for (String path : java.util.Arrays.asList(null, "accountName", "format"))
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  jdbc.update(
                      sql,
                      java.util.UUID.randomUUID(),
                      snap.command().analysisId(),
                      a.review().id(),
                      "Ignore",
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
        st.setObject(3, a.review().id());
        st.setString(4, "Ignore");
        st.setString(5, "posts[0].text");
        org.assertj.core.api.Assertions.assertThat(st.executeUpdate()).isEqualTo(1);
      }
      for (String table :
          java.util.List.of(
              "brand_analysis_sources", "brand_analysis_citations", "social_text_snapshots"))
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
        st.setObject(2, a.review().id());
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
                  return common.withdraw(source.getId(), a.review().id(), scope(), actor.getId());
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

  @org.junit.jupiter.api.Test
  void sqlCannotAdmitSocialAsGenericTextOrRewriteSnapshot() {
    var a = admit();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) select ?,id,lock_version,0,0,checksum,encode(sha256(convert_to(source_reference,'UTF8')),'hex'),source_reference,?,clock_timestamp(),'text-review-v1',clock_timestamp(),clock_timestamp() from brand_evidence where id=?",
                    java.util.UUID.randomUUID(),
                    actor.getId(),
                    source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update social_text_snapshots set payload='forged' where id=?", a.snapshotId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update("delete from social_text_snapshots where id=?", a.snapshotId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @org.junit.jupiter.api.Test
  void sqlRejectsAlteredProjectionReceiptExpiryAndPostMapping() {
    var t = token();
    for (String column :
        java.util.List.of(
            "payload",
            "post_ids",
            "source_checksum",
            "captured_at",
            "provider_call_id",
            "evidence_id")) {
      String replacement =
          switch (column) {
            case "payload" -> "'{}'";
            case "post_ids" -> "'[]'";
            case "source_checksum" -> "repeat('a',64)";
            case "captured_at" -> "captured_at-interval '1 second'";
            default -> "gen_random_uuid()";
          };
      String fields =
          "id,evidence_id,source_version,provider_call_id,source_checksum,captured_at,source_captured_at,payload,content_checksum,post_ids,projection_version,created_at,updated_at,lock_version";
      var selected = new java.util.ArrayList<>(java.util.Arrays.asList(fields.split(",")));
      selected.set(0, "gen_random_uuid()");
      selected.set(selected.indexOf(column), replacement);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into social_text_snapshots("
                          + fields
                          + ") select "
                          + String.join(",", selected)
                          + " from social_text_snapshots where id=?",
                      t.snapshotId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    var a = admit();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,social_snapshot_id,binding_expires_at,created_at,updated_at) select ?,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,clock_timestamp(),policy_version,social_snapshot_id,clock_timestamp()+interval '30 seconds',clock_timestamp(),clock_timestamp() from evidence_admissions where id=?",
                    java.util.UUID.randomUUID(),
                    a.review().id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @org.junit.jupiter.api.Test
  void sqlHistoryLimitCannotBeBypassedByBindingExpiryRereviews() {
    var a = admit();
    jdbc.update(
        "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,social_snapshot_id,binding_expires_at,created_at,updated_at) select gen_random_uuid(),a.evidence_id,a.source_version,a.brand_version,a.workspace_version,a.source_checksum,a.content_checksum,a.reviewed_text,a.reviewed_by,clock_timestamp(),a.policy_version,a.social_snapshot_id,a.binding_expires_at+n*interval '1 second',clock_timestamp(),clock_timestamp() from evidence_admissions a cross join generate_series(1,999) n where a.id=?",
        a.review().id());
    org.assertj.core.api.Assertions.assertThat(admissions.countForEvidence(source.getId()))
        .isEqualTo(1000);
    expiry = expiry.plusSeconds(2000);
    org.mockito.Mockito.doReturn(expiry)
        .when(provider)
        .requireApprovedAccount(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    org.assertj.core.api.Assertions.assertThatThrownBy(this::admit)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("history limit");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,social_snapshot_id,binding_expires_at,created_at,updated_at) select gen_random_uuid(),evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,clock_timestamp(),policy_version,social_snapshot_id,binding_expires_at+interval '1 day',clock_timestamp(),clock_timestamp() from evidence_admissions where id=?",
                    a.review().id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @org.junit.jupiter.api.Test
  void sqlAndJavaCanonicalProjectionAgreeOnEscapedUnicodeAndRejectControls() {
    String raw =
        raw(com.cadence.domain.SocialEvidenceProvider.INSTAGRAM)
            .replace("Creator biography", "Quote \\\" backslash \\\\ line\\n😀");
    var e =
        new com.cadence.domain.BrandEvidence(
            brand,
            com.cadence.domain.EvidenceType.TEXT,
            raw,
            com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + raw),
            clock.instant());
    e = evidence.saveAndFlush(e);
    var origin =
        new com.cadence.domain.EvidenceSocialOrigin(
            e.getId(),
            java.util.UUID.randomUUID(),
            com.cadence.domain.SocialEvidenceProvider.INSTAGRAM,
            ACCOUNT,
            "v25.0",
            clock.instant(),
            e.getChecksum());
    String expected = com.cadence.domain.SocialTextProjection.project(e, origin).payload();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "select social_text_projection(?,'INSTAGRAM',?,'v25.0',clock_timestamp())",
                String.class,
                raw,
                ACCOUNT))
        .isEqualTo(expected);
    String invalid = raw.replace("Māori", "\\u0001");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.queryForObject(
                    "select social_text_projection(?,'INSTAGRAM',?,'v25.0',clock_timestamp())",
                    String.class,
                    invalid,
                    ACCOUNT))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
