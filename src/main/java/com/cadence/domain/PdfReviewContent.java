package com.cadence.domain;

import java.time.*;
import java.util.*;

/** Readiness for human review only; never an admission or document safety guarantee. */
public record PdfReviewContent(
    UUID artifactId,
    UUID originalScanId,
    UUID latestScanId,
    String sourceChecksum,
    String contentChecksum,
    String text,
    String rendererVersion,
    Instant scanExpiresAt,
    long sourceBytes) {
  public static final String POLICY_VERSION = "pdf-text-review-v1";
  public static final int MAX_SOURCE_BYTES = 10 * 1024 * 1024;

  public PdfReviewContent {
    if (artifactId == null
        || originalScanId == null
        || latestScanId == null
        || sourceChecksum == null
        || !sourceChecksum.matches("[0-9a-f]{64}")
        || text == null
        || text.isBlank()
        || !EvidenceArtifact.validText(text)
        || !EvidenceAdmission.checksum(text).equals(contentChecksum)
        || rendererVersion == null
        || !rendererVersion.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")
        || scanExpiresAt == null
        || sourceBytes < 1
        || sourceBytes > MAX_SOURCE_BYTES)
      throw new IllegalArgumentException("Invalid PDF review content");
  }

  public static void requireSupported(BrandEvidence e) {
    if (e == null
        || e.getType() != EvidenceType.FILE
        || !"application/pdf".equals(e.getMediaType())
        || e.getStatus() != EvidenceStatus.PENDING
        || e.getBrand().getStatus() != BrandStatus.ACTIVE
        || e.getBrand().getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Evidence is not eligible for PDF text review");
  }

  public static PdfReviewContent from(
      BrandEvidence e,
      EvidenceArtifact a,
      EvidenceScanResult latest,
      QuarantineObject q,
      Instant now,
      Duration maxAge) {
    requireSupported(e);
    if (now == null
        || maxAge == null
        || maxAge.compareTo(Duration.ofSeconds(1)) < 0
        || maxAge.compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("Invalid PDF review policy");
    if (a == null
        || latest == null
        || q == null
        || a.getId() == null
        || latest.getId() == null
        || a.getKind() != EvidenceArtifact.Kind.PDF_TEXT
        || !a.getEvidenceId().equals(e.getId())
        || a.getSourceVersion() != e.getLockVersion()
        || !a.getSourceChecksum().equals(e.getChecksum())
        || a.getStorageReference() != null
        || a.getJob() == null
        || a.getJob().getStatus() != JobStatus.SUCCEEDED
        || a.getProducedAt() == null
        || a.getProducedAt().isAfter(now)) throw unavailable();
    var original = a.getScanResult();
    if (!clean(original, e, now)
        || !clean(latest, e, now)
        || a.getProducedAt().isBefore(original.getScannedAt())
        || !now.isBefore(latest.getScannedAt().plus(maxAge))) throw unavailable();
    try {
      new ScanProvenance(original.getScannerEngine(), original.getSignatureVersion());
      new ScanProvenance(latest.getScannerEngine(), latest.getSignatureVersion());
    } catch (IllegalArgumentException invalid) {
      throw unavailable();
    }
    String text = a.getExtractedText();
    if (text == null
        || text.isBlank()
        || !EvidenceArtifact.validText(text)
        || !EvidenceAdmission.checksum(text).equals(a.getOutputChecksum())
        || a.getRendererVersion() == null
        || !a.getRendererVersion().matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")) throw unavailable();
    if (q.getState() != QuarantineObject.State.REFERENCED
        || q.isQuotaReleased()
        || q.getDeletedAt() != null
        || !e.getId().equals(q.getEvidenceId())
        || !e.getBrand().getId().equals(q.getBrandId())
        || !e.getBrand().getWorkspace().getId().equals(q.getWorkspaceId())
        || !e.getSourceReference().equals(q.reference())
        || !("quarantine/writes/" + q.getWorkspaceId() + "/" + q.getId() + ".pdf")
            .equals(q.getObjectKey())
        || q.getRetainedBytes() < 1
        || q.getRetainedBytes() > MAX_SOURCE_BYTES) throw unavailable();
    return new PdfReviewContent(
        a.getId(),
        original.getId(),
        latest.getId(),
        e.getChecksum(),
        a.getOutputChecksum(),
        text,
        a.getRendererVersion(),
        latest.getScannedAt().plus(maxAge),
        q.getRetainedBytes());
  }

  private static boolean clean(EvidenceScanResult s, BrandEvidence e, Instant now) {
    return s != null
        && s.getId() != null
        && e.getId().equals(s.getEvidenceId())
        && s.getVerdict() == ScanVerdict.CLEAN
        && s.getSourceVersion() == e.getLockVersion()
        && e.getChecksum().equals(s.getChecksum())
        && s.getScannedAt() != null
        && !s.getScannedAt().isAfter(now);
  }

  private static IllegalStateException unavailable() {
    return new IllegalStateException("PDF review prerequisites are not current");
  }

  public void verifyBytes(byte[] bytes) {
    if (bytes == null
        || bytes.length != sourceBytes
        || bytes.length < 5
        || bytes.length > MAX_SOURCE_BYTES) throw unavailable();
    byte[] owned = bytes.clone();
    if (owned[0] != '%'
        || owned[1] != 'P'
        || owned[2] != 'D'
        || owned[3] != 'F'
        || owned[4] != '-'
        || !checksum(owned).equals(sourceChecksum)) throw unavailable();
  }

  private static String checksum(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
