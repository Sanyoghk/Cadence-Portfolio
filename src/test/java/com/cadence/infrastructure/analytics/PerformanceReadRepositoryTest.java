package com.cadence.infrastructure.analytics;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.domain.PerformanceQuery;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PerformanceReadRepositoryTest {
  @ParameterizedTest
  @ValueSource(ints = {0, 1, 5})
  void executionTimeoutHonorsPolicyAndShorterTransactionDeadline(int transactionSeconds)
      throws Exception {
    var datasource = mock(DataSource.class);
    var connection = mock(Connection.class);
    var statement = mock(PreparedStatement.class);
    var rows = mock(ResultSet.class);
    var timeout = new AtomicInteger();
    when(datasource.getConnection()).thenReturn(connection);
    when(connection.prepareStatement(PerformanceReadRepository.SQL)).thenReturn(statement);
    doAnswer(
            i -> {
              timeout.set(i.getArgument(0));
              return null;
            })
        .when(statement)
        .setQueryTimeout(anyInt());
    when(statement.getQueryTimeout()).thenAnswer(i -> timeout.get());
    when(statement.executeQuery())
        .thenAnswer(
            i -> {
              assertThat(timeout.get()).isEqualTo(transactionSeconds == 1 ? 1 : 3);
              return rows;
            });
    if (transactionSeconds > 0) {
      var holder = new ConnectionHolder(connection);
      holder.setTimeoutInSeconds(transactionSeconds);
      TransactionSynchronizationManager.bindResource(datasource, holder);
    }
    try {
      var query =
          new PerformanceQuery(
              LocalDate.of(2026, 1, 1),
              LocalDate.of(2026, 1, 2),
              "UTC",
              PerformanceQuery.Dimension.PLATFORM,
              null,
              0,
              50);
      assertThat(
              new PerformanceReadRepository(new JdbcTemplate(datasource))
                  .read(UUID.randomUUID(), UUID.randomUUID(), query))
          .isEmpty();
      verify(statement).executeQuery();
    } finally {
      if (transactionSeconds > 0) TransactionSynchronizationManager.unbindResource(datasource);
    }
  }
}
