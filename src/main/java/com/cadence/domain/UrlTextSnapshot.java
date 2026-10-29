package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable acquisition receipt. It records extraction, never human consent or malware safety. */
@Entity
@Table(
    name = "url_text_snapshots",
    uniqueConstraints = @UniqueConstraint(columnNames = {"evidence_id", "source_version"}))
public class UrlTextSnapshot extends BaseEntity {
  @Column(name = "evidence_id", nullable = false, updatable = false)
  private UUID evidenceId;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(name = "fetch_job_id", nullable = false, updatable = false)
  private UUID fetchJobId;

  @Column(name = "fetch_attempt", nullable = false, updatable = false)
  private int fetchAttempt;

  @Column(name = "lease_owner", nullable = false, updatable = false)
  private String leaseOwner;

  @Column(name = "source_checksum", nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(name = "storage_reference", nullable = false, updatable = false, length = 500)
  private String storageReference;

  @Column(name = "fetched_checksum", nullable = false, updatable = false, length = 64)
  private String fetchedChecksum;

  @Column(name = "fetched_at", nullable = false, updatable = false)
  private Instant fetchedAt;

  @Column(name = "captured_at", nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(name = "extracted_at", nullable = false, updatable = false)
  private Instant extractedAt;

  @Column(name = "media_type", nullable = false, updatable = false, length = 100)
  private String mediaType;

  @Column(name = "extraction_version", nullable = false, updatable = false, length = 32)
  private String extractionVersion;

  @Column(nullable = false, updatable = false, length = 500)
  private String warnings;

  @Column(nullable = false, updatable = false, length = 120000)
  private String payload;

  @Column(name = "content_checksum", nullable = false, updatable = false, length = 64)
  private String contentChecksum;

  protected UrlTextSnapshot() {}

  public UrlTextSnapshot(BrandEvidence e, DurableJob job, Instant now) {
    requireSource(e);
    if (job == null
        || job.getId() == null
        || !"BRAND_EVIDENCE_INGEST".equals(job.getType())
        || !("evidence:" + e.getId()).equals(job.getPayloadReference())
        || job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() < 1
        || job.getLeaseOwner() == null
        || job.getLeaseExpiresAt() == null
        || !job.getLeaseExpiresAt().isAfter(now)
        || !e.getFetchedAt().equals(now)
        || !e.getExtractedAt().equals(now))
      throw new IllegalArgumentException("Invalid URL acquisition receipt");
    evidenceId = e.getId();
    sourceVersion = e.getLockVersion();
    fetchJobId = job.getId();
    fetchAttempt = job.getAttempts();
    leaseOwner = job.getLeaseOwner();
    sourceChecksum = e.getChecksum();
    fetchedChecksum = e.getFetchedChecksum();
    fetchedAt = e.getFetchedAt();
    capturedAt = e.getCapturedAt();
    extractedAt = e.getExtractedAt();
    storageReference = e.getFetchedReference();
    mediaType = e.getFetchedMediaType();
    extractionVersion = e.getWebsiteMetadata().version();
    warnings = e.getWebsiteMetadata().warnings();
    payload = project(e.getWebsiteMetadata());
    contentChecksum = EvidenceAdmission.checksum(payload);
  }

  public static void requireSource(BrandEvidence e) {
    if (e == null
        || e.getId() == null
        || e.getType() != EvidenceType.URL
        || e.getStatus() != EvidenceStatus.FETCHED
        || e.getBrand().getStatus() != BrandStatus.ACTIVE
        || e.getBrand().getWorkspace().getStatus() != WorkspaceStatus.ACTIVE
        || e.getWebsiteMetadata() == null
        || !"website-v2".equals(e.getWebsiteMetadata().version())
        || e.getFetchedAt() == null
        || e.getExtractedAt() == null
        || e.getExtractedAt().isBefore(e.getFetchedAt())
        || !EvidenceAdmission.checksum("URL\n" + e.getSourceReference()).equals(e.getChecksum()))
      throw new IllegalStateException("URL review prerequisites unavailable");
  }

  public void requireCurrent(BrandEvidence e) {
    requireSource(e);
    if (!evidenceId.equals(e.getId())
        || sourceVersion != e.getLockVersion()
        || !sourceChecksum.equals(e.getChecksum())
        || !fetchedChecksum.equals(e.getFetchedChecksum())
        || !storageReference.equals(e.getFetchedReference())
        || !fetchedAt.equals(e.getFetchedAt())
        || !capturedAt.equals(e.getCapturedAt())
        || !extractedAt.equals(e.getExtractedAt())
        || !mediaType.equals(e.getFetchedMediaType())
        || !extractionVersion.equals(e.getWebsiteMetadata().version())
        || !warnings.equals(e.getWebsiteMetadata().warnings())
        || !payload.equals(project(e.getWebsiteMetadata()))
        || !contentChecksum.equals(EvidenceAdmission.checksum(payload)))
      throw new IllegalStateException("URL acquisition snapshot changed");
  }

  public static String project(WebsiteMetadata m) {
    if (m == null || !"website-v2".equals(m.version()))
      throw new IllegalArgumentException("Unsupported URL extraction");
    return "{\"format\":\"url-text-v1\",\"title\":"
        + quote(m.title())
        + ",\"description\":"
        + quote(m.description())
        + ",\"text\":"
        + quote(m.text())
        + "}";
  }

  private static String quote(String s) {
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= s.length() || !Character.isLowSurrogate(s.charAt(i)))
          throw new IllegalArgumentException("Invalid Unicode");
      } else if (Character.isLowSurrogate(c) || Character.isISOControl(c))
        throw new IllegalArgumentException("Invalid Unicode");
    }
    return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public UUID getFetchJobId() {
    return fetchJobId;
  }

  public int getFetchAttempt() {
    return fetchAttempt;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public String getFetchedChecksum() {
    return fetchedChecksum;
  }

  public Instant getFetchedAt() {
    return fetchedAt;
  }

  public Instant getExtractedAt() {
    return extractedAt;
  }

  public String getMediaType() {
    return mediaType;
  }

  public String getExtractionVersion() {
    return extractionVersion;
  }

  public String getWarnings() {
    return warnings;
  }

  public String getPayload() {
    return payload;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }
}
