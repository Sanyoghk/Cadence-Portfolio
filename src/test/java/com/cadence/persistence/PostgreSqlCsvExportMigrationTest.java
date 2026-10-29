package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgreSqlCsvExportMigrationTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV50RetainsExactStagingAuditAndNeverFabricatesExports() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("50")
        .load()
        .migrate();
    String before;
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      s.execute(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
      s.execute(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
      s.execute(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values ('00000000-0000-0000-0000-000000000003','migration-fixture','OWNER',true,now(),now())");
      s.execute(
          "insert into workspace_memberships(user_id,workspace_id) values ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001')");
      s.execute(
          "with t as (select clock_timestamp() as now) insert into media_assets(id,created_at,updated_at,lock_version,brand_id,fixture,origin,format,checksum,byte_size,width,height,duration_millis,requested_by,requested_at,deadline,status,moderation) select '00000000-0000-0000-0000-000000000004',now,now,0,'00000000-0000-0000-0000-000000000002','IMAGE_V1','DEMO_FIXTURE','PNG','6318b17380802c6bf6ecc6b0333af1a064ea70b5944ddbc36da4319d671ffc99',6688,512,512,0,'00000000-0000-0000-0000-000000000003',now,now+interval '120 seconds','STAGING','PENDING' from t");
      try (var r = s.executeQuery("select to_jsonb(a)::text from media_assets a")) {
        r.next();
        before = r.getString(1);
      }
    }
    assertThat(
            Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("51")
                .load()
                .migrate()
                .migrationsExecuted)
        .isEqualTo(1);
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      try (var r = s.executeQuery("select to_jsonb(a)::text from media_assets a")) {
        r.next();
        assertThat(r.getString(1)).isEqualTo(before);
      }
      for (var table : java.util.List.of("csv_exports", "csv_export_items"))
        try (var r = s.executeQuery("select count(*) from " + table)) {
          r.next();
          assertThat(r.getInt(1)).isZero();
        }
    }
  }
}
