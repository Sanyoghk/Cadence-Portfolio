package com.cadence.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "csv_exports")
public class CsvExportBatch extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", nullable = false, updatable = false)
  private Brand brand;

  @OneToOne(optional = false)
  @JoinColumn(name = "job_id", nullable = false, unique = true, updatable = false)
  private DurableJob job;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "client_key", nullable = false, length = 128, updatable = false)
  private String clientKey;

  @Column(name = "request_digest", nullable = false, length = 64, updatable = false)
  private String requestDigest;

  @Column(name = "from_at", nullable = false, updatable = false)
  private Instant from;

  @Column(name = "until_at", nullable = false, updatable = false)
  private Instant until;

  @Column(nullable = false, length = 100, updatable = false)
  private String timezone;

  @Column(name = "captured_at", nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @ElementCollection
  @CollectionTable(name = "csv_export_items", joinColumns = @JoinColumn(name = "export_id"))
  @OrderColumn(name = "item_order")
  private List<CsvExportItem> items = new ArrayList<>();

  @Column(name = "snapshot_checksum", length = 64, nullable = false, updatable = false)
  private String snapshotChecksum;

  @JdbcTypeCode(SqlTypes.VARBINARY)
  @Column(name = "artifact", length = CsvDocument.MAX_BYTES)
  private byte[] artifact;

  @Column(name = "artifact_checksum", length = 64)
  private String artifactChecksum;

  @Column(name = "published_attempt")
  private Integer publishedAttempt;

  @Column(name = "published_worker", length = 255)
  private String publishedWorker;

  @Column(name = "completed_at")
  private Instant completedAt;

  protected CsvExportBatch() {}

  public CsvExportBatch(
      Brand brand,
      DurableJob job,
      UUID actor,
      String key,
      CsvExportRequest request,
      Instant now,
      List<CsvExportItem> items,
      String checksum) {
    if (brand == null
        || job == null
        || actor == null
        || key == null
        || !key.matches("[A-Za-z0-9_-]{16,128}")
        || now == null
        || items == null
        || items.isEmpty()
        || items.size() > 84
        || checksum == null
        || !checksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid export capture");
    this.brand = brand;
    this.job = job;
    actorId = actor;
    clientKey = key;
    requestDigest = request.digest();
    from = request.from();
    until = request.until();
    timezone = request.timezone();
    capturedAt = now;
    deadline = now.plusSeconds(300);
    expiresAt = now.plusSeconds(86400);
    this.items.addAll(items);
    snapshotChecksum = checksum;
  }

  public void publish(byte[] bytes, String worker, int attempt, Instant now) {
    if (artifact != null
        || bytes == null
        || bytes.length == 0
        || bytes.length > CsvDocument.MAX_BYTES
        || !now.isBefore(deadline)
        || job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() != attempt
        || !Objects.equals(job.getLeaseOwner(), worker)
        || job.getLeaseExpiresAt() == null
        || !job.getLeaseExpiresAt().isAfter(now))
      throw new IllegalStateException("Export publication fenced");
    artifact = bytes.clone();
    artifactChecksum = MediaObjectKey.hash(bytes);
    publishedAttempt = attempt;
    publishedWorker = worker;
    completedAt = now;
  }

  public byte[] bytes(Instant now) {
    if (job.getStatus() != JobStatus.SUCCEEDED
        || artifact == null
        || !now.isBefore(expiresAt)
        || !MediaObjectKey.hash(artifact).equals(artifactChecksum))
      throw new IllegalStateException("Export artifact unavailable or expired");
    return artifact.clone();
  }

  public Brand getBrand() {
    return brand;
  }

  public DurableJob getJob() {
    return job;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public String getTimezone() {
    return timezone;
  }

  public Instant getFrom() {
    return from;
  }

  public Instant getUntil() {
    return until;
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

  public List<CsvExportItem> getItems() {
    return List.copyOf(items);
  }

  public String getSnapshotChecksum() {
    return snapshotChecksum;
  }

  public String getArtifactChecksum() {
    return artifactChecksum;
  }
}
