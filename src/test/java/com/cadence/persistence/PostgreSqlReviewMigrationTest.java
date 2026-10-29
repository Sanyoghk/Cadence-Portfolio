package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.infrastructure.persistence.LegacyScanReceiptMigrationCallback;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgreSqlReviewMigrationTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void workspaceBudgetUpgradePreservesLegacyMembershipsButBlocksFurtherGrowth() throws Exception {
    String schema = "budget_" + UUID.randomUUID().toString().replace("-", "");
    configuration(schema)
        .callbacks(new LegacyScanReceiptMigrationCallback())
        .target("25")
        .load()
        .migrate();
    try (var connection = connection(schema);
        var statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO workspaces(id,name,industry,status,created_at,updated_at)
          SELECT gen_random_uuid(),'Legacy','Creator','ARCHIVED',now(),now() FROM generate_series(1,12);
          INSERT INTO cadence_users(id,external_subject,role,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000006','legacy-owner','OWNER',now(),now());
          INSERT INTO workspace_memberships(user_id,workspace_id)
          SELECT '00000000-0000-0000-0000-000000000006',id FROM workspaces;
          """);
    }
    configuration(schema).callbacks(new LegacyScanReceiptMigrationCallback()).load().migrate();
    try (var connection = connection(schema);
        var statement = connection.createStatement()) {
      try (var rows =
          statement.executeQuery(
              "SELECT workspace_membership_count,(SELECT count(*) FROM workspace_memberships) FROM cadence_users")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(12);
        assertThat(rows.getInt(2)).isEqualTo(12);
      }
      statement.execute(
          "INSERT INTO workspaces(id,name,industry,status,created_at,updated_at) VALUES ('00000000-0000-0000-0000-000000000007','Extra','Creator','ACTIVE',now(),now())");
      assertThatThrownBy(
              () ->
                  statement.execute(
                      "INSERT INTO workspace_memberships(user_id,workspace_id) VALUES ('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000007')"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("budget exceeded");
    }
  }

  @Test
  void populatedV13UpgradesAndPreservesImmutableReceiptHistory() throws Exception {
    String schema = legacyDatabase();
    configuration(schema).callbacks(new LegacyScanReceiptMigrationCallback()).load().migrate();
    try (var connection = connection(schema);
        var statement = connection.createStatement()) {
      try (var rows =
          statement.executeQuery(
              "SELECT scanner_engine,signature_version,verdict FROM evidence_scan_results")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("legacy-unknown");
        assertThat(rows.getString(2)).isEqualTo("legacy-unknown");
        assertThat(rows.getString(3)).isEqualTo("CLEAN");
        assertThat(rows.next()).isFalse();
      }
      assertThatThrownBy(
              () -> statement.execute("UPDATE evidence_scan_results SET verdict='INFECTED'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("immutable");
      assertTriggerEnabled(connection);
    }
  }

  @Test
  void failedBackfillRollsBackTriggerChangeTogetherWithMigration() throws Exception {
    String schema = legacyDatabase();
    var interrupted =
        new LegacyScanReceiptMigrationCallback() {
          @Override
          public void handle(Event event, Context context) {
            super.handle(event, context);
            if (event == Event.BEFORE_EACH_MIGRATE)
              throw new FlywayException("Injected migration interruption");
          }
        };
    assertThatThrownBy(() -> configuration(schema).callbacks(interrupted).load().migrate())
        .isInstanceOf(FlywayException.class);
    try (var connection = connection(schema);
        var statement = connection.createStatement()) {
      assertTriggerEnabled(connection);
      try (var rows =
          statement.executeQuery(
              "SELECT max(version::int) FROM flyway_schema_history WHERE success AND version IS NOT NULL")) {
        rows.next();
        assertThat(rows.getInt(1)).isEqualTo(13);
      }
      assertThatThrownBy(
              () -> statement.execute("UPDATE evidence_scan_results SET verdict='INFECTED'"))
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("immutable");
    }
    configuration(schema).callbacks(new LegacyScanReceiptMigrationCallback()).load().migrate();
  }

  private String legacyDatabase() throws Exception {
    String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
    configuration(schema).target("13").load().migrate();
    try (var connection = connection(schema);
        var statement = connection.createStatement()) {
      statement.execute(
          """
          INSERT INTO workspaces(id,name,industry,status,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000001','Legacy','Creator','ACTIVE',now(),now());
          INSERT INTO brands(id,name,workspace_id,status,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000002','Brand','00000000-0000-0000-0000-000000000001','ACTIVE',now(),now());
          INSERT INTO brand_evidence(id,brand_id,type,status,source_reference,checksum,captured_at,original_file_name,media_type,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','FILE','PENDING','quarantine:legacy',repeat('a',64),now()-interval '1 hour','legacy.pdf','application/pdf',now(),now());
          INSERT INTO jobs(id,type,payload_reference,idempotency_key,status,attempts,max_attempts,next_attempt_at,lease_owner,lease_expires_at,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000004','BRAND_EVIDENCE_INGEST','evidence:00000000-0000-0000-0000-000000000003','legacy','RUNNING',1,3,now(),'worker',now()+interval '5 minutes',now(),now());
          INSERT INTO evidence_scan_results(id,evidence_id,job_id,attempt_number,lease_owner,source_version,checksum,scanned_at,verdict,created_at,updated_at)
          VALUES ('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000004',1,'worker',0,repeat('a',64),now(),'CLEAN',now(),now());
          """);
    }
    return schema;
  }

  private FluentConfiguration configuration(String schema) {
    return Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema);
  }

  private Connection connection(String schema) throws SQLException {
    var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    connection.setSchema(schema);
    return connection;
  }

  private void assertTriggerEnabled(Connection connection) throws SQLException {
    try (var statement = connection.createStatement();
        var rows =
            statement.executeQuery(
                "SELECT tgenabled FROM pg_trigger WHERE tgrelid='evidence_scan_results'::regclass AND tgname='evidence_scan_result_guard'")) {
      assertThat(rows.next()).isTrue();
      assertThat(rows.getString(1)).isEqualTo("O");
    }
  }
}
