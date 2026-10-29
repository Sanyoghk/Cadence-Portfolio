package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Durable immutable request/source binding; scheduling remains in DurableJob. */
@Entity
@Table(
    name = "ai_generation_operations",
    uniqueConstraints =
        @UniqueConstraint(columnNames = {"actor_id", "workspace_id", "kind", "key_digest"}))
public class AiGenerationOperation extends BaseEntity {
  public enum Kind {
    DECLARED,
    EVIDENCE,
    STRATEGY,
    CONTENT
  }

  public enum Status {
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    SUPERSEDED,
    FAILED,
    EXPIRED,
    OUTCOME_UNKNOWN
  }

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, updatable = false)
  private UUID brandId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private Kind kind;

  @Column(nullable = false, updatable = false, length = 64)
  private String keyDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String snapshotChecksum;

  @Column(nullable = false, updatable = false, length = 65536)
  private String snapshot;

  @Column(nullable = false, updatable = false)
  private Instant submittedAt;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @OneToOne(optional = false)
  @JoinColumn(name = "job_id", nullable = false, updatable = false, unique = true)
  private DurableJob job;

  @ElementCollection
  @CollectionTable(name = "ai_generation_sources", joinColumns = @JoinColumn(name = "operation_id"))
  @Column(name = "admission_id", nullable = false)
  private Set<UUID> admissions = new HashSet<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column private UUID resultId;

  @Column(length = 65536)
  private String resultJson;

  @Column private Instant completedAt;

  protected AiGenerationOperation() {}

  public AiGenerationOperation(
      UUID actor,
      UUID workspace,
      UUID brand,
      Kind kind,
      String key,
      String request,
      String snapshot,
      Instant now,
      DurableJob job,
      Set<UUID> admissions) {
    this.actorId = Objects.requireNonNull(actor);
    this.workspaceId = Objects.requireNonNull(workspace);
    this.brandId = Objects.requireNonNull(brand);
    this.kind = Objects.requireNonNull(kind);
    this.keyDigest = digest(key);
    this.requestDigest = digest(request);
    this.snapshot = text(snapshot);
    this.snapshotChecksum = BrandAnalysisAttempt.checksum(snapshot);
    this.submittedAt = Objects.requireNonNull(now);
    this.deadline = now.plusSeconds(60);
    this.job = Objects.requireNonNull(job);
    this.admissions = Set.copyOf(admissions);
    this.status = Status.QUEUED;
    if (admissions.size() > 5) throw new IllegalArgumentException("Too many sources");
  }

  private static String digest(String s) {
    if (s == null || !s.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid digest");
    return s;
  }

  private static String text(String s) {
    if (s == null
        || s.isBlank()
        || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
      throw new IllegalArgumentException("Invalid generation snapshot");
    return s;
  }

  public boolean terminal() {
    return status != Status.QUEUED && status != Status.RUNNING && status != Status.RETRY_WAIT;
  }

  public void running() {
    if (terminal()) throw new IllegalStateException("Generation terminal");
    status = Status.RUNNING;
  }

  public void retry() {
    if (status != Status.RUNNING) throw new IllegalStateException("Generation not running");
    status = Status.RETRY_WAIT;
  }

  public void finish(Status next, Instant now) {
    if (terminal()
        || next == Status.QUEUED
        || next == Status.RUNNING
        || next == Status.RETRY_WAIT
        || next == Status.SUCCEEDED) throw new IllegalStateException("Invalid completion");
    status = next;
    completedAt = now;
  }

  public void succeed(UUID result, String json, Instant now) {
    if (status != Status.RUNNING || !now.isBefore(deadline))
      throw new IllegalStateException("Generation not current");
    resultId = Objects.requireNonNull(result);
    resultJson = text(json);
    status = Status.SUCCEEDED;
    completedAt = now;
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

  public Kind getKind() {
    return kind;
  }

  public String getKeyDigest() {
    return keyDigest;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public String getSnapshotChecksum() {
    return snapshotChecksum;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public DurableJob getJob() {
    return job;
  }

  public Set<UUID> getAdmissions() {
    return Set.copyOf(admissions);
  }

  public Status getStatus() {
    return status;
  }

  public UUID getResultId() {
    return resultId;
  }

  public String getResultJson() {
    return resultJson;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
