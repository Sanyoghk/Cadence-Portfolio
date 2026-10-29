package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A durable reservation owns one never-reused object key. Tombstones are never removed. */
@Entity
@Table(name = "quarantine_objects")
public class QuarantineObject {
  public enum State {
    RESERVED,
    REFERENCED,
    DELETING,
    TOMBSTONED
  }

  @Id private UUID id;

  @Version
  @Column(name = "lock_version", nullable = false)
  private Long version;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "brand_id", nullable = false, updatable = false)
  private UUID brandId;

  @Column(name = "object_key", nullable = false, updatable = false, unique = true, length = 400)
  private String objectKey;

  @Column(name = "reserved_bytes", nullable = false, updatable = false)
  private long reservedBytes;

  @Column(name = "retained_bytes", nullable = false)
  private long retainedBytes;

  @Column(name = "reserved_at", nullable = false, updatable = false)
  private Instant reservedAt;

  @Column(name = "write_deadline", nullable = false, updatable = false)
  private Instant writeDeadline;

  @Column(name = "orphan_after", nullable = false, updatable = false)
  private Instant orphanAfter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private State state;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  @Column(name = "referenced_at")
  private Instant referencedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "release_after")
  private Instant releaseAfter;

  @Column(name = "next_cleanup_at", nullable = false)
  private Instant nextCleanupAt;

  @Column(name = "quota_released", nullable = false)
  private boolean quotaReleased;

  @Column(name = "cleanup_attempts", nullable = false)
  private long cleanupAttempts;

  protected QuarantineObject() {}

  public QuarantineObject(
      UUID workspaceId,
      UUID brandId,
      long bytes,
      String extension,
      Instant now,
      Duration writeDeadline,
      Duration orphanRetention) {
    if (workspaceId == null
        || brandId == null
        || bytes < 1
        || bytes > 10 * 1024 * 1024
        || !Set.of("pdf", "png", "jpg", "html", "txt").contains(extension)
        || now == null
        || writeDeadline == null
        || writeDeadline.isNegative()
        || writeDeadline.isZero()
        || orphanRetention == null
        || orphanRetention.isNegative()
        || orphanRetention.isZero())
      throw new IllegalArgumentException("Invalid quarantine reservation");
    id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.brandId = brandId;
    objectKey = "quarantine/writes/" + workspaceId + "/" + id + "." + extension;
    reservedBytes = bytes;
    retainedBytes = bytes;
    reservedAt = now;
    this.writeDeadline = now.plus(writeDeadline);
    orphanAfter = this.writeDeadline.plus(orphanRetention);
    nextCleanupAt = orphanAfter;
    state = State.RESERVED;
  }

  public void requireWritable(Instant now) {
    if (state != State.RESERVED
        || now == null
        || now.isBefore(reservedAt)
        || !now.isBefore(writeDeadline))
      throw new IllegalStateException("Quarantine reservation is no longer writable");
  }

  public void reference(UUID evidenceId, long actualBytes, Instant now) {
    requireWritable(now);
    if (evidenceId == null || actualBytes < 1 || actualBytes > reservedBytes)
      throw new IllegalArgumentException("Invalid quarantine object reference");
    this.evidenceId = evidenceId;
    referencedAt = now;
    retainedBytes = actualBytes;
    state = State.REFERENCED;
  }

  /** Fences all future publication before any external deletion starts. */
  public void beginDeletion(Instant now, Duration retryDelay) {
    if (state == State.REFERENCED
        || now == null
        || now.isBefore(nextCleanupAt)
        || (state == State.RESERVED && now.isBefore(orphanAfter)))
      throw new IllegalStateException("Quarantine object is not eligible for cleanup");
    if (state == State.RESERVED) state = State.DELETING;
    cleanupAttempts = Math.addExact(cleanupAttempts, 1);
    nextCleanupAt = now.plus(retryDelay);
  }

  /** Repeat deletion after the drain window before releasing the reservation's quota. */
  public void deletionConfirmed(Instant now, Duration drain, Duration sweep) {
    if (!(state == State.DELETING || state == State.TOMBSTONED) || now == null)
      throw new IllegalStateException("Quarantine object has no cleanup claim");
    if (deletedAt == null) {
      deletedAt = now;
      releaseAfter = now.plus(drain);
    } else if (!now.isBefore(releaseAfter)) {
      quotaReleased = true;
    }
    state = State.TOMBSTONED;
    nextCleanupAt = quotaReleased ? now.plus(sweep) : releaseAfter;
  }

  public UUID getId() {
    return id;
  }

  public long getLockVersion() {
    if (version == null) throw new IllegalStateException("Quarantine object is not persisted");
    return version;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getBrandId() {
    return brandId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public String reference() {
    return "quarantine:" + objectKey;
  }

  public long getReservedBytes() {
    return reservedBytes;
  }

  public long getRetainedBytes() {
    return retainedBytes;
  }

  public Instant getReservedAt() {
    return reservedAt;
  }

  public Instant getWriteDeadline() {
    return writeDeadline;
  }

  public Instant getOrphanAfter() {
    return orphanAfter;
  }

  public State getState() {
    return state;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public Instant getReferencedAt() {
    return referencedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Instant getReleaseAfter() {
    return releaseAfter;
  }

  public Instant getNextCleanupAt() {
    return nextCleanupAt;
  }

  public boolean isQuotaReleased() {
    return quotaReleased;
  }

  public long getCleanupAttempts() {
    return cleanupAttempts;
  }
}
