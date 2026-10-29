package com.cadence.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

@Entity
@Table(name = "media_assets")
public class MediaAsset extends BaseEntity {
  public enum State {
    STAGING,
    AVAILABLE,
    REJECTED,
    ARCHIVED
  }

  public enum Moderation {
    PENDING,
    APPROVED_DEMO,
    REJECTED
  }

  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", updatable = false)
  private Brand brand;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private DemoMediaFixture fixture;

  @Column(nullable = false, updatable = false, length = 30)
  private String origin = "DEMO_FIXTURE";

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private MediaFormat format;

  @Column(nullable = false, updatable = false, length = 64)
  private String checksum;

  @Column(name = "byte_size", nullable = false, updatable = false)
  private int byteSize;

  @Column(nullable = false, updatable = false)
  private int width;

  @Column(nullable = false, updatable = false)
  private int height;

  @Column(name = "duration_millis", nullable = false, updatable = false)
  private int durationMillis;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private State status = State.STAGING;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Moderation moderation = Moderation.PENDING;

  @Enumerated(EnumType.STRING)
  @Column(name = "scan_result")
  private ScanVerdict scanResult;

  @Column(name = "scan_engine", length = 100)
  private String scanEngine;

  @Column(name = "scan_signature", length = 100)
  private String scanSignature;

  @Column(name = "scanned_at")
  private Instant scannedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "verification_failure", length = 40)
  private String verificationFailure;

  @Column(name = "reviewed_by")
  private UUID reviewedBy;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "archived_by")
  private UUID archivedBy;

  @Column(name = "archived_at")
  private Instant archivedAt;

  protected MediaAsset() {}

  public MediaAsset(Brand brand, DemoMediaFixture fixture, UUID actor, Instant now) {
    if (brand == null || fixture == null || actor == null || now == null)
      throw new IllegalArgumentException("Invalid media intent");
    this.brand = brand;
    this.fixture = fixture;
    requestedBy = actor;
    requestedAt = now;
    deadline = now.plusSeconds(120);
    format = fixture.format();
    checksum = fixture.checksum();
    byteSize = fixture.size();
    width = fixture.width();
    height = fixture.height();
    durationMillis = fixture.durationMillis();
  }

  public void complete(
      ScanVerdict verdict, ScanProvenance provenance, Instant scanTime, Instant now) {
    if (status != State.STAGING || now == null || now.isBefore(requestedAt))
      throw new IllegalStateException("Media intent changed");
    if (verdict != null) {
      if (scanTime == null || scanTime.isBefore(requestedAt) || scanTime.isAfter(now))
        throw new IllegalArgumentException("Invalid media scan time");
      if (provenance == null) throw new IllegalArgumentException("Scan provenance required");
      scanResult = verdict;
      scanEngine = provenance.engine();
      scanSignature = provenance.signatureVersion();
      scannedAt = scanTime;
    }
    completedAt = now;
    if (!now.isBefore(deadline)) {
      status = State.REJECTED;
      verificationFailure = "DEADLINE_EXCEEDED";
    } else if (verdict == null) {
      status = State.REJECTED;
      verificationFailure = "PHYSICAL_UNAVAILABLE";
    } else if (verdict != ScanVerdict.CLEAN) {
      status = State.REJECTED;
      verificationFailure = "SCAN_REJECTED";
    } else status = State.AVAILABLE;
  }

  public void review(boolean approved, UUID actor, Instant now) {
    requirePreview();
    if (moderation != Moderation.PENDING
        || actor == null
        || now == null
        || now.isBefore(completedAt)) throw new IllegalStateException("Media review changed");
    moderation = approved ? Moderation.APPROVED_DEMO : Moderation.REJECTED;
    reviewedBy = actor;
    reviewedAt = now;
    if (!approved) status = State.REJECTED;
  }

  public void archive(UUID actor, Instant now) {
    if (status == State.ARCHIVED
        || actor == null
        || now == null
        || now.isBefore(requestedAt)
        || (reviewedAt != null && now.isBefore(reviewedAt))
        || (completedAt != null && now.isBefore(completedAt)))
      throw new IllegalStateException("Invalid media archive");
    status = State.ARCHIVED;
    archivedBy = actor;
    archivedAt = now;
  }

  public void requirePreview() {
    if (status != State.AVAILABLE
        || scanResult != ScanVerdict.CLEAN
        || completedAt == null
        || verificationFailure != null)
      throw new IllegalStateException("Media not verified or unavailable");
  }

  public void requireUsable() {
    requirePreview();
    if (moderation != Moderation.APPROVED_DEMO || reviewedBy == null || reviewedAt == null)
      throw new IllegalStateException("Media review required");
  }

  public MediaObjectKey key() {
    return new MediaObjectKey(brand.getId(), getId(), checksum, format);
  }

  public Brand getBrand() {
    return brand;
  }

  public DemoMediaFixture getFixture() {
    return fixture;
  }

  public String getOrigin() {
    return origin;
  }

  public MediaFormat getFormat() {
    return format;
  }

  public String getChecksum() {
    return checksum;
  }

  public int getByteSize() {
    return byteSize;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getDurationMillis() {
    return durationMillis;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public State getStatus() {
    return status;
  }

  public Moderation getModeration() {
    return moderation;
  }

  public ScanVerdict getScanResult() {
    return scanResult;
  }

  public String getScanEngine() {
    return scanEngine;
  }

  public String getScanSignature() {
    return scanSignature;
  }

  public Instant getScannedAt() {
    return scannedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getVerificationFailure() {
    return verificationFailure;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public UUID getArchivedBy() {
    return archivedBy;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
