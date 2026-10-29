package com.cadence.application.services;

import com.cadence.application.repositories.DurableJobRepository;
import com.cadence.domain.DurableJob;
import com.cadence.domain.JobStatus;
import com.cadence.domain.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DurableJobQueue {

  private static final List<JobStatus> CLAIMABLE_STATUSES =
      List.of(JobStatus.PENDING, JobStatus.FAILED);
  private static final int RECOVERY_BATCH_SIZE = 100;

  private final DurableJobRepository jobs;
  private final java.time.Clock clock;

  public DurableJobQueue(DurableJobRepository jobs, java.time.Clock clock) {
    this.jobs = jobs;
    this.clock = clock;
  }

  @Transactional
  public DurableJob enqueue(
      String type,
      String payloadReference,
      String idempotencyKey,
      int maxAttempts,
      Instant availableAt) {
    return jobs.findByIdempotencyKey(idempotencyKey)
        .orElseGet(
            () ->
                jobs.save(
                    new DurableJob(
                        type, payloadReference, idempotencyKey, maxAttempts, availableAt)));
  }

  @Transactional
  public Optional<DurableJob> claimNext(String workerId, Duration leaseDuration) {
    Instant now = clock.instant();
    recoverExpiredLeases(now);
    return jobs.findClaimable(CLAIMABLE_STATUSES, now, PageRequest.of(0, 1)).stream()
        .findFirst()
        .map(
            job -> {
              job.claim(workerId, clock.instant(), leaseDuration);
              return job;
            });
  }

  @Transactional
  public void succeed(UUID jobId, String workerId, int expectedAttempt) {
    DurableJob job =
        jobs.findLockedById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    job.succeed(workerId, expectedAttempt, clock.instant());
  }

  @Transactional
  public void fail(
      UUID jobId,
      String workerId,
      int expectedAttempt,
      String safeError,
      RetryPolicy retryPolicy,
      boolean permanent) {
    DurableJob job =
        jobs.findLockedById(jobId).orElseThrow(() -> new IllegalArgumentException("Job not found"));
    job.fail(workerId, expectedAttempt, clock.instant(), safeError, retryPolicy, permanent);
  }

  private void recoverExpiredLeases(Instant now) {
    jobs.findExpiredLeases(JobStatus.RUNNING, now, PageRequest.of(0, RECOVERY_BATCH_SIZE))
        .forEach(job -> job.recoverExpiredLease(now));
  }
}
