package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record CreatorReportDetails(
    UUID id,
    UUID jobId,
    String type,
    JobStatus status,
    int attempts,
    UUID brandId,
    UUID strategyId,
    UUID capturedBy,
    Instant capturedAt,
    Instant deadline,
    Instant downloadExpiresAt,
    String snapshotChecksum,
    String artifactChecksum,
    String errorCode,
    String resultState,
    String resultPath,
    String downloadPath) {
  public static CreatorReportDetails from(CreatorReport r, Instant now) {
    var j = r.getJob();
    boolean available = j.getStatus() == JobStatus.SUCCEEDED && now.isBefore(r.getExpiresAt());
    var state = !now.isBefore(r.getExpiresAt()) ? "EXPIRED" : available ? "AVAILABLE" : "NOT_READY";
    String error = null;
    if (j.getStatus() == JobStatus.FAILED || j.getStatus() == JobStatus.DEAD)
      error =
          java.util.Set.of(
                      "REPORT_SOURCE_UNAVAILABLE",
                      "REPORT_OUTPUT_INVALID",
                      "REPORT_RENDER_FAILED",
                      "REPORT_DEADLINE_EXCEEDED")
                  .contains(j.getLastError() == null ? "" : j.getLastError())
              ? j.getLastError()
              : "REPORT_FAILED";
    return new CreatorReportDetails(
        r.getId(),
        j.getId(),
        "CREATOR_REPORT",
        j.getStatus(),
        j.getAttempts(),
        r.getBrand().getId(),
        r.getStrategy().getId(),
        r.getActorId(),
        r.getCapturedAt(),
        r.getDeadline(),
        r.getExpiresAt(),
        r.getSnapshotChecksum(),
        r.getArtifactChecksum(),
        error,
        state,
        available ? "/api/v1/reports/" + r.getId() + "/result" : null,
        available ? "/api/v1/reports/" + r.getId() + "/download" : null);
  }
}
