package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

class DatabaseEvidenceClockTest {
  @Test
  void usesDatabaseTimeAndDoesNotUseHostClock() throws Exception {
    for (String database : java.util.List.of("PostgreSQL", "H2")) {
      JdbcTemplate jdbc = mock(JdbcTemplate.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metadata = mock(DatabaseMetaData.class);
      PreparedStatement statement = mock(PreparedStatement.class);
      ResultSet result = mock(ResultSet.class);
      when(connection.getMetaData()).thenReturn(metadata);
      when(metadata.getDatabaseProductName()).thenReturn(database);
      when(connection.prepareStatement(anyString())).thenReturn(statement);
      when(statement.executeQuery()).thenReturn(result);
      when(result.next()).thenReturn(true);
      Instant expected = Instant.parse("2024-01-01T00:00:00Z");
      when(result.getTimestamp(1)).thenReturn(Timestamp.from(expected));
      when(jdbc.execute(org.mockito.ArgumentMatchers.<ConnectionCallback<Instant>>any()))
          .thenAnswer(
              invocation ->
                  ((ConnectionCallback<Instant>) invocation.getArgument(0))
                      .doInConnection(connection));
      var clock = new DatabaseEvidenceClock(jdbc);
      assertThat(clock.instant()).isEqualTo(expected);
      assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
      assertThat(clock.withZone(ZoneOffset.UTC)).isSameAs(clock);
      assertThatThrownBy(() -> clock.withZone(ZoneId.of("Europe/London")))
          .isInstanceOf(IllegalArgumentException.class);
      verify(connection)
          .prepareStatement(
              database.equals("PostgreSQL")
                  ? "select clock_timestamp()"
                  : "select current_timestamp");
      verify(statement).setQueryTimeout(3);
      verify(result).close();
      verify(statement).close();
    }
  }
}
