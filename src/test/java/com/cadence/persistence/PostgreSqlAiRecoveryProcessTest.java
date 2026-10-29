package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PostgreSqlAiRecoveryProcessTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @TempDir Path directory;
  String generationKind;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"DECLARED", "CONTENT"})
  void crashBeforeDispatchRecoversInNewJvm(String kind) throws Exception {
    generationKind = kind;
    scenario("claim", "SUCCEEDED", 81);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"DECLARED", "CONTENT"})
  void crashAfterDispatchDoesNotRedispatch(String kind) throws Exception {
    generationKind = kind;
    scenario("dispatch", "OUTCOME_UNKNOWN", 82);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"DECLARED", "CONTENT"})
  void crashAfterCommitKeepsOneResult(String kind) throws Exception {
    generationKind = kind;
    scenario("commit", "SUCCEEDED", 83);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"DECLARED", "CONTENT"})
  void crashDuringPublicationRollsBackBeforeRestart(String kind) throws Exception {
    generationKind = kind;
    scenario("publication", "OUTCOME_UNKNOWN", -1);
  }

  void scenario(String phase, String expected, int exit) throws Exception {
    Path state = directory.resolve("state");
    Process child = start(phase, state);
    try (var connection =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var jdbc =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true));
      if (phase.equals("publication")) {
        var limit = Instant.now().plusSeconds(90);
        Integer backend = null;
        while (Instant.now().isBefore(limit) && child.isAlive()) {
          var ids =
              jdbc.queryForList(
                  "select pid from pg_stat_activity where datname=current_database() and wait_event='PgSleep'",
                  Integer.class);
          if (!ids.isEmpty()) {
            backend = ids.getFirst();
            break;
          }
          Thread.sleep(100);
        }
        assertThat(backend).as("child reached uncommitted publication; see child log").isNotNull();
        child.destroyForcibly();
        assertThat(child.waitFor(10, TimeUnit.SECONDS)).isTrue();
        jdbc.queryForObject("select pg_terminate_backend(?)", Boolean.class, backend);
        jdbc.execute("drop trigger pause_test_publication on ai_generation_operations");
        jdbc.execute("drop function pause_test_publication()");
      } else {
        assertThat(child.waitFor(90, TimeUnit.SECONDS))
            .as("child completed; see child log")
            .isTrue();
        assertThat(child.exitValue()).isEqualTo(exit);
      }
      UUID operation = UUID.fromString(Files.readAllLines(state).getFirst());
      Map<String, Object> before =
          jdbc.queryForMap(
              "select status,result_id,snapshot_checksum from ai_generation_operations where id=?",
              operation);
      Process restarted = start("recover", state);
      try {
        assertThat(restarted.waitFor(90, TimeUnit.SECONDS)).isTrue();
        assertThat(restarted.exitValue()).isZero();
      } finally {
        if (restarted.isAlive()) restarted.destroyForcibly();
      }
      assertThat(Files.readString(directory.resolve("result"))).isEqualTo(expected);
      var accounting =
          jdbc.queryForMap(
              "select state,actual,usage_origin from generation_reservations where operation_id=?",
              operation);
      if (phase.equals("dispatch")) {
        assertThat(accounting.get("state")).isEqualTo("HELD_UNKNOWN");
        assertThat(accounting.get("actual")).isNull();
      } else {
        assertThat(accounting.get("state")).isEqualTo("SETTLED");
        assertThat(((Number) accounting.get("actual")).longValue()).isEqualTo(1);
        assertThat(accounting.get("usage_origin")).isEqualTo("SYNTHETIC");
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from generation_reservations where operation_id=?",
                  Long.class,
                  operation))
          .isEqualTo(1);
      var after =
          jdbc.queryForMap(
              "select status,result_id,snapshot_checksum from ai_generation_operations where id=?",
              operation);
      assertThat(after.get("snapshot_checksum")).isEqualTo(before.get("snapshot_checksum"));
      if (phase.equals("commit")) assertThat(after).isEqualTo(before);
      if (expected.equals("OUTCOME_UNKNOWN")) assertThat(after.get("result_id")).isNull();
      if (generationKind.equals("CONTENT")) {
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from content_generation_batches where operation_id=?",
                    Integer.class,
                    operation))
            .isEqualTo(expected.equals("SUCCEEDED") ? 1 : 0);
        assertThat(
                jdbc.queryForObject(
                    "select count(*) from post_revisions r join content_posts p on p.id=r.post_id join ai_generation_operations o on o.brand_id=p.brand_id where o.id=?",
                    Integer.class,
                    operation))
            .isEqualTo(expected.equals("SUCCEEDED") ? 1 : 0);
      }
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from ai_generation_attempts where operation_id=?",
                  Integer.class,
                  operation))
          .isEqualTo(phase.equals("claim") ? 2 : 1);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from ai_generation_attempts where operation_id=? and dispatched_at is not null",
                  Integer.class,
                  operation))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from ai_generation_attempts where operation_id=? and finished_at is null",
                  Integer.class,
                  operation))
          .isZero();
    } finally {
      if (child.isAlive()) child.destroyForcibly();
    }
  }

  Process start(String phase, Path state) throws Exception {
    Path args = directory.resolve(phase + ".args");
    String cp =
        System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
    Files.writeString(
        args,
        "-Xmx256m\n-cp\n\""
            + cp.replace("\\", "/")
            + "\"\ncom.cadence.support.AiRecoveryProcess\n"
            + phase
            + "\n\""
            + state.toString().replace("\\", "/")
            + "\"\n"
            + generationKind
            + "\n");
    var builder =
        new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(), "@" + args);
    builder.environment().put("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
    builder.environment().put("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
    builder.environment().put("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
    builder.environment().put("SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver");
    // Only synthetic Testcontainers credentials in child environment; never command arguments/logs.
    Path logs = Path.of("target", "recovery-process-logs");
    Files.createDirectories(logs);
    return builder
        .redirectErrorStream(true)
        .redirectOutput(logs.resolve(directory.getFileName() + "-" + phase + ".log").toFile())
        .start();
  }
}
