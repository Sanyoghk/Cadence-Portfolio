package com.cadence.persistence;

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles({"test", "postgres-test"})
class PostgreSqlAiGenerationTest extends com.cadence.support.AiGenerationContract {
  @org.junit.jupiter.api.Test
  void completionWitnessCannotBeRewrittenOrDetachedFromTerminalJob() {
    var v = submit();
    service.execute(service.claim(v.operationId()));
    for (String assignment :
        java.util.List.of(
            "dispatched_at=null",
            "finished_at=null,outcome=null,reason=null",
            "reason='INPUT_UNAVAILABLE'",
            "completion_schema=0")) {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update ai_generation_attempts set " + assignment + " where operation_id=?",
                      v.operationId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    var b = brands.saveAndFlush(new com.cadence.domain.Brand("Second", workspace));
    var pending =
        service.submit(
            actor,
            scope,
            b.getId(),
            com.cadence.domain.AiGenerationOperation.Kind.DECLARED,
            0,
            "completion");
    service.claim(pending.operationId());
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update ai_generation_attempts set finished_at=started_at,outcome='FAILED',reason='CAPABILITY_UNAVAILABLE' where operation_id=?",
                    pending.operationId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @org.junit.jupiter.api.Test
  void sqlGuardsImmutableCaptureAndQueueBinding() {
    var v = submit();
    var a = admittedText();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into ai_generation_sources(operation_id,admission_id) values(?,?)",
                    v.operationId(),
                    a.admissionId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update ai_generation_operations set actor_id=? where id=?",
                    java.util.UUID.randomUUID(),
                    v.operationId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update jobs set payload_reference='generation:forged' where id=?", v.jobId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var t = service.claim(v.operationId());
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update("update jobs set lease_owner='forged' where id=?", v.jobId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    service.execute(t);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> jdbc.update("update jobs set attempts=0 where id=?", v.jobId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbc.update(
                    "update ai_generation_operations set result_json='{}' where id=?",
                    v.operationId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @org.junit.jupiter.api.Test
  void publicationFailureRollsBackAllWrites() {
    var v = submit();
    jdbc.execute(
        "create function reject_test_ai_publish() returns trigger language plpgsql as $$ begin if NEW.status='SUCCEEDED' then raise exception 'test publication failure' using errcode='23514';end if;return NEW;end $$");
    jdbc.execute(
        "create trigger test_ai_publish before update on ai_generation_operations for each row execute function reject_test_ai_publish()");
    try {
      service.execute(service.claim(v.operationId()));
      org.assertj.core.api.Assertions.assertThat(progress(v).status()).isEqualTo("SUPERSEDED");
      org.assertj.core.api.Assertions.assertThat(
              brands.findById(brand.getId()).orElseThrow().getLockVersion())
          .isZero();
      org.assertj.core.api.Assertions.assertThat(
              analyses.findByBrandIdAndStatus(
                  brand.getId(), com.cadence.domain.BrandAnalysisAttempt.Status.SUCCEEDED))
          .isEmpty();
    } finally {
      jdbc.execute("drop trigger test_ai_publish on ai_generation_operations");
      jdbc.execute("drop function reject_test_ai_publish()");
    }
  }

  @org.junit.jupiter.api.Test
  void runtimeCannotRewriteCaptureOrEraseAttempts() throws Exception {
    var v = submit();
    service.execute(service.claim(v.operationId()));
    var pending =
        service.submit(
            actor,
            scope,
            brand.getId(),
            com.cadence.domain.AiGenerationOperation.Kind.DECLARED,
            1,
            "runtime-claim");
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var st = c.createStatement()) {
        st.execute(
            "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin;end if;end $$");
        st.execute("grant usage on schema public to cadence_runtime");
        st.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      }
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      try (var st = c.createStatement()) {
        st.execute("set role cadence_runtime");
        for (String sql :
            java.util.List.of(
                "update ai_generation_operations set snapshot=snapshot",
                "delete from ai_generation_attempts",
                "update ai_generation_attempts set worker_id=worker_id",
                "delete from ai_generation_sources"))
          org.assertj.core.api.Assertions.assertThatThrownBy(() -> st.executeUpdate(sql))
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  e ->
                      org.assertj.core.api.Assertions.assertThat(
                              ((java.sql.SQLException) e).getSQLState())
                          .isEqualTo("42501"));
        try (var rs = st.executeQuery("select status from ai_generation_operations")) {
          org.assertj.core.api.Assertions.assertThat(rs.next()).isTrue();
        }
      }
      c.setAutoCommit(false);
      var runtime =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      runtime.update(
          "update ai_generation_operations set status='RUNNING' where id=?", pending.operationId());
      runtime.update(
          "update jobs set status='RUNNING',attempts=1,lease_owner='runtime',lease_expires_at=now()+interval '10 seconds' where id=?",
          pending.jobId());
      runtime.update(
          "insert into ai_generation_attempts(id,created_at,updated_at,operation_id,attempt_number,worker_id,started_at,lease_expires_at) select ?,now(),now(),?,1,'runtime',now(),lease_expires_at from jobs where id=?",
          java.util.UUID.randomUUID(),
          pending.operationId(),
          pending.jobId());
      c.commit();
      runtime.update(
          "update ai_generation_attempts set finished_at=now(),outcome='FAILED',reason='CAPABILITY_UNAVAILABLE' where operation_id=?",
          pending.operationId());
      runtime.update(
          "update jobs set status='DEAD',completed_at=now(),lease_owner=null,lease_expires_at=null where id=?",
          pending.jobId());
      runtime.update(
          "update ai_generation_operations set status='FAILED',completed_at=now() where id=?",
          pending.operationId());
      c.commit();
      org.assertj.core.api.Assertions.assertThat(
              runtime.queryForObject(
                  "select status from ai_generation_operations where id=?",
                  String.class,
                  pending.operationId()))
          .isEqualTo("FAILED");
    }
  }

  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @org.springframework.test.context.DynamicPropertySource
  static void db(org.springframework.test.context.DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
    p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }
}
