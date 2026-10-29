package com.cadence.infrastructure.persistence;

import java.sql.SQLException;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.springframework.stereotype.Component;

/** Allows V14's provenance backfill without changing an already published migration checksum. */
@Component
public class LegacyScanReceiptMigrationCallback implements Callback {
  @Override
  public boolean supports(Event event, Context context) {
    return (event == Event.BEFORE_EACH_MIGRATE || event == Event.AFTER_EACH_MIGRATE)
        && context.getMigrationInfo() != null
        && MigrationVersion.fromVersion("14").equals(context.getMigrationInfo().getVersion());
  }

  @Override
  public boolean canHandleInTransaction(Event event, Context context) {
    return true;
  }

  @Override
  public void handle(Event event, Context context) {
    try {
      var connection = context.getConnection();
      if (connection.getAutoCommit()
          || !connection.getMetaData().getDatabaseProductName().equals("PostgreSQL")) {
        throw new FlywayException("V14 receipt backfill requires a PostgreSQL transaction");
      }
      // ALTER TABLE holds an exclusive table lock until commit. Neither another writer nor a
      // failed migration can observe receipts with their append-only protection disabled.
      try (var statement = connection.createStatement()) {
        statement.execute(
            "ALTER TABLE evidence_scan_results "
                + (event == Event.BEFORE_EACH_MIGRATE ? "DISABLE" : "ENABLE")
                + " TRIGGER evidence_scan_result_guard");
      }
    } catch (SQLException exception) {
      throw new FlywayException("Cannot protect legacy scan receipt migration", exception);
    }
  }

  @Override
  public String getCallbackName() {
    return "legacy-scan-receipt-v14";
  }
}
