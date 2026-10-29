package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlIdentityEvidenceMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV34HistoryKeepsContentApprovalAndNullEvidenceAudit() throws Exception {
    var schema = "identity_evidence_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("34")
        .load()
        .migrate();
    UUID ws = UUID.randomUUID(),
        brand = UUID.randomUUID(),
        actor = UUID.randomUUID(),
        identity = UUID.randomUUID();
    Map<String, Object> original;
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      c.setAutoCommit(false);
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      jdbc.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Prior studio','Creator','ACTIVE',now(),now())",
          ws);
      jdbc.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Prior brand','ACTIVE',now(),now())",
          brand,
          ws);
      jdbc.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'prior-owner','OWNER',true,now(),now())",
          actor);
      jdbc.update("insert into workspace_memberships(user_id,workspace_id) values(?,?)", actor, ws);
      jdbc.update(
          "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,1,'DRAFT',now(),now(),'#000000','#111111','#222222','#333333','#FFFFFF','Prior caption')",
          identity,
          brand);
      jdbc.update("insert into identity_fonts values(?,0,'Inter')", identity);
      jdbc.update("insert into identity_voice_up values(?,0,'Clear')", identity);
      jdbc.update("insert into identity_voice_down values(?,0,'Spam')", identity);
      c.commit();
      jdbc.update(
          "update brand_identities set status='APPROVED',approved_by=?,approved_at=clock_timestamp(),lock_version=lock_version+1 where id=?",
          actor,
          identity);
      c.commit();
      original = jdbc.queryForMap("select * from brand_identities where id=?", identity);
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      var after = jdbc.queryForMap("select * from brand_identities where id=?", identity);
      assertThat(after.remove("analysis_copied_by")).isNull();
      assertThat(after).isEqualTo(original);
      assertThat(
              jdbc.queryForList(
                  "select item_value from identity_fonts where identity_id=?",
                  String.class,
                  identity))
          .containsExactly("Inter");
      assertThat(jdbc.queryForObject("select count(*) from brand_analysis_sources", Long.class))
          .isZero();
      assertThat(jdbc.queryForObject("select count(*) from brand_analysis_citations", Long.class))
          .isZero();
    }
  }
}
