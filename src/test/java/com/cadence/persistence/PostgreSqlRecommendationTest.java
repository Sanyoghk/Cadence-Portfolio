package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.RecommendationApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlRecommendationTest extends RecommendationApiContract {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;

  private String digest(String value) {
    return com.cadence.domain.MetricRow.sha(
        value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private UUID admissionForSql(
      com.cadence.application.results.RecommendationDetails r, String key) {
    return recommendationKeys
        .saveAndFlush(
            new com.cadence.domain.RecommendationKey(
                actorId,
                workspace.getId(),
                brand.getId(),
                "GENERATE",
                digest(key),
                recommendationRows.findById(r.id()).orElseThrow().getRequestDigest()))
        .getId();
  }

  private void cloneCapture(java.sql.Connection c, UUID old, UUID id, UUID admission, String key)
      throws Exception {
    try (var s =
        c.prepareStatement(
            "insert into recommendation_insights select (jsonb_populate_record(null::recommendation_insights,to_jsonb(r)||jsonb_build_object('id',?::uuid,'request_id',?::uuid,'key_digest',?::text,'created_at',clock_timestamp(),'updated_at',clock_timestamp(),'captured_at',clock_timestamp()))).* from recommendation_insights r where id=?")) {
      s.setObject(1, id);
      s.setObject(2, admission);
      s.setString(3, digest(key));
      s.setObject(4, old);
      s.executeUpdate();
    }
  }

  @Test
  void sqlIncompleteCaptureRejectedByDeferredSampleGuard() throws Exception {
    seedRecommendation();
    var r = generate("sql-complete");
    var admission = admissionForSql(r, "sql-incomplete");
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      cloneCapture(c, r.id(), UUID.randomUUID(), admission, "sql-incomplete");
      assertThatThrownBy(c::commit)
          .isInstanceOfSatisfying(
              java.sql.SQLException.class,
              e -> {
                assertThat(e.getSQLState()).isEqualTo("23514");
                assertThat(e.getMessage()).contains("Invalid recommendation sample count");
              });
      c.rollback();
    }
    assertThat(recommendationRows.countByBrandId(brand.getId())).isEqualTo(1);
  }

  @Test
  void sqlExistingButWrongAttributionRejectedAtCommit() throws Exception {
    seedRecommendation();
    var r = generate("sql-linked");
    var admission = admissionForSql(r, "sql-wrong-link");
    var id = UUID.randomUUID();
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      cloneCapture(c, r.id(), id, admission, "sql-wrong-link");
      try (var s =
          c.prepareStatement(
              "insert into recommendation_metric_links(recommendation_id,item_order,observation_id,attribution_id,value_checksum) select ?,item_order,observation_id,case when item_order=0 then (select attribution_id from recommendation_metric_links where recommendation_id=? and item_order=1) else attribution_id end,value_checksum from recommendation_metric_links where recommendation_id=?")) {
        s.setObject(1, id);
        s.setObject(2, r.id());
        s.setObject(3, r.id());
        s.executeUpdate();
      }
      assertThatThrownBy(c::commit)
          .isInstanceOfSatisfying(
              java.sql.SQLException.class,
              e -> {
                assertThat(e.getSQLState()).isEqualTo("23514");
                assertThat(e.getMessage()).contains("Invalid or stale recommendation evidence");
              });
      c.rollback();
    }
    assertThat(recommendationRows.countByBrandId(brand.getId())).isEqualTo(1);
  }

  @Autowired javax.sql.DataSource datasource;

  @Test
  void runtimeRoleCanGenerateAndApplyButCannotRewriteReceipts() throws Exception {
    seedRecommendation();
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
      var r = generate("runtime");
      var result = apply(r.id(), "runtime-apply");
      assertThat(result.resolution().draftId()).isNotNull();
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update recommendation_keys set request_digest=repeat('0',64) where id=?",
                      r.operationId()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "delete from recommendation_resolutions where id=?",
                      result.resolution().id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    } finally {
      setRole(false);
    }
  }

  private void setRole(boolean enabled) throws Exception {
    var pool = (com.zaxxer.hikari.HikariDataSource) datasource;
    var cs = new ArrayList<java.sql.Connection>();
    try {
      for (int i = 0; i < pool.getMaximumPoolSize(); i++) cs.add(pool.getConnection());
      for (var c : cs)
        try (var s = c.createStatement()) {
          s.execute(enabled ? "set role cadence_runtime" : "reset role");
        }
    } finally {
      for (var c : cs) c.close();
    }
  }

  @Test
  void revocationDuringRuleCallPreventsPublication() {
    seedRecommendation();
    long before = recommendationRows.count();
    org.mockito.Mockito.doAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actorId);
              return ((com.cadence.application.ports.RecommendationInput) i.getArgument(0))
                  .expected();
            })
        .when(recommendationEngine)
        .recommend(org.mockito.ArgumentMatchers.any());
    assertThatThrownBy(() -> generate("revoked-egress"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(recommendationRows.count()).isEqualTo(before);
  }

  @Test
  void currentArchiveAndUnknownReceiptDenied() {
    seedRecommendation();
    var r = generate("archive");
    jdbc.update("update brands set status='ARCHIVED' where id=?", brand.getId());
    assertThatThrownBy(() -> apply(r.id(), "archived-apply"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> recommendations.read(UUID.randomUUID(), scope()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
  }

  @Test
  void concurrentGenerationAndApplyPublishExactlyOnce() throws Exception {
    seedRecommendation();
    var results = parallel(() -> generate("concurrent-gen").id());
    assertThat(results.get(0)).isEqualTo(results.get(1));
    var id = results.getFirst();
    var drafts = parallel(() -> apply(id, "concurrent-apply").resolution().draftId());
    assertThat(drafts.get(0)).isEqualTo(drafts.get(1));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recommendation_resolutions where recommendation_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
  }

  private List<UUID> parallel(Callable<UUID> task) throws Exception {
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var futures = new ArrayList<Future<UUID>>();
      for (int i = 0; i < 2; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  SecurityContextHolder.getContext().setAuthentication(owner);
                  try {
                    return task.call();
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      return List.of(
          futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
    }
  }

  @Test
  void revocationAndArchiveDenyApplyButHistoryRemains() {
    seedRecommendation();
    var r = generate("revoke");
    jdbc.update("delete from workspace_memberships where user_id=?", actorId);
    assertThatThrownBy(() -> apply(r.id(), "revoked"))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from recommendation_insights where id=?", Integer.class, r.id()))
        .isEqualTo(1);
  }

  @Test
  void sqlImmutableHistoryAndSourceLinksCannotBeForged() {
    seedRecommendation();
    var r = generate("sql");
    for (var sql :
        List.of(
            "update recommendation_insights set source_checksum=repeat('0',64) where id=?",
            "delete from recommendation_insights where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, r.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update recommendation_metric_links set observation_id=? where recommendation_id=?",
                    UUID.randomUUID(),
                    r.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into recommendation_metric_links(recommendation_id,item_order,observation_id,attribution_id,value_checksum) values(?,99,?,?,?)",
                    r.id(),
                    r.capture().sources().getFirst().observationId(),
                    r.capture().sources().getFirst().attributionId(),
                    r.capture().sources().getFirst().valueChecksum()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
