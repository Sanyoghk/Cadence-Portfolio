package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record CsvExportDetails(
    UUID id,
    UUID jobId,
    String type,
    JobStatus status,
    int attempts,
    UUID brandId,
    UUID capturedBy,
    Instant capturedAt,
    Instant deadline,
    Instant downloadExpiresAt,
    int rowCount,
    String snapshotChecksum,
    String artifactChecksum,
    String errorCode,
    String downloadPath) {
  public static CsvExportDetails from(CsvExportBatch e) {
    var j = e.getJob();
    return new CsvExportDetails(
        e.getId(),
        j.getId(),
        "CSV_EXPORT",
        j.getStatus(),
        j.getAttempts(),
        e.getBrand().getId(),
        e.getActorId(),
        e.getCapturedAt(),
        e.getDeadline(),
        e.getExpiresAt(),
        e.getItems().size(),
        e.getSnapshotChecksum(),
        e.getArtifactChecksum(),
        j.getStatus() == JobStatus.FAILED || j.getStatus() == JobStatus.DEAD
            ? safeError(j.getLastError())
            : null,
        j.getStatus() == JobStatus.SUCCEEDED ? "/api/v1/exports/" + e.getId() + "/download" : null);
  }

  private static String safeError(String code) {
    if ("Worker lease expired".equals(code)) return "CSV_LEASE_EXHAUSTED";
    return java.util.Set.of(
                "CSV_INPUT_UNAVAILABLE", "CSV_SOURCE_UNAVAILABLE", "CSV_DEADLINE_EXCEEDED")
            .contains(code == null ? "" : code)
        ? code
        : "CSV_FAILED";
  }
}
