package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "generation_reservations")
public class GenerationReservation extends BaseEntity {
  public enum State {
    RESERVED,
    HELD_UNKNOWN,
    SETTLED,
    RELEASED
  }

  @Column(nullable = false, updatable = false, unique = true)
  private UUID requestId;

  @Column(updatable = false, unique = true)
  private UUID operationId;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, updatable = false)
  private UUID brandId;

  @Column(nullable = false, updatable = false, length = 16)
  private String kind;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @Column(nullable = false, updatable = false, length = 24)
  private String namespace;

  @Column(nullable = false, updatable = false, length = 64)
  private String policyVersion;

  @Column(nullable = false, updatable = false, length = 1024)
  private String policySnapshot;

  @Column(nullable = false, updatable = false, length = 64)
  private String quoteVersion;

  @Column(nullable = false, updatable = false, length = 24)
  private String unit;

  @Column(nullable = false, updatable = false)
  private int scale;

  @Column(nullable = false, updatable = false)
  private Instant periodStart;

  @Column(nullable = false, updatable = false)
  private Instant periodEnd;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @Column(nullable = false, updatable = false)
  private long reserved;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private State state;

  @Column private Instant dispatchedAt;
  @Column private Integer attemptNumber;
  @Column private Instant settledAt;
  @Column private Long actual;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private GenerationUsage.Origin usageOrigin;

  @Column(length = 64)
  private String usageEvidence;

  protected GenerationReservation() {}

  public GenerationReservation(
      UUID request,
      UUID operation,
      UUID actor,
      UUID workspace,
      UUID brand,
      String kind,
      String digest,
      String policy,
      String quote,
      String policySnapshot,
      Instant start,
      Instant end,
      Instant deadline,
      long upper) {
    this(
        request,
        operation,
        actor,
        workspace,
        brand,
        kind,
        digest,
        policy,
        new GenerationQuote(
            GenerationQuote.Namespace.SYNTHETIC, "SYNTHETIC_CREDIT", 0, upper, quote),
        policySnapshot,
        start,
        end,
        deadline);
  }

  public GenerationReservation(
      UUID request,
      UUID operation,
      UUID actor,
      UUID workspace,
      UUID brand,
      String kind,
      String digest,
      String policy,
      GenerationQuote quote,
      String policySnapshot,
      Instant start,
      Instant end,
      Instant deadline) {
    this.requestId = Objects.requireNonNull(request);
    this.operationId = operation;
    this.actorId = Objects.requireNonNull(actor);
    this.workspaceId = Objects.requireNonNull(workspace);
    this.brandId = Objects.requireNonNull(brand);
    if (!Set.of("DECLARED", "EVIDENCE", "STRATEGY", "CONTENT").contains(kind)
        || digest == null
        || !digest.matches("[0-9a-f]{64}")
        || policy == null
        || !policy.matches("[A-Za-z0-9._:-]{1,64}")
        || quote == null
        || !start.equals(start.truncatedTo(java.time.temporal.ChronoUnit.DAYS))
        || !end.equals(start.plusSeconds(86400))
        || deadline.isAfter(start.plusSeconds(86460))
        || !deadline.isAfter(start)) throw new IllegalArgumentException("Invalid reservation");
    this.kind = kind;
    this.requestDigest = digest;
    this.namespace = quote.namespace().name();
    this.policyVersion = policy;
    if (policySnapshot == null || !policySnapshot.matches("[a-z0-9=;:-]{1,1024}"))
      throw new IllegalArgumentException("Invalid budget policy snapshot");
    this.policySnapshot = policySnapshot;
    this.quoteVersion = quote.version();
    this.unit = quote.unit();
    this.scale = quote.scale();
    this.periodStart = start;
    this.periodEnd = end;
    this.deadline = deadline;
    this.reserved = quote.upper();
    this.state = State.RESERVED;
  }

  public void dispatch(int attempt, Instant now) {
    if (state != State.RESERVED
        || attempt < 1
        || attempt > 3
        || !now.isBefore(deadline)
        || now.isBefore(periodStart))
      throw new IllegalStateException("Reservation is not dispatchable");
    state = State.HELD_UNKNOWN;
    dispatchedAt = now;
    attemptNumber = attempt;
  }

  public void release() {
    if (state == State.RELEASED) return;
    if (state != State.RESERVED) throw new IllegalStateException("Execution cannot be excluded");
    state = State.RELEASED;
  }

  public void settle(GenerationUsage usage, Instant now) {
    if (!unit.equals(usage.unit())
        || scale != usage.scale()
        || usage.origin()
            != (namespace.equals("SYNTHETIC")
                ? GenerationUsage.Origin.SYNTHETIC
                : GenerationUsage.Origin.MEASURED))
      throw new IllegalArgumentException("Usage namespace mismatch");
    if (state == State.SETTLED) {
      if (actual != usage.amount()
          || !usageEvidence.equals(usage.evidence())
          || usageOrigin != usage.origin())
        throw new IllegalStateException("Conflicting settlement");
      return;
    }
    if (state != State.HELD_UNKNOWN || now.isBefore(dispatchedAt))
      throw new IllegalStateException("No dispatch evidence");
    state = State.SETTLED;
    actual = usage.amount();
    usageOrigin = usage.origin();
    usageEvidence = usage.evidence();
    settledAt = now;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getKind() {
    return kind;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getBrandId() {
    return brandId;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public State getState() {
    return state;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public long getReserved() {
    return reserved;
  }

  public Long getActual() {
    return actual;
  }

  public Instant getDispatchedAt() {
    return dispatchedAt;
  }

  public Integer getAttemptNumber() {
    return attemptNumber;
  }

  public String getPolicyVersion() {
    return policyVersion;
  }

  public GenerationUsage.Origin getUsageOrigin() {
    return usageOrigin;
  }
}
