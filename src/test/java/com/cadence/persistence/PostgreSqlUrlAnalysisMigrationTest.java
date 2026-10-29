package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.cadence.domain.EvidenceAdmission;
import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlUrlAnalysisMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV39TextAttemptSourcesAndQuotesRemainUnchangedAfterV40() throws Exception {
    String schema = "pdf_identity_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("39")
        .load()
        .migrate();
    UUID ws = UUID.randomUUID(),
        b = UUID.randomUUID(),
        u = UUID.randomUUID(),
        e = UUID.randomUUID(),
        a = UUID.randomUUID(),
        attempt = UUID.randomUUID();
    String text = "Reviewed prior text";
    var command =
        new BrandAnalysisCommand(
            attempt,
            0,
            0,
            "Prior brand",
            "Creator",
            List.of(new AnalysisTextSource(a, e, 0, EvidenceAdmission.checksum(text), text)));
    Map<String, Object> oldAttempt, oldSource, oldQuote;
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      jdbc.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Prior','Creator','ACTIVE',now(),now())",
          ws);
      jdbc.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Prior brand','ACTIVE',now(),now())",
          b,
          ws);
      jdbc.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'prior-pdf-analysis','OWNER',true,now(),now())",
          u);
      jdbc.update("insert into workspace_memberships(user_id,workspace_id) values(?,?)", u, ws);
      jdbc.update(
          "insert into brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,created_at,updated_at) values(?,?,'TEXT','PENDING',?,? ,now(),now(),now())",
          e,
          b,
          text,
          EvidenceAdmission.checksum("TEXT\n" + text));
      jdbc.update(
          "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) select ?,id,0,0,0,checksum,encode(sha256(convert_to(source_reference,'UTF8')),'hex'),source_reference,?,clock_timestamp(),'text-review-v1',clock_timestamp(),clock_timestamp() from brand_evidence where id=?",
          a,
          u,
          e);
      c.setAutoCommit(false);
      jdbc.update(
          "insert into brand_analysis_attempts(id,brand_id,brand_version,workspace_version,source_checksum,started_at,deadline,status,requested_by,input_brand_name,input_industry,created_at,updated_at) values(?,?,0,0,?,statement_timestamp(),statement_timestamp()+interval '60 seconds','RUNNING',?,'Prior brand','Creator',clock_timestamp(),clock_timestamp())",
          attempt,
          b,
          command.sourceChecksum(),
          u);
      jdbc.update(
          "insert into brand_analysis_sources(id,attempt_id,admission_id,position,created_at,updated_at) values(?,?,?,0,clock_timestamp(),clock_timestamp())",
          UUID.randomUUID(),
          attempt,
          a);
      jdbc.update(
          "insert into brand_analysis_citations(id,attempt_id,admission_id,position,quote,created_at,updated_at) values(?,?,?,0,'Reviewed',clock_timestamp(),clock_timestamp())",
          UUID.randomUUID(),
          attempt,
          a);
      c.commit();
      oldAttempt = jdbc.queryForMap("select * from brand_analysis_attempts where id=?", attempt);
      oldSource =
          jdbc.queryForMap("select * from brand_analysis_sources where attempt_id=?", attempt);
      oldQuote =
          jdbc.queryForMap("select * from brand_analysis_citations where attempt_id=?", attempt);
      oldQuote.put("field_path", null);
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
      assertThat(jdbc.queryForMap("select * from brand_analysis_attempts where id=?", attempt))
          .isEqualTo(oldAttempt);
      assertThat(
              jdbc.queryForMap("select * from brand_analysis_sources where attempt_id=?", attempt))
          .isEqualTo(oldSource);
      assertThat(
              jdbc.queryForMap(
                  "select * from brand_analysis_citations where attempt_id=?", attempt))
          .isEqualTo(oldQuote);
      try (var s = c.prepareStatement("select validate_analysis_admission(?,?,false)")) {
        s.setObject(1, attempt);
        s.setObject(2, a);
        assertThat(s.execute()).isTrue();
      }
    }
  }

  @Test
  void populatedV39ApprovedIdentityRemainsExactlyUnchanged() throws Exception {
    var schema = "identity_evidence_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("39")
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
