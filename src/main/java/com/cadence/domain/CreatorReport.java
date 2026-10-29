package com.cadence.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "creator_reports")
public class CreatorReport extends BaseEntity {
  public static final int MAX_BYTES = 2097152;
  public static final int MAX_SNAPSHOT_BYTES = 1048576;

  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", updatable = false)
  private Brand brand;

  @OneToOne(optional = false)
  @JoinColumn(name = "job_id", unique = true, updatable = false)
  private DurableJob job;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @ManyToOne(optional = false)
  @JoinColumn(name = "strategy_id", updatable = false)
  private Strategy strategy;

  @Column(nullable = false, updatable = false)
  private long sourceVersion;

  @Column(nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(nullable = false, updatable = false, length = 64)
  private String keyDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @Column(nullable = false, updatable = false, length = MAX_SNAPSHOT_BYTES)
  private String snapshot;

  @Column(nullable = false, updatable = false, length = 64)
  private String snapshotChecksum;

  @Column(nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(length = MAX_BYTES)
  private byte[] artifact;

  @Column(length = 64)
  private String artifactChecksum;

  private Integer publishedAttempt;

  @Column(length = 255)
  private String publishedWorker;

  private Instant completedAt;

  protected CreatorReport() {}

  public CreatorReport(
      Brand brand,
      Strategy strategy,
      DurableJob job,
      UUID actor,
      String key,
      String digest,
      String snapshot,
      String sourceChecksum,
      Instant now) {
    if (brand == null
        || strategy == null
        || job == null
        || actor == null
        || snapshot == null
        || snapshot.getBytes(StandardCharsets.UTF_8).length > MAX_SNAPSHOT_BYTES
        || now == null
        || !strategy.getBrand().getId().equals(brand.getId()))
      throw new IllegalArgumentException("Invalid report capture");
    this.brand = brand;
    this.strategy = strategy;
    this.job = job;
    actorId = actor;
    workspaceId = brand.getWorkspace().getId();
    sourceVersion = strategy.getLockVersion();
    this.sourceChecksum = sourceChecksum;
    keyDigest = key;
    requestDigest = digest;
    this.snapshot = snapshot;
    snapshotChecksum = hash(snapshot.getBytes(StandardCharsets.UTF_8));
    capturedAt = now;
    deadline = now.plusSeconds(300);
    expiresAt = now.plusSeconds(86400);
  }

  public static String hash(byte[] b) {
    return MetricRow.sha(b);
  }

  public void requireLease(String worker, int attempt, Instant now) {
    if (job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() != attempt
        || !Objects.equals(job.getLeaseOwner(), worker)
        || job.getLeaseExpiresAt() == null
        || !job.getLeaseExpiresAt().isAfter(now)
        || !now.isBefore(deadline)) throw new IllegalStateException("Report publication fenced");
  }

  public void publish(byte[] bytes, String worker, int attempt, Instant now) {
    requireLease(worker, attempt, now);
    if (artifact != null) throw new IllegalStateException("Report already published");
    if (bytes == null
        || bytes.length == 0
        || bytes.length > MAX_BYTES - snapshot.getBytes(StandardCharsets.UTF_8).length)
      throw new CreatorReportOutputException();
    artifact = bytes.clone();
    artifactChecksum = hash(bytes);
    publishedWorker = worker;
    publishedAttempt = attempt;
    completedAt = now;
  }

  public void requireResult(Instant now) {
    if (job.getStatus() != JobStatus.SUCCEEDED
        || artifact == null
        || !now.isBefore(expiresAt)
        || !hash(artifact).equals(artifactChecksum))
      throw new IllegalStateException("Report result unavailable or expired");
  }

  public byte[] bytes(Instant now) {
    requireResult(now);
    return artifact.clone();
  }

  public Brand getBrand() {
    return brand;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public DurableJob getJob() {
    return job;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
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

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public String getArtifactChecksum() {
    return artifactChecksum;
  }
}
