package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "jobs")
public class DurableJob extends BaseEntity {

  private static final int MAX_ERROR_LENGTH = 1000;

  @Column(nullable = false, length = 100)
  private String type;

  @Column(name = "payload_reference", nullable = false, length = 500)
  private String payloadReference;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
  private String idempotencyKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private JobStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "next_attempt_at", nullable = false)
  private Instant nextAttemptAt;

  @Column(name = "lease_owner", length = 255)
  private String leaseOwner;

  @Column(name = "lease_expires_at")
  private Instant leaseExpiresAt;

  @Column(name = "last_error", length = MAX_ERROR_LENGTH)
  private String lastError;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected DurableJob() {}

  public DurableJob(
      String type,
      String payloadReference,
      String idempotencyKey,
      int maxAttempts,
      Instant availableAt) {
    this.type = requireText(type, "type");
    this.payloadReference = requireText(payloadReference, "payloadReference");
    this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    if (maxAttempts < 1) {
      throw new IllegalArgumentException("maxAttempts must be at least 1");
    }
    this.maxAttempts = maxAttempts;
    this.nextAttemptAt = requireNonNull(availableAt, "availableAt");
    this.status = JobStatus.PENDING;
  }

  public void claim(String workerId, Instant now, Duration leaseDuration) {
    workerId = requireText(workerId, "workerId");
    requireNonNull(now, "now");
    requireNonNull(leaseDuration, "leaseDuration");
    if (leaseDuration.isZero() || leaseDuration.isNegative()) {
      throw new IllegalArgumentException("leaseDuration must be positive");
    }
    if ((status != JobStatus.PENDING && status != JobStatus.FAILED) || nextAttemptAt.isAfter(now)) {
      throw new IllegalStateException("Job is not ready to be claimed");
    }
    status = JobStatus.RUNNING;
    attempts++;
    leaseOwner = workerId;
    leaseExpiresAt = now.plus(leaseDuration);
    lastError = null;
  }

  public void bindGeneration(java.util.UUID operationId) {
    if (!"AI_GENERATION".equals(type)
        || status != JobStatus.PENDING
        || attempts != 0
        || !"generation:pending".equals(payloadReference)
        || operationId == null) throw new IllegalStateException("Invalid generation binding");
    payloadReference = "generation:" + operationId;
  }

  public void bindExport(java.util.UUID exportId) {
    if (!"CSV_EXPORT".equals(type)
        || status != JobStatus.PENDING
        || attempts != 0
        || !"csv-export:pending".equals(payloadReference)
        || exportId == null) throw new IllegalStateException("Invalid export binding");
    payloadReference = "csv-export:" + exportId;
  }

  public void bindMetrics(java.util.UUID importId) {
    if (!"METRICS_IMPORT".equals(type)
        || status != JobStatus.PENDING
        || attempts != 0
        || !"metrics:pending".equals(payloadReference)
        || importId == null) throw new IllegalStateException("Invalid metrics binding");
    payloadReference = "metrics:" + importId;
  }

  public void bindReport(java.util.UUID reportId) {
    if (!"CREATOR_REPORT".equals(type)
        || status != JobStatus.PENDING
        || attempts != 0
        || !"report:pending".equals(payloadReference)
        || reportId == null) throw new IllegalStateException("Invalid report binding");
    payloadReference = "report:" + reportId;
  }

  public void succeed(String workerId, int expectedAttempt, Instant now) {
    requireLeaseOwner(workerId, expectedAttempt, now);
    status = JobStatus.SUCCEEDED;
    completedAt = requireNonNull(now, "now");
    clearLease();
  }

  public void fail(
      String workerId,
      int expectedAttempt,
      Instant now,
      String safeError,
      RetryPolicy retryPolicy,
      boolean permanent) {
    requireLeaseOwner(workerId, expectedAttempt, now);
    requireNonNull(now, "now");
    requireNonNull(retryPolicy, "retryPolicy");
    lastError = sanitizeError(safeError);
    clearLease();
    if (permanent || attempts >= maxAttempts) {
      status = JobStatus.DEAD;
      completedAt = now;
      return;
    }
    status = JobStatus.FAILED;
    nextAttemptAt = now.plus(retryPolicy.delayForAttempt(attempts));
  }

  public void recoverExpiredLease(Instant now) {
    requireNonNull(now, "now");
    if (status != JobStatus.RUNNING || leaseExpiresAt == null || leaseExpiresAt.isAfter(now)) {
      throw new IllegalStateException("Job lease has not expired");
    }
    lastError = "Worker lease expired";
    clearLease();
    if (attempts >= maxAttempts) {
      status = JobStatus.DEAD;
      completedAt = now;
    } else {
      status = JobStatus.FAILED;
      nextAttemptAt = now;
    }
  }

  /** Internal AI operation recovery; callers hold parent/operation/job locks. */
  public void terminateAi(Instant now) {
    if (!"AI_GENERATION".equals(type) || status == JobStatus.SUCCEEDED || status == JobStatus.DEAD)
      throw new IllegalStateException("Invalid AI job termination");
    status = JobStatus.DEAD;
    completedAt = requireNonNull(now, "now");
    lastError = "Generation terminated";
    clearLease();
  }

  private void requireLeaseOwner(String workerId, int expectedAttempt, Instant now) {
    workerId = requireText(workerId, "workerId");
    requireNonNull(now, "now");
    if (status != JobStatus.RUNNING
        || !workerId.equals(leaseOwner)
        || expectedAttempt != attempts
        || leaseExpiresAt == null
        || !leaseExpiresAt.isAfter(now)) {
      throw new IllegalStateException("Job is not leased by this worker");
    }
  }

  private void clearLease() {
    leaseOwner = null;
    leaseExpiresAt = null;
  }

  private static String sanitizeError(String value) {
    if (value == null || value.isBlank()) {
      return "Job failed";
    }
    String singleLine = value.replaceAll("[\\r\\n\\t]+", " ").trim();
    return singleLine.substring(0, Math.min(singleLine.length(), MAX_ERROR_LENGTH));
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }

  private static <T> T requireNonNull(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
    return value;
  }

  public String getType() {
    return type;
  }

  public String getPayloadReference() {
    return payloadReference;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public JobStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public Instant getLeaseExpiresAt() {
    return leaseExpiresAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
