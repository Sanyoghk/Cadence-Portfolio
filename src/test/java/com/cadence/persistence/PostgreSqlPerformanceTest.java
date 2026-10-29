package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.repositories.MetricAttributionRepository;
import com.cadence.domain.*;
import com.cadence.infrastructure.analytics.PerformanceReadRepository;
import com.cadence.support.PerformanceApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlPerformanceTest extends PerformanceApiContract {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;
  @Autowired MetricAttributionRepository attributions;

  @Test
  void hundredContextVersionsStayOneSampleAndBounded() {
    var p = approved();
    var o = observation(p, 100, 20, 1, 1, 2);
    var first = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    var latest = first;
    for (int v = 2; v <= 100; v++)
      latest =
          performance.reviseAttribution(
              o,
              scope(),
              p.revisionId(),
              p.lockVersion(),
              v % 2 == 0 ? "community" : "education",
              v - 1);
    assertThat(latest.attributionNumber()).isEqualTo(100);
    assertThat(
            performance
                .reviseAttribution(o, scope(), p.revisionId(), p.lockVersion(), "community", 99)
                .id())
        .isEqualTo(latest.id());
    assertThatThrownBy(
            () ->
                performance.reviseAttribution(
                    o, scope(), p.revisionId(), p.lockVersion(), "education", 100))
        .isInstanceOf(IllegalStateException.class);
    assertThat(performance.attributionVersion(o, scope(), 1).id()).isEqualTo(first.id());
    assertThat(report(PerformanceQuery.Dimension.PILLAR).selectedRows()).isEqualTo(1);
    assertThat(report(PerformanceQuery.Dimension.PILLAR).items().getFirst().sources()).hasSize(1);
  }

  @Test
  void simultaneousAttributionReplayHasExactlyOneIdentity() throws Exception {
    var p = approved();
    var o = observation(p, 100, 20, 0, 0, 1);
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<Future<UUID>>();
      for (int i = 0; i < 2; i++)
        tasks.add(
            pool.submit(
                () -> {
                  start.await();
                  SecurityContextHolder.getContext().setAuthentication(owner);
                  try {
                    return performance
                        .attribute(o, scope(), p.revisionId(), p.lockVersion(), "education")
                        .id();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      assertThat(tasks.get(0).get(20, TimeUnit.SECONDS))
          .isEqualTo(tasks.get(1).get(20, TimeUnit.SECONDS));
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from metric_attributions where observation_id=?", Long.class, o))
        .isEqualTo(1);
  }

  @Test
  void currentMembershipRevocationDeniesReadAndClassification() {
    var p = approved();
    var o = observation(p, 100, 20, 0, 0, 1);
    jdbc.update("delete from workspace_memberships where user_id=?", actorId);
    assertThatThrownBy(() -> report(PerformanceQuery.Dimension.PLATFORM))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () -> performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void currentArchiveDeniesReadWithoutRemovingHistory() {
    var p = approved();
    var o = observation(p, 100, 20, 0, 0, 1);
    var a = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    jdbc.update("update brands set status='ARCHIVED' where id=?", brand.getId());
    assertThatThrownBy(() -> report(PerformanceQuery.Dimension.FORMAT))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from metric_attributions where id=?", Long.class, a.id()))
        .isEqualTo(1);
  }

  @Test
  void sqlForeignRevisionAndHistoryMutationsDenied() {
    var p = approved();
    var foreign = approved();
    var o = observation(p, 100, 20, 0, 0, 1);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into metric_attributions(id,created_at,updated_at,lock_version,attribution_number,observation_id,revision_id,revision_version,content_checksum,pillar_origin,actor_id,attributed_at) values(?,now(),now(),0,1,?,?,?,?,'UNKNOWN',?,clock_timestamp())",
                    UUID.randomUUID(),
                    o,
                    foreign.revisionId(),
                    foreign.lockVersion(),
                    PostScheduleEvent.checksum(content("Original")),
                    actorId))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            java.sql.SQLException.class,
            e -> {
              assertThat(e.getSQLState()).isEqualTo("23514");
              assertThat(e.getMessage()).contains("Invalid metrics attribution source or actor");
            });
    var a = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
    for (var sql :
        List.of(
            "update metric_attributions set pillar_id='community' where id=?",
            "delete from metric_attributions where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, a.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void boundedProjectionCapAndActualQueryPlan() throws Exception {
    var p = create();
    var q = query(PerformanceQuery.Dimension.PLATFORM);
    var base = clock.instant().minusSeconds(2);
    var all = new ArrayList<MetricRow>();
    for (int i = 0; i < 1001; i++)
      all.add(
          new MetricRow(
              p.id(),
              p.platform(),
              q.from(),
              q.until(),
              base.plusNanos(i * 1000L),
              100L,
              10L,
              0L,
              0L,
              1L,
              null));
    for (int i = 0; i < all.size(); i += 200) {
      var rows = all.subList(i, Math.min(i + 200, all.size()));
      var e =
          metricImports.submit(
              brand.getId(),
              scope(),
              "analytics-cap-" + UUID.randomUUID(),
              "MANUAL_JSON",
              mapper.writeValueAsBytes(
                  new com.cadence.application.services.MetricImportParser.Manual(rows)));
      metricWorker.execute(metricWorker.claim(e.id()));
      assertThat(metricImports.status(e.id(), scope(), false).status())
          .isEqualTo(JobStatus.SUCCEEDED);
    }
    assertThatThrownBy(() -> report(PerformanceQuery.Dimension.PLATFORM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("candidate cap");
    jdbc.execute("analyze metric_observations");
    jdbc.execute("analyze content_posts");
    var plan =
        jdbc.queryForObject(
            "explain (analyze,buffers,format json) " + PerformanceReadRepository.SQL,
            String.class,
            brand.getId(),
            workspace.getId(),
            q.until().atOffset(java.time.ZoneOffset.UTC),
            q.from().atOffset(java.time.ZoneOffset.UTC),
            "INSTAGRAM",
            "INSTAGRAM");
    var json = mapper.readTree(plan).get(0);
    assertThat(json.path("Plan").path("Actual Rows").asInt()).isEqualTo(1001);
    assertThat(json.path("Execution Time").asDouble()).isLessThan(2000);
    assertThat(
            jdbc.queryForList(
                "select indexname from pg_indexes where tablename='metric_observations'",
                String.class))
        .contains("metric_observation_window_idx");
    java.nio.file.Files.writeString(java.nio.file.Path.of("var/prompt20-query-plan.json"), plan);
  }

  @Test
  void runtimeRoleCanReadAndAttributeButCannotRewrite() throws Exception {
    var p = approved();
    var o = observation(p, 100, 20, 0, 0, 1);
    try (var c = datasource.getConnection();
        var s = c.createStatement()) {
      s.execute(
          "do $$ begin if not exists(select 1 from pg_roles where rolname='cadence_runtime') then create role cadence_runtime nologin;end if;end $$");
      s.execute("grant usage on schema public to cadence_runtime");
      s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
    }
    setRole(true);
    try {
      var a = performance.attribute(o, scope(), p.revisionId(), p.lockVersion(), "education");
      assertThat(report(PerformanceQuery.Dimension.FORMAT).selectedRows()).isEqualTo(1);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update metric_attributions set actor_id=? where id=?", actorId, a.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      setRole(false);
    }
  }

  private void setRole(boolean enabled) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var connections = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) connections.add(pool.getConnection());
      for (var c : connections)
        try (var s = c.createStatement()) {
          s.execute(enabled ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : connections) c.close();
    }
  }
}
