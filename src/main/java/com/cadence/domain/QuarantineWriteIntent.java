package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Durable record committed before external FILE storage I/O. It is not permission to delete. */
@Entity
@Table(name = "quarantine_write_intents")
public class QuarantineWriteIntent {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "brand_id", nullable = false, updatable = false)
  private UUID brandId;

  @Column(name = "object_key", nullable = false, updatable = false, length = 400)
  private String objectKey;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "registered_at")
  private Instant registeredAt;

  @Column(name = "evidence_id")
  private UUID evidenceId;

  protected QuarantineWriteIntent() {}

  public QuarantineWriteIntent(UUID brandId, String objectKey, Instant startedAt) {
    if (brandId == null
        || objectKey == null
        || !(objectKey.matches("quarantine/" + brandId + "/[0-9a-f]{64}[.](?:pdf|png|jpg)")
            || objectKey.matches("quarantine/writes/[0-9a-f-]{36}/[0-9a-f-]{36}[.](?:pdf|png|jpg)"))
        || startedAt == null) throw new IllegalArgumentException("Invalid quarantine write intent");
    this.brandId = brandId;
    this.objectKey = objectKey;
    this.startedAt = startedAt;
  }

  public void registered(UUID evidenceId, Instant now) {
    if (this.evidenceId != null || evidenceId == null || now == null || now.isBefore(startedAt))
      throw new IllegalStateException("Invalid quarantine registration");
    this.evidenceId = evidenceId;
    this.registeredAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getBrandId() {
    return brandId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }
}
