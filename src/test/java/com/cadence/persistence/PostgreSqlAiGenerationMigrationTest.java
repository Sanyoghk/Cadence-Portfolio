package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlAiGenerationMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV42DoesNotInventJobsOrRewriteLegacyHistory() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("42")
        .load()
        .migrate();
    var workspace = UUID.randomUUID();
    var brand = UUID.randomUUID();
    Map<String, Object> before;
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var j =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      j.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Existing','Creator','ACTIVE',now(),now())",
          workspace);
      j.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Legacy','ACTIVE',now(),now())",
          brand,
          workspace);
      before = j.queryForMap("select * from brands where id=?", brand);
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var j =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      assertThat(j.queryForMap("select * from brands where id=?", brand)).isEqualTo(before);
      for (String table :
          List.of("ai_generation_operations", "ai_generation_sources", "ai_generation_attempts"))
        assertThat(j.queryForObject("select count(*) from " + table, Integer.class)).isZero();
    }
  }
}
