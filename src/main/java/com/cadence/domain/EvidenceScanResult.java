package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Append-only observation for one valid Job attempt; no setters or cascading deletion. */
@Entity
@Table(
    name = "evidence_scan_results",
    uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "attempt_number"}))
public class EvidenceScanResult extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "evidence_id", nullable = false, updatable = false)
  private BrandEvidence evidence;

  @ManyToOne(optional = false)
  @JoinColumn(name = "job_id", nullable = false, updatable = false)
  private DurableJob job;

  @Column(name = "attempt_number", nullable = false, updatable = false)
  private int attemptNumber;

  @Column(name = "lease_owner", nullable = false, updatable = false, length = 255)
  private String leaseOwner;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(nullable = false, updatable = false, length = 64)
  private String checksum;

  @Column(name = "scanned_at", nullable = false, updatable = false)
  private Instant scannedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 32)
  private ScanVerdict verdict;

  @Column(name = "scanner_engine", nullable = false, updatable = false, length = 100)
  private String scannerEngine;

  @Column(name = "signature_version", nullable = false, updatable = false, length = 100)
  private String signatureVersion;

  protected EvidenceScanResult() {}

  public EvidenceScanResult(
      BrandEvidence evidence,
      DurableJob job,
      String checksum,
      Instant scannedAt,
      ScanVerdict verdict,
      String scannerEngine,
      String signatureVersion,
      Instant now) {
    try {
      new ScanProvenance(scannerEngine, signatureVersion);
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("Invalid evidence scan provenance");
    }
    if (evidence == null
        || job == null
        || verdict == null
        || now == null
        || scannedAt == null
        || evidence.getId() == null
        || job.getId() == null
        || !isScannable(evidence)
        || !("EVIDENCE_MALWARE_SCAN".equals(job.getType())
            || (evidence.getType() == EvidenceType.FILE
                && "BRAND_EVIDENCE_INGEST".equals(job.getType())))
        || !("evidence:" + evidence.getId()).equals(job.getPayloadReference())
        || job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() < 1
        || job.getLeaseOwner() == null
        || job.getLeaseExpiresAt() == null
        || !job.getLeaseExpiresAt().isAfter(now)
        || checksum == null
        || !checksum.matches("[0-9a-f]{64}")
        || !checksum.equals(expectedChecksum(evidence))
        || scannedAt.isBefore(scanAvailableAt(evidence))
        || scannedAt.isAfter(now))
      throw new IllegalArgumentException("Invalid evidence scan provenance");
    this.evidence = evidence;
    this.job = job;
    this.attemptNumber = job.getAttempts();
    this.leaseOwner = job.getLeaseOwner();
    this.sourceVersion = evidence.getLockVersion();
    this.checksum = checksum;
    this.scannedAt = scannedAt;
    this.verdict = verdict;
    this.scannerEngine = scannerEngine;
    this.signatureVersion = signatureVersion;
  }

  private static boolean isScannable(BrandEvidence evidence) {
    return (evidence.getType() == EvidenceType.FILE
            && evidence.getStatus() == EvidenceStatus.PENDING)
        || (evidence.getType() == EvidenceType.URL
            && evidence.getStatus() == EvidenceStatus.FETCHED);
  }

  private static String expectedChecksum(BrandEvidence evidence) {
    return evidence.getType() == EvidenceType.URL
        ? evidence.getFetchedChecksum()
        : evidence.getChecksum();
  }

  private static Instant scanAvailableAt(BrandEvidence evidence) {
    return evidence.getType() == EvidenceType.URL
        ? evidence.getFetchedAt()
        : evidence.getCapturedAt();
  }

  public int getAttemptNumber() {
    return attemptNumber;
  }

  public java.util.UUID getEvidenceId() {
    return evidence.getId();
  }

  public String getChecksum() {
    return checksum;
  }

  public Instant getScannedAt() {
    return scannedAt;
  }

  public ScanVerdict getVerdict() {
    return verdict;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public String getLeaseOwner() {
    return leaseOwner;
  }

  public String getScannerEngine() {
    return scannerEngine;
  }

  public String getSignatureVersion() {
    return signatureVersion;
  }
}
