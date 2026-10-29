package com.cadence.presentation.dto;

import com.cadence.domain.DurableJob;
import com.cadence.domain.JobStatus;
import java.time.Instant;
import java.util.UUID;

/** Public progress metadata only: persisted worker errors and payloads are never serialized. */
public record JobResponse(
    UUID id,
    JobStatus status,
    int attempts,
    int maxAttempts,
    Instant nextAttemptAt,
    Instant completedAt) {
  public static JobResponse from(DurableJob job) {
    boolean waiting = job.getStatus() == JobStatus.PENDING || job.getStatus() == JobStatus.FAILED;
    return new JobResponse(
        job.getId(),
        job.getStatus(),
        job.getAttempts(),
        job.getMaxAttempts(),
        waiting ? job.getNextAttemptAt() : null,
        job.getCompletedAt());
  }
}
