package com.cadence.infrastructure.storage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Database-authoritative UTC time for quarantine leases and append-only scan observations. */
@Component
public final class DatabaseEvidenceClock extends Clock {
  private final JdbcTemplate jdbc;

  public DatabaseEvidenceClock(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    if (!ZoneOffset.UTC.equals(zone))
      throw new IllegalArgumentException("Evidence clock requires UTC");
    return this;
  }

  @Override
  public Instant instant() {
    return jdbc.execute(
        (org.springframework.jdbc.core.ConnectionCallback<Instant>)
            connection -> {
              boolean postgres =
                  connection.getMetaData().getDatabaseProductName().equals("PostgreSQL");
              try (var statement =
                  connection.prepareStatement(
                      postgres ? "select clock_timestamp()" : "select current_timestamp")) {
                statement.setQueryTimeout(3);
                try (var result = statement.executeQuery()) {
                  if (!result.next()) throw new IllegalStateException("Evidence clock unavailable");
                  return result.getTimestamp(1).toInstant();
                }
              }
            });
  }
}
