package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlApprovedStrategyMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV41GenerationKeepsExactHistoryWithoutInventedIdentityApproval() throws Exception {
    var config =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    config.target("41").load().migrate();
    UUID ws = UUID.randomUUID(), brand = UUID.randomUUID(), id = UUID.randomUUID();
    Map<String, Object> before;
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      jdbc.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Prior','Creator','ACTIVE',now(),now())",
          ws);
      jdbc.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Prior','ACTIVE',now(),now())",
          brand,
          ws);
      c.setAutoCommit(false);
      jdbc.update(
          "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at,generation_id,generation_brand_version,generation_workspace_version,generation_started_at,generation_deadline,generation_provider,generation_model_version,generation_prompt_version,generation_warnings) values(?,?,1,'DRAFT',3,'UTC',now(),now(),?,0,0,now(),now()+interval '60 seconds','prior-provider','v1','v1','HUMAN_REVIEW_REQUIRED')",
          id,
          brand,
          UUID.randomUUID());
      jdbc.update(
          "insert into strategy_content_pillars values(?,0,'Prior',100,'Prior content')", id);
      jdbc.update("insert into strategy_best_publishing_times values(?,0,'12:00:00')", id);
      c.commit();
      c.setAutoCommit(true);
      before = jdbc.queryForMap("select * from strategies where id=?", id);
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      var after = jdbc.queryForMap("select * from strategies where id=?", id);
      assertThat(after).containsAllEntriesOf(before);
      assertThat(after.get("generation_identity_id")).isNull();
      assertThat(after.get("generation_requested_by")).isNull();
      assertThat(
              jdbc.queryForObject(
                  "select pillar_name from strategy_content_pillars where strategy_id=?",
                  String.class,
                  id))
          .isEqualTo("Prior");
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into strategies select (jsonb_populate_record(null::strategies,to_jsonb(s)||jsonb_build_object('id',?::uuid,'version',2,'generation_id',?::uuid))).* from strategies s where s.id=?",
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
          .hasMessageContaining("Approved Identity source required");
      assertThat(jdbc.update("update strategies set posts_per_week=4 where id=?", id)).isEqualTo(1);
    }
  }
}
