package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "ai_generation_attempts",
    uniqueConstraints = @UniqueConstraint(columnNames = {"operation_id", "attempt_number"}))
public class AiGenerationAttempt extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private int attemptNumber;

  @Column(nullable = false, updatable = false, length = 255)
  private String workerId;

  @Column(nullable = false, updatable = false)
  private Instant startedAt;

  @Column(nullable = false, updatable = false)
  private Instant leaseExpiresAt;

  @Column(nullable = false, updatable = false)
  private short completionSchema = 1;

  @Column private Instant dispatchedAt;
  @Column private UUID analysisId;
  @Column private Instant finishedAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private Outcome outcome;

  @Enumerated(EnumType.STRING)
  @Column(length = 40)
  private Reason reason;

  public enum Outcome {
    SUCCEEDED,
    RETRY_SAFE,
    SUPERSEDED,
    FAILED,
    EXPIRED,
    OUTCOME_UNKNOWN
  }

  public enum Reason {
    COMPLETED,
    LEASE_EXPIRED_BEFORE_DISPATCH,
    INPUT_UNAVAILABLE,
    SOURCE_OR_ACCESS_CHANGED,
    INVALID_OUTPUT,
    DEADLINE_EXPIRED,
    PROVIDER_OUTCOME_UNKNOWN,
    CAPABILITY_UNAVAILABLE,
    BUDGET_EXCEEDED,
    PUBLICATION_REJECTED
  }

  public void finish(Outcome outcome, Reason reason, Instant now) {
    if (finishedAt != null
        || outcome == null
        || reason == null
        || now == null
        || now.isBefore(startedAt)
        || (dispatchedAt != null && now.isBefore(dispatchedAt))
        || (outcome == Outcome.RETRY_SAFE && dispatchedAt != null)
        || (outcome == Outcome.SUCCEEDED && dispatchedAt == null))
      throw new IllegalStateException("Invalid attempt completion");
    this.outcome = outcome;
    this.reason = reason;
    this.finishedAt = now;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public Reason getReason() {
    return reason;
  }

  protected AiGenerationAttempt() {}

  public AiGenerationAttempt(
      UUID operation, int number, String worker, Instant now, Instant leaseExpiresAt) {
    if (operation == null
        || number < 1
        || number > 3
        || worker == null
        || worker.isBlank()
        || worker.length() > 255
        || now == null) throw new IllegalArgumentException("Invalid generation attempt");
    if (leaseExpiresAt == null || !leaseExpiresAt.isAfter(now))
      throw new IllegalArgumentException("Invalid lease");
    this.leaseExpiresAt = leaseExpiresAt;
    operationId = operation;
    attemptNumber = number;
    workerId = worker;
    startedAt = now;
  }

  public void dispatch(Instant now, UUID analysis) {
    if (finishedAt != null || dispatchedAt != null || now.isBefore(startedAt))
      throw new IllegalStateException("Already dispatched");
    dispatchedAt = now;
    analysisId = analysis;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public String getWorkerId() {
    return workerId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getDispatchedAt() {
    return dispatchedAt;
  }

  public UUID getAnalysisId() {
    return analysisId;
  }
}
