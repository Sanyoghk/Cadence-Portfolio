package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlGenerationBudgetTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
    p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired CadenceUserRepository users;
  @Autowired AiGenerationService service;
  @Autowired GenerationBudgets budgets;
  @Autowired GenerationReservationRepository reservations;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.cadence.application.ports.BrandAnalysisPort analyzer;

  @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

  @Test
  void overrunReconciliationAndRuntimeLedgerInvariants() throws Exception {
    var workspace = workspaces.saveAndFlush(new Workspace("Budget", "Creator"));
    var brand = brands.saveAndFlush(new Brand("Budget", workspace));
    var actor =
        users
            .saveAndFlush(new CadenceUser("budget-owner", UserRole.OWNER, Set.of(workspace)))
            .getId();
    // UTC fixture period remains exactly 24 hours across a session-zone DST transition.
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setAutoCommit(false);
      try (var st = c.createStatement()) {
        st.execute("set local time zone 'Europe/London'");
      }
      var local =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      local.update(
          "insert into generation_reservations(id,created_at,updated_at,request_id,actor_id,workspace_id,brand_id,kind,request_digest,namespace,policy_version,policy_snapshot,quote_version,unit,scale,period_start,period_end,deadline,reserved,state) values(?,now(),now(),?,?,?,?,'STRATEGY',repeat('a',64),'SYNTHETIC','fixture-v1','upper=1','synthetic-fixed-v1','SYNTHETIC_CREDIT',0,'2026-10-25 00:00:00+00','2026-10-26 00:00:00+00','2026-10-25 00:01:00+00',1,'RESERVED')",
          UUID.randomUUID(),
          UUID.randomUUID(),
          actor,
          workspace.getId(),
          brand.getId());
      c.rollback();
    }
    var scope = Set.of(workspace.getId());
    var v =
        service.submit(
            actor, scope, brand.getId(), AiGenerationOperation.Kind.DECLARED, 0, "overrun");
    org.mockito.Mockito.when(analyzer.analyze(org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("unknown"));
    service.execute(service.claim(v.operationId()));
    assertThat(reservations.findByRequestId(v.operationId()).orElseThrow().getActual()).isNull();
    for (String assignment :
        List.of(
            "actor_id=gen_random_uuid()",
            "reserved=0",
            "state='RELEASED',dispatched_at=null,attempt_number=null",
            "policy_version='forged'"))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update generation_reservations set " + assignment + " where request_id=?",
                      v.operationId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var usage =
        new GenerationUsage(
            GenerationUsage.Origin.SYNTHETIC,
            "SYNTHETIC_CREDIT",
            0,
            Long.MAX_VALUE,
            "f".repeat(64));
    budgets.reconcile(v.operationId(), usage);
    budgets.reconcile(v.operationId(), usage);
    assertThat(reservations.findByRequestId(v.operationId()).orElseThrow().getActual())
        .isEqualTo(Long.MAX_VALUE);
    assertThat(service.status(actor, scope, v.jobId()).status()).isEqualTo("OUTCOME_UNKNOWN");
    assertThatThrownBy(
            () ->
                service.submit(
                    actor, scope, brand.getId(), AiGenerationOperation.Kind.DECLARED, 0, "blocked"))
        .isInstanceOf(com.cadence.application.exceptions.GenerationBudgetExceededException.class);
    for (String assignment :
        List.of(
            "actual=0",
            "usage_evidence=repeat('b',64)",
            "state='HELD_UNKNOWN',actual=null,usage_origin=null,usage_evidence=null,settled_at=null"))
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update generation_reservations set " + assignment + " where request_id=?",
                      v.operationId()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "delete from generation_reservations where request_id=?", v.operationId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("update generation_budget_mutex set id=1"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var st = c.createStatement()) {
        st.execute("create role cadence_runtime nologin");
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
            List.of(
                "delete from generation_reservations",
                "update generation_reservations set reserved=reserved",
                "update generation_reservations set actor_id=actor_id",
                "delete from generation_budget_mutex"))
          assertThatThrownBy(() -> st.execute(sql))
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  e -> assertThat(((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"));
        try (var r = st.executeQuery("select * from generation_budget_mutex for update")) {
          assertThat(r.next()).isTrue();
        }
      }
    }
  }
}
