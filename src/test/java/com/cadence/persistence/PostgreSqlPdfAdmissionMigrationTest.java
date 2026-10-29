package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.DriverManager;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlPdfAdmissionMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV35UpgradePreservesTextReceiptAndWithdrawalWithoutPdfBackfill() throws Exception {
    String schema = "pdf_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("35")
        .load()
        .migrate();
    UUID ws = UUID.randomUUID(),
        b = UUID.randomUUID(),
        u = UUID.randomUUID(),
        e = UUID.randomUUID(),
        a = UUID.randomUUID();
    Map<String, Object> old, withdrawal;
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
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Prior','ACTIVE',now(),now())",
          b,
          ws);
      jdbc.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'prior-pdf-upgrade','OWNER',true,now(),now())",
          u);
      jdbc.update("insert into workspace_memberships(user_id,workspace_id) values(?,?)", u, ws);
      jdbc.update(
          "insert into brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,created_at,updated_at) values(?,?,'TEXT','PENDING','Prior text',? ,now(),now(),now())",
          e,
          b,
          com.cadence.domain.EvidenceAdmission.checksum("TEXT\nPrior text"));
      jdbc.update(
          "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) select ?,id,0,0,0,checksum,encode(sha256(convert_to(source_reference,'UTF8')),'hex'),source_reference,?,clock_timestamp(),'text-review-v1',clock_timestamp(),clock_timestamp() from brand_evidence where id=?",
          a,
          u,
          e);
      jdbc.update(
          "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,clock_timestamp(),clock_timestamp(),clock_timestamp())",
          UUID.randomUUID(),
          a,
          u);
      old = jdbc.queryForMap("select * from evidence_admissions where id=?", a);
      withdrawal =
          jdbc.queryForMap("select * from evidence_admission_withdrawals where admission_id=?", a);
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
      assertThat(jdbc.queryForMap("select * from evidence_admissions where id=?", a))
          .containsAllEntriesOf(old)
          .containsEntry("artifact_id", null)
          .containsEntry("original_scan_id", null)
          .containsEntry("latest_scan_id", null)
          .containsEntry("renderer_version", null)
          .containsEntry("scan_expires_at", null);
      assertThat(
              jdbc.queryForMap(
                  "select * from evidence_admission_withdrawals where admission_id=?", a))
          .isEqualTo(withdrawal);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "update evidence_admissions set reviewed_text='Forged' where id=?", a))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from pg_indexes where schemaname=? and indexname in ('admission_text_context_unique','admission_pdf_context_unique','evidence_admission_source_history_idx')",
                  Integer.class,
                  schema))
          .isEqualTo(3);
    }
  }
}
