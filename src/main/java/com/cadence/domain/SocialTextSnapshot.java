package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "social_text_snapshots",
    uniqueConstraints = @UniqueConstraint(columnNames = {"evidence_id", "source_version"}))
public class SocialTextSnapshot extends BaseEntity {
  @Column(name = "evidence_id", nullable = false, updatable = false)
  private UUID evidenceId;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(name = "provider_call_id", nullable = false, updatable = false)
  private UUID providerCallId;

  @Column(name = "source_checksum", nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(name = "captured_at", nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(name = "source_captured_at", nullable = false, updatable = false)
  private Instant sourceCapturedAt;

  @Column(nullable = false, updatable = false, length = 20000)
  private String payload;

  @Column(name = "content_checksum", nullable = false, updatable = false, length = 64)
  private String contentChecksum;

  @Column(name = "post_ids", nullable = false, updatable = false, length = 1000)
  private String postIds;

  @Column(name = "projection_version", nullable = false, updatable = false, length = 32)
  private String projectionVersion;

  protected SocialTextSnapshot() {}

  public SocialTextSnapshot(BrandEvidence e, EvidenceSocialOrigin o) {
    var p = SocialTextProjection.project(e, o);
    evidenceId = e.getId();
    sourceVersion = e.getLockVersion();
    providerCallId = o.getProviderCallId();
    sourceChecksum = e.getChecksum();
    capturedAt = o.getCapturedAt();
    sourceCapturedAt = e.getCapturedAt();
    payload = p.payload();
    postIds = p.postIds();
    contentChecksum = EvidenceAdmission.checksum(payload);
    projectionVersion = SocialTextProjection.FORMAT;
  }

  public void requireCurrent(BrandEvidence e, EvidenceSocialOrigin o) {
    var p = SocialTextProjection.project(e, o);
    if (!evidenceId.equals(e.getId())
        || sourceVersion != e.getLockVersion()
        || !providerCallId.equals(o.getProviderCallId())
        || !sourceChecksum.equals(e.getChecksum())
        || !capturedAt.equals(o.getCapturedAt())
        || !sourceCapturedAt.equals(e.getCapturedAt())
        || !payload.equals(p.payload())
        || !postIds.equals(p.postIds())
        || !EvidenceAdmission.checksum(payload).equals(contentChecksum)
        || !SocialTextProjection.FORMAT.equals(projectionVersion))
      throw new IllegalStateException("Social snapshot changed");
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public UUID getProviderCallId() {
    return providerCallId;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public String getPayload() {
    return payload;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public String getPostIds() {
    return postIds;
  }
}
