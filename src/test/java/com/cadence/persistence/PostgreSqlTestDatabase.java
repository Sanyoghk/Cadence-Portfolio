package com.cadence.persistence;

import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.extension.*;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Testcontainers by default; an explicitly supplied disposable server also works without Docker.
 */
public final class PostgreSqlTestDatabase
    implements ExecutionCondition, BeforeAllCallback, AfterAllCallback {
  private final PostgreSQLContainer<?> container =
      com.cadence.support.TestContainerImages.postgres();
  private final String server = System.getenv("CADENCE_TEST_POSTGRES_URL");
  private final String database = "cadence_test_" + UUID.randomUUID().toString().replace("-", "");
  private String url;

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    return server != null || DockerClientFactory.instance().isDockerAvailable()
        ? ConditionEvaluationResult.enabled("PostgreSQL available")
        : ConditionEvaluationResult.disabled("Docker or explicit disposable PostgreSQL required");
  }

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    start();
  }

  private synchronized void start() throws Exception {
    if (url != null) return;
    if (server == null) {
      container.start();
      url = container.getJdbcUrl();
    } else {
      // Deliberately restrict this opt-in to a local disposable test server, never a remote DB.
      if (!server.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/postgres")) {
        throw new IllegalArgumentException(
            "Test server must use 127.0.0.1, explicit port and /postgres");
      }
      try (var connection = DriverManager.getConnection(server, getUsername(), getPassword());
          var statement = connection.createStatement()) {
        statement.execute("CREATE DATABASE " + database);
      }
      url = server.substring(0, server.lastIndexOf('/') + 1) + database;
    }
  }

  public String getJdbcUrl() {
    try {
      start();
      return url;
    } catch (Exception failure) {
      throw new IllegalStateException("Cannot initialize isolated test database", failure);
    }
  }

  public String getUsername() {
    return server == null ? container.getUsername() : System.getenv("CADENCE_TEST_POSTGRES_USER");
  }

  public String getPassword() {
    return server == null
        ? container.getPassword()
        : System.getenv("CADENCE_TEST_POSTGRES_PASSWORD");
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    if (url == null) return;
    if (server == null) {
      container.stop();
    } else {
      try (var connection = DriverManager.getConnection(server, getUsername(), getPassword());
          var statement = connection.createStatement()) {
        statement.execute("DROP DATABASE " + database + " WITH (FORCE)");
      }
    }
  }
}
