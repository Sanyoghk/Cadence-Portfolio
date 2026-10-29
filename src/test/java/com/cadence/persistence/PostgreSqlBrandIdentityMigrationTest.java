package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlBrandIdentityMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV29HistoryHasNoInventedAnalysisOriginAfterUpgrade() throws Exception {
    var schema = "origin_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("29")
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setSchema(schema);
      c.setAutoCommit(false);
      try (var s = c.createStatement()) {
        s.execute(
            "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
        s.execute(
            "insert into brands(id,workspace_id,name,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
        s.execute(
            "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002',1,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Existing')");
        s.execute(
            "insert into identity_fonts values('00000000-0000-0000-0000-000000000003',0,'Inter')");
        s.execute(
            "insert into identity_voice_up values('00000000-0000-0000-0000-000000000003',0,'Clear')");
        s.execute(
            "insert into identity_voice_down values('00000000-0000-0000-0000-000000000003',0,'Spam')");
      }
      c.commit();
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
                  "select version,status,sample_caption,analysis_attempt_id from brand_identities")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(1);
        assertThat(rows.getString(2)).isEqualTo("DRAFT");
        assertThat(rows.getString(3)).isEqualTo("Existing");
        assertThat(rows.getObject(4)).isNull();
        assertThat(rows.next()).isFalse();
      }
    }
  }

  @Test
  void populatedV28DraftKeepsContentVersionAndNullAuditAfterLifecycleUpgrade() throws Exception {
    var schema = "identity_" + java.util.UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("28")
        .load()
        .migrate();
    var id = java.util.UUID.randomUUID();
    try (var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setSchema(schema);
      connection.setAutoCommit(false);
      try (var s = connection.createStatement()) {
        s.execute(
            "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
        s.execute(
            "insert into brands(id,workspace_id,name,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
      }
      try (var root =
          connection.prepareStatement(
              "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,lock_version,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,'00000000-0000-0000-0000-000000000002',1,'DRAFT',now(),now(),7,'#112233','#FFFFFF','#FF0000','#808080','#F5F5F5','Existing draft')")) {
        root.setObject(1, id);
        root.executeUpdate();
      }
      for (var query :
          java.util.List.of(
              "insert into identity_fonts values(?,0,'Inter')",
              "insert into identity_voice_up values(?,0,'Clear')",
              "insert into identity_voice_down values(?,0,'Spam')")) {
        try (var s = connection.prepareStatement(query)) {
          s.setObject(1, id);
          s.executeUpdate();
        }
      }
      connection.commit();
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    try (var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setSchema(schema);
      try (var s =
          connection.prepareStatement(
              "select version,status,lock_version,primary_color,sample_caption,revised_from_id,approved_by,approved_at from brand_identities where id=?")) {
        s.setObject(1, id);
        try (var rows = s.executeQuery()) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getInt(1)).isEqualTo(1);
          assertThat(rows.getString(2)).isEqualTo("DRAFT");
          assertThat(rows.getLong(3)).isEqualTo(7);
          assertThat(rows.getString(4)).isEqualTo("#112233");
          assertThat(rows.getString(5)).isEqualTo("Existing draft");
          for (int column = 6; column <= 8; column++) assertThat(rows.getObject(column)).isNull();
          assertThat(rows.next()).isFalse();
        }
      }
      for (var entry :
          java.util.Map.of(
                  "identity_fonts",
                  "Inter",
                  "identity_voice_up",
                  "Clear",
                  "identity_voice_down",
                  "Spam")
              .entrySet()) {
        // Table names are a static test allowlist, never caller-controlled input.
        try (var s =
            connection.prepareStatement(
                "select item_value from " + entry.getKey() + " where identity_id=?")) {
          s.setObject(1, id);
          try (var rows = s.executeQuery()) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(entry.getValue());
            assertThat(rows.next()).isFalse();
          }
        }
      }
    }
  }

  @Test
  void populatedLegacySignatureSurvivesWithoutInventingIdentityRevisions() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("27")
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = connection.createStatement()) {
      s.execute(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
      s.execute(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Legacy caption')");
      s.execute(
          "insert into brand_signature_fonts values ('00000000-0000-0000-0000-000000000002',0,'Inter')");
      s.execute(
          "insert into brand_signature_voice_up values ('00000000-0000-0000-0000-000000000002',0,'clear')");
      s.execute(
          "insert into brand_signature_voice_down values ('00000000-0000-0000-0000-000000000002',0,'spam')");
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = connection.createStatement()) {
      try (var rows = s.executeQuery("select primary_color,sample_caption from brands")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("#000000");
        assertThat(rows.getString(2)).isEqualTo("Legacy caption");
      }
      for (var query :
          java.util.List.of(
              "select font from brand_signature_fonts",
              "select voice_up from brand_signature_voice_up",
              "select voice_down from brand_signature_voice_down")) {
        try (var rows = s.executeQuery(query)) {
          assertThat(rows.next()).isTrue();
          assertThat(rows.getString(1)).isIn("Inter", "clear", "spam");
          assertThat(rows.next()).isFalse();
        }
      }
      try (var rows = s.executeQuery("select count(*) from brand_identities")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isZero();
      }
    }
  }
}
