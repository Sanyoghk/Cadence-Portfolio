package com.cadence.application.services;

import java.util.UUID;
import org.slf4j.LoggerFactory;

/**
 * Diagnostic categories, not authoritative outcomes or a retry-safety promise. Never log causes.
 */
public final class WorkerFailureDiagnostics {
  public enum Kind {
    METRIC_IMPORT,
    CSV_EXPORT,
    CREATOR_REPORT
  }

  public enum Category {
    CORRECTION_CONFLICT,
    SNAPSHOT_CHANGED,
    LEASE_OR_DEADLINE,
    INVALID_METRIC_SOURCE,
    INVALID_PUBLICATION,
    RETAINED_QUOTA,
    DATABASE_TRANSIENT,
    DATABASE_REJECTED,
    DATABASE_METRICS_CAPTURE,
    DATABASE_METRICS_SOURCE,
    DATABASE_METRICS_CHECKSUM,
    DATABASE_METRICS_PUBLICATION,
    DATABASE_METRICS_RESULT,
    DATABASE_METRICS_SEAL,
    ACCESS_DENIED,
    INPUT_REJECTED,
    STATE_REJECTED,
    UNAVAILABLE
  }

  private WorkerFailureDiagnostics() {}

  public static void rejected(Kind kind, UUID operation, int attempt, RuntimeException failure) {
    LoggerFactory.getLogger(WorkerFailureDiagnostics.class)
        .warn(
            "worker_attempt_rejected kind={} operation={} attempt={} category={} type={}",
            kind,
            operation,
            attempt,
            category(failure),
            failure.getClass().getSimpleName());
  }

  static Category category(RuntimeException failure) {
    if (failure instanceof org.springframework.security.access.AccessDeniedException)
      return Category.ACCESS_DENIED;
    if (failure instanceof org.springframework.dao.TransientDataAccessException)
      return Category.DATABASE_TRANSIENT;
    if (failure instanceof org.springframework.dao.DataAccessException) {
      Throwable current = failure;
      for (int depth = 0; current != null && depth < 8; depth++, current = current.getCause()) {
        String message = current.getMessage();
        if (message == null) continue;
        // Only fixed diagnostics are emitted. Never emit a database message or user row.
        if (message.contains("Invalid metrics capture or quota"))
          return Category.DATABASE_METRICS_CAPTURE;
        if (message.contains("Metrics source or lease unavailable"))
          return Category.DATABASE_METRICS_SOURCE;
        if (message.contains("Invalid metric checksum")) return Category.DATABASE_METRICS_CHECKSUM;
        if (message.contains("Metrics worker fenced")) return Category.DATABASE_METRICS_PUBLICATION;
        if (message.contains("Invalid metrics result")) return Category.DATABASE_METRICS_RESULT;
        if (message.contains("Metrics publication incoherent")
            || message.contains("Metrics observation without publication"))
          return Category.DATABASE_METRICS_SEAL;
      }
      return Category.DATABASE_REJECTED;
    }
    if (failure instanceof IllegalStateException)
      return switch (failure.getMessage() == null ? "" : failure.getMessage()) {
        case "Metrics correction conflict" -> Category.CORRECTION_CONFLICT;
        case "Metrics snapshot changed" -> Category.SNAPSHOT_CHANGED;
        case "Metrics lease or deadline expired" -> Category.LEASE_OR_DEADLINE;
        case "Invalid metric source or correction" -> Category.INVALID_METRIC_SOURCE;
        case "Invalid metric publication" -> Category.INVALID_PUBLICATION;
        case "Retained metrics quota reached" -> Category.RETAINED_QUOTA;
        default -> Category.STATE_REJECTED;
      };
    return failure instanceof IllegalArgumentException
        ? Category.INPUT_REJECTED
        : Category.UNAVAILABLE;
  }
}
