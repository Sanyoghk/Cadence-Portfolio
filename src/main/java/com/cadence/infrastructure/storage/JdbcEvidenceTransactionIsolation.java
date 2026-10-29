package com.cadence.infrastructure.storage;

import com.cadence.application.ports.EvidenceTransactionIsolation;
import java.sql.Connection;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Checks the actual transaction-bound JDBC connection, including an inherited default level. */
@Component
public final class JdbcEvidenceTransactionIsolation implements EvidenceTransactionIsolation {
  private final JdbcTemplate jdbc;

  public JdbcEvidenceTransactionIsolation(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void requireReadCommitted() {
    jdbc.execute(
        (ConnectionCallback<Void>)
            connection -> {
              if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED)
                throw new IllegalStateException(
                    "Evidence operation requires READ_COMMITTED isolation");
              return null;
            });
  }
}
