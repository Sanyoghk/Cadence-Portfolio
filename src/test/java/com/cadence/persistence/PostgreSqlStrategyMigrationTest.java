package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.*;

class PostgreSqlStrategyMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV23StrategiesKeepHistoryWithoutInventingGenerationProvenance() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("23")
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = connection.createStatement()) {
      s.execute(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
      s.execute(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
      for (int version = 1; version <= 2; version++) {
        String id = "00000000-0000-0000-0000-00000000000" + (version + 2);
        // Constants generated entirely by this test, never external input.
        s.execute(
            "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at) values ('"
                + id
                + "','00000000-0000-0000-0000-000000000002',"
                + version
                + ",'"
                + (version == 1 ? "APPROVED" : "DRAFT")
                + "',3,'UTC',now(),now())");
        s.execute(
            "insert into strategy_content_pillars values ('"
                + id
                + "',0,'Education',100,'Useful content')");
        s.execute("insert into strategy_best_publishing_times values ('" + id + "',0,'12:00:00')");
      }
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = connection.createStatement()) {
      try (var rows =
          s.executeQuery(
              "select version,status,generation_id,posts_per_week,revised_from_id,approved_by,approved_at from strategies order by version")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(1);
        assertThat(rows.getString(2)).isEqualTo("APPROVED");
        assertThat(rows.getObject(3)).isNull();
        assertThat(rows.getInt(4)).isEqualTo(3);
        assertThat(rows.getObject(5)).isNull();
        assertThat(rows.getObject(6)).isNull();
        assertThat(rows.getObject(7)).isNull();
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(2);
        assertThat(rows.getString(2)).isEqualTo("DRAFT");
        assertThat(rows.getObject(3)).isNull();
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          s.executeQuery(
              "select count(*) from strategy_content_pillars where pillar_name='Education' and pillar_weight=100")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
      try (var rows =
          s.executeQuery(
              "select count(*) from strategy_best_publishing_times where publishing_time='12:00:00'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
      try (var rows =
          s.executeQuery(
              "select count(*) from strategies where content_format_version=1 and whitespace_status is null and edited_by is null and edited_at is null and generation_content_format is null and generation_output_checksum is null")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isEqualTo(2);
      }
      try (var rows = s.executeQuery("select count(*) from strategy_playbook_slots")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getInt(1)).isZero();
      }
      assertThatThrownBy(
              () ->
                  s.execute(
                      "update strategy_content_pillars set pillar_name='Tampered' where strategy_id='00000000-0000-0000-0000-000000000003'"))
          .isInstanceOf(SQLException.class);
      assertThat(
              s.executeUpdate(
                  "update strategy_content_pillars set pillar_name='Draft edit' where strategy_id='00000000-0000-0000-0000-000000000004'"))
          .isEqualTo(1);
    }
  }
}
