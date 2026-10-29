package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;

public record MetricImportDetails(
    UUID id,
    UUID jobId,
    String type,
    JobStatus status,
    int attempts,
    UUID brandId,
    UUID submittedBy,
    Instant submittedAt,
    Instant deadline,
    Instant completedAt,
    String origin,
    String schemaVersion,
    String rawChecksum,
    int rowCount,
    List<UUID> observationIds,
    String errorCode) {
  public static MetricImportDetails from(MetricImport e) {
    var j = e.getJob();
    String error = null;
    if (j.getStatus() == JobStatus.DEAD || j.getStatus() == JobStatus.FAILED)
      error =
          Set.of("METRICS_INPUT_UNAVAILABLE", "METRICS_RETRYABLE", "METRICS_DEADLINE_EXCEEDED")
                  .contains(Objects.toString(j.getLastError(), ""))
              ? j.getLastError()
              : j.getStatus() == JobStatus.DEAD
                  ? "METRICS_LEASE_EXHAUSTED"
                  : "METRICS_LEASE_EXPIRED";
    return new MetricImportDetails(
        e.getId(),
        j.getId(),
        "METRICS_IMPORT",
        j.getStatus(),
        j.getAttempts(),
        e.getBrand().getId(),
        e.getActorId(),
        e.getSubmittedAt(),
        e.getDeadline(),
        e.getCompletedAt(),
        e.getOrigin(),
        "metric-interval-v1",
        e.getRawChecksum(),
        e.getRowCount(),
        e.getResults(),
        error);
  }
}
