package com.cadence.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlUtcMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedLegacyDatabaseIsInterpretedExplicitlyAsUtcRegardlessOfSessionTimezone()
      throws Exception {
    var configuration =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    configuration.target("21").load().migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("SET TIME ZONE 'Pacific/Auckland'");
      statement.execute(
          "INSERT INTO workspaces (id,name,industry,status,created_at,updated_at) VALUES ('00000000-0000-0000-0000-000000000111','Demo','Creator','ACTIVE',TIMESTAMP '2026-07-01 12:00:00',TIMESTAMP '2026-07-01 13:00:00')");
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .initSql("SET TIME ZONE 'Europe/London'")
        .load()
        .migrate();
    try (var connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
      statement.execute("SET TIME ZONE 'America/Los_Angeles'");
      try (var rows = statement.executeQuery("SELECT created_at,updated_at FROM workspaces")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getObject(1, OffsetDateTime.class).toInstant())
            .isEqualTo(Instant.parse("2026-07-01T12:00:00Z"));
        assertThat(rows.getObject(2, OffsetDateTime.class).toInstant())
            .isEqualTo(Instant.parse("2026-07-01T13:00:00Z"));
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          statement.executeQuery(
              "SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name='brand_identities' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone' ORDER BY column_name")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("created_at");
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("updated_at");
        assertThat(rows.next()).isFalse();
      }
      try (var rows =
          statement.executeQuery(
              "SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='strategies' AND column_name='edited_at'")) {
        assertThat(rows.next()).isTrue();
        assertThat(rows.getString(1)).isEqualTo("timestamp with time zone");
      }
      try (var rows =
          statement.executeQuery(
              "SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND column_name IN ('created_at','updated_at') AND data_type='timestamp with time zone'")) {
        assertThat(rows.next()).isTrue();
        // V55 adds creator report audit. V54 adds recommendation/key/resolution audit. V53 adds
        // human attribution audit. V52 adds
        // raw metric receipt and observation audit. V51
        // adds durable CSV audit. V50 adds
        // media asset audit. V49 adds the append-only schedule
        // ledger. V48 adds a
        // timestamped immutable Content
        // generation receipt. V47 adds Post tables. V46
        // edited_at is outside this
        // count.
        assertThat(rows.getInt(1)).isEqualTo(70);
      }
    }
  }
}
