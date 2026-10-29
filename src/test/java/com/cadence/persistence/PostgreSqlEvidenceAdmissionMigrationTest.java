package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlEvidenceAdmissionMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void populatedV32UpgradePreservesReviewAndWithdrawalAndEnforcesNewContext(boolean withdrawn)
      throws Exception {
    var schema = "rereview_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("32")
        .load()
        .migrate();
    var ws = java.util.UUID.randomUUID();
    var brand = java.util.UUID.randomUUID();
    var source = java.util.UUID.randomUUID();
    var actor = java.util.UUID.randomUUID();
    var admission = java.util.UUID.randomUUID();
    java.util.Map<String, Object> original;
    java.util.Map<String, Object> originalWithdrawal = null;
    String insert =
        "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) select ?,id,lock_version,?,0,checksum,encode(sha256(convert_to(source_reference,'UTF8')),'hex'),source_reference,?,clock_timestamp(),'text-review-v1',clock_timestamp(),clock_timestamp() from brand_evidence where id=?";
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      jdbc.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Studio','Creator','ACTIVE',now(),now())",
          ws);
      jdbc.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Brand','ACTIVE',now(),now())",
          brand,
          ws);
      jdbc.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'upgrade-owner','OWNER',true,now(),now())",
          actor);
      jdbc.update("insert into workspace_memberships(user_id,workspace_id) values(?,?)", actor, ws);
      String text = "Prior human review";
      jdbc.update(
          "insert into brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,created_at,updated_at) values(?,?,'TEXT','PENDING',?,?,now(),now(),now())",
          source,
          brand,
          text,
          com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + text));
      jdbc.update(insert, admission, 0, actor, source);
      if (withdrawn) {
        jdbc.update(
            "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,now(),now(),now())",
            java.util.UUID.randomUUID(),
            admission,
            actor);
        originalWithdrawal = jdbc.queryForMap("select * from evidence_admission_withdrawals");
      }
      original = jdbc.queryForMap("select * from evidence_admissions");
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
      assertThat(jdbc.queryForMap("select * from evidence_admissions"))
          .containsAllEntriesOf(original)
          .containsEntry("artifact_id", null)
          .containsEntry("original_scan_id", null)
          .containsEntry("latest_scan_id", null)
          .containsEntry("renderer_version", null)
          .containsEntry("scan_expires_at", null);
      if (withdrawn)
        assertThat(jdbc.queryForMap("select * from evidence_admission_withdrawals"))
            .isEqualTo(originalWithdrawal);
      else
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from evidence_admission_withdrawals", Integer.class))
            .isZero();
      jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand);
      if (withdrawn)
        assertThatThrownBy(() -> jdbc.update(insert, java.util.UUID.randomUUID(), 1, actor, source))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      else {
        assertThat(jdbc.update(insert, java.util.UUID.randomUUID(), 1, actor, source)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(insert, java.util.UUID.randomUUID(), 1, actor, source))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      }
    }
  }

  @Test
  void populatedV31ReviewRemainsUnchangedAndIsNotAutomaticallyWithdrawn() throws Exception {
    var schema = "withdrawal_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("31")
        .load()
        .migrate();
    String text = "Reviewed existing input";
    var ws = java.util.UUID.randomUUID();
    var brand = java.util.UUID.randomUUID();
    var source = java.util.UUID.randomUUID();
    var actor = java.util.UUID.randomUUID();
    var admission = java.util.UUID.randomUUID();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      jdbc.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Studio','Creator','ACTIVE',now(),now())",
          ws);
      jdbc.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Brand','ACTIVE',now(),now())",
          brand,
          ws);
      jdbc.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'migration-owner','OWNER',true,now(),now())",
          actor);
      jdbc.update("insert into workspace_memberships(user_id,workspace_id) values(?,?)", actor, ws);
      jdbc.update(
          "insert into brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,created_at,updated_at) values(?,?,'TEXT','PENDING',?,?,now(),now(),now())",
          source,
          brand,
          text,
          com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + text));
      jdbc.update(
          "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,0,0,?,?,?,?,now(),'text-review-v1',now(),now())",
          admission,
          source,
          com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + text),
          com.cadence.domain.EvidenceAdmission.checksum(text),
          text,
          actor);
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
      try (var s = c.createStatement();
          var rows =
              s.executeQuery(
                  "select reviewed_text,content_checksum,lock_version,(select count(*) from evidence_admission_withdrawals) from evidence_admissions")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo(text);
        assertThat(rows.getString(2))
            .isEqualTo(com.cadence.domain.EvidenceAdmission.checksum(text));
        assertThat(rows.getLong(3)).isZero();
        assertThat(rows.getInt(4)).isZero();
        assertThat(rows.next()).isFalse();
      }
    }
  }

  @Test
  void populatedV30EvidenceIsNotAutomaticallyAdmittedOrMarkedReady() throws Exception {
    var schema = "admission_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("30")
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      try (var s = c.createStatement()) {
        s.execute(
            "insert into workspaces(id,name,industry,status,created_at,updated_at) values('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
        s.execute(
            "insert into brands(id,workspace_id,name,status,created_at,updated_at) values('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
        s.execute(
            "insert into brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,created_at,updated_at) values('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','TEXT','PENDING','Existing text',encode(sha256(convert_to(E'TEXT\\nExisting text','UTF8')),'hex'),now(),now(),now())");
      }
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
      try (var s = c.createStatement();
          var rows =
              s.executeQuery(
                  "select status,source_reference,lock_version,(select count(*) from evidence_admissions) from brand_evidence")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("PENDING");
        assertThat(rows.getString(2)).isEqualTo("Existing text");
        assertThat(rows.getLong(3)).isZero();
        assertThat(rows.getInt(4)).isZero();
        assertThat(rows.next()).isFalse();
      }
    }
  }
}
