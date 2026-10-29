package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

class PostgreSqlEvidenceConnectionPolicyTest {
  @Test
  void postgresqlConnectionsAlwaysHaveFiniteLockAndStatementBudgets() {
    try (var datasource = new HikariDataSource()) {
      datasource.setJdbcUrl("jdbc:postgresql://localhost/fixture");
      var policy = PostgreSqlEvidenceConnectionPolicy.evidenceDatabaseTimeouts();
      assertThat(policy.postProcessBeforeInitialization(datasource, "dataSource"))
          .isSameAs(datasource);
      assertThat(datasource.getConnectionInitSql())
          .isEqualTo(PostgreSqlEvidenceConnectionPolicy.TIMEOUT_SQL);
      assertThatCode(() -> policy.postProcessBeforeInitialization(datasource, "dataSource"))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void overridesCannotSilentlyDisableTheDatabaseDeadline() {
    try (var datasource = new HikariDataSource()) {
      datasource.setJdbcUrl("jdbc:postgresql://localhost/fixture");
      datasource.setConnectionInitSql("SET statement_timeout = 0");
      assertThatThrownBy(
              () ->
                  PostgreSqlEvidenceConnectionPolicy.evidenceDatabaseTimeouts()
                      .postProcessBeforeInitialization(datasource, "dataSource"))
          .hasMessage("Invalid evidence database timeout policy");
    }
  }

  @Test
  void localH2DoesNotReceivePostgresqlSpecificSql() {
    try (var datasource = new HikariDataSource()) {
      datasource.setJdbcUrl("jdbc:h2:mem:fixture");
      PostgreSqlEvidenceConnectionPolicy.evidenceDatabaseTimeouts()
          .postProcessBeforeInitialization(datasource, "dataSource");
      assertThat(datasource.getConnectionInitSql()).isNull();
    }
  }
}
