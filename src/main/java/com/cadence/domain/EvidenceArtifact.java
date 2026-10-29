package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only derivative provenance. A screenshot remains private quarantined content. */
@Entity
@Table(
    name = "evidence_artifacts",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {"evidence_id", "source_checksum", "source_version", "kind"}))
public class EvidenceArtifact extends BaseEntity {
  public enum Kind {
    SCREENSHOT,
    PDF_TEXT
  }

  @ManyToOne(optional = false)
  @JoinColumn(name = "evidence_id", nullable = false, updatable = false)
  private BrandEvidence evidence;

  @ManyToOne(optional = false)
  @JoinColumn(name = "scan_result_id", nullable = false, updatable = false)
  private EvidenceScanResult scanResult;

  @ManyToOne(optional = false)
  @JoinColumn(name = "job_id", nullable = false, updatable = false)
  private DurableJob job;

  @Column(name = "source_checksum", nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(name = "attempt_number", nullable = false, updatable = false)
  private int attemptNumber;

  @Column(name = "lease_owner", nullable = false, updatable = false, length = 255)
  private String leaseOwner;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private Kind kind;

  @Column(name = "storage_reference", updatable = false, length = 500)
  private String storageReference;

  @Column(name = "extracted_text", updatable = false, length = 16000)
  private String extractedText;

  @Column(name = "output_checksum", nullable = false, updatable = false, length = 64)
  private String outputChecksum;

  @Column(name = "renderer_version", nullable = false, updatable = false, length = 100)
  private String rendererVersion;

  @Column(name = "produced_at", nullable = false, updatable = false)
  private Instant producedAt;

  @Column(name = "pixel_width", updatable = false)
  private Integer pixelWidth;

  @Column(name = "pixel_height", updatable = false)
  private Integer pixelHeight;

  protected EvidenceArtifact() {}

  public EvidenceArtifact(
      BrandEvidence evidence,
      EvidenceScanResult scanResult,
      DurableJob job,
      Kind kind,
      String storageReference,
      String extractedText,
      String outputChecksum,
      String rendererVersion,
      Instant producedAt,
      Integer pixelWidth,
      Integer pixelHeight) {
    if (evidence == null
        || evidence.getId() == null
        || scanResult == null
        || scanResult.getId() == null
        || job == null
        || job.getId() == null
        || kind == null
        || producedAt == null
        || evidence.getStatus() == EvidenceStatus.FAILED
        || !evidence.getId().equals(scanResult.getEvidenceId())
        || scanResult.getVerdict() != ScanVerdict.CLEAN
        || scanResult.getSourceVersion() != evidence.getLockVersion()
        || !scanResult.getChecksum().equals(sourceChecksum(evidence))
        || producedAt.isBefore(scanResult.getScannedAt())
        || !"EVIDENCE_DOCUMENT_EXTRACT".equals(job.getType())
        || !("evidence:" + evidence.getId()).equals(job.getPayloadReference())
        || job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() < 1
        || job.getLeaseOwner() == null
        || job.getLeaseExpiresAt() == null
        || !job.getLeaseExpiresAt().isAfter(producedAt)
        || outputChecksum == null
        || !outputChecksum.matches("[0-9a-f]{64}")
        || rendererVersion == null
        || !rendererVersion.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
      throw new IllegalArgumentException("Invalid evidence artifact provenance");
    if (kind == Kind.SCREENSHOT) {
      if (evidence.getType() != EvidenceType.URL
          || !"text/html".equals(evidence.getFetchedMediaType())
          || storageReference == null
          || !storageReference.matches(
              "quarantine:quarantine/writes/[0-9a-f-]{36}/[0-9a-f-]{36}[.]png")
          || extractedText != null
          || !Integer.valueOf(1280).equals(pixelWidth)
          || !Integer.valueOf(720).equals(pixelHeight))
        throw new IllegalArgumentException("Invalid quarantined screenshot artifact");
    } else if (evidence.getType() != EvidenceType.FILE
        || !"application/pdf".equals(evidence.getMediaType())
        || storageReference != null
        || !validText(extractedText)
        || pixelWidth != null
        || pixelHeight != null) {
      throw new IllegalArgumentException("Invalid PDF text artifact");
    }
    this.evidence = evidence;
    this.scanResult = scanResult;
    this.job = job;
    sourceChecksum = sourceChecksum(evidence);
    sourceVersion = evidence.getLockVersion();
    attemptNumber = job.getAttempts();
    leaseOwner = job.getLeaseOwner();
    this.kind = kind;
    this.storageReference = storageReference;
    this.extractedText = extractedText;
    this.outputChecksum = outputChecksum;
    this.rendererVersion = rendererVersion;
    this.producedAt = producedAt;
    this.pixelWidth = pixelWidth;
    this.pixelHeight = pixelHeight;
  }

  public static String sourceChecksum(BrandEvidence evidence) {
    return evidence.getType() == EvidenceType.URL
        ? evidence.getFetchedChecksum()
        : evidence.getChecksum();
  }

  public static boolean validText(String text) {
    if (text == null || text.length() > 16000) return false;
    for (int i = 0; i < text.length(); i++) {
      char current = text.charAt(i);
      if (Character.isHighSurrogate(current)) {
        if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
      } else if (Character.isLowSurrogate(current)
          || (Character.isISOControl(current)
              && current != '\n'
              && current != '\r'
              && current != '\t')) {
        return false;
      }
    }
    return true;
  }

  public java.util.UUID getEvidenceId() {
    return evidence.getId();
  }

  public java.util.UUID getScanResultId() {
    return scanResult.getId();
  }

  public EvidenceScanResult getScanResult() {
    return scanResult;
  }

  public DurableJob getJob() {
    return job;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public Kind getKind() {
    return kind;
  }

  public String getStorageReference() {
    return storageReference;
  }

  public String getExtractedText() {
    return extractedText;
  }

  public String getOutputChecksum() {
    return outputChecksum;
  }

  public String getRendererVersion() {
    return rendererVersion;
  }

  public Instant getProducedAt() {
    return producedAt;
  }

  public Integer getPixelWidth() {
    return pixelWidth;
  }

  public Integer getPixelHeight() {
    return pixelHeight;
  }
}
