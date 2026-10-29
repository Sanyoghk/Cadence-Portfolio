package com.cadence.domain;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Exact URL projection and capture-time scan. Neither the URL nor raw HTML is model input. */
public record UrlReviewContent(
    UUID snapshotId,
    UUID latestScanId,
    String sourceChecksum,
    String fetchedChecksum,
    String contentChecksum,
    String text,
    WebsiteMetadata metadata,
    UUID fetchJobId,
    int fetchAttempt,
    Instant fetchedAt,
    Instant extractedAt,
    Instant scanExpiresAt,
    long sourceBytes,
    String mediaType) {
  public static final String POLICY_VERSION = "url-text-review-v1";
  public static final int MAX_SOURCE_BYTES = 2 * 1024 * 1024;

  public static UrlReviewContent from(
      BrandEvidence e,
      UrlTextSnapshot s,
      EvidenceScanResult scan,
      QuarantineObject q,
      Instant now,
      Duration maxAge) {
    UrlTextSnapshot.requireSource(e);
    if (s == null || s.getId() == null || scan == null || q == null) throw unavailable();
    s.requireCurrent(e);
    if (!e.getId().equals(scan.getEvidenceId())
        || scan.getVerdict() != ScanVerdict.CLEAN
        || scan.getSourceVersion() != e.getLockVersion()
        || !e.getFetchedChecksum().equals(scan.getChecksum())
        || scan.getScannedAt().isAfter(now)
        || scan.getScannedAt().isBefore(e.getFetchedAt())
        || !now.isBefore(scan.getScannedAt().plus(maxAge))
        || e.getExtractedAt().isAfter(now)) throw unavailable();
    if ("legacy-unknown".equals(scan.getScannerEngine())
        || "legacy-unknown".equals(scan.getSignatureVersion())) throw unavailable();
    new ScanProvenance(scan.getScannerEngine(), scan.getSignatureVersion());
    var m = e.getWebsiteMetadata();
    if (m.title().isBlank() && m.description().isBlank() && m.text().isBlank()) throw unavailable();
    if (s.getPayload().getBytes(StandardCharsets.UTF_8).length > 20000) throw unavailable();
    if (q.getState() != QuarantineObject.State.REFERENCED
        || q.isQuotaReleased()
        || q.getDeletedAt() != null
        || !e.getId().equals(q.getEvidenceId())
        || !e.getBrand().getId().equals(q.getBrandId())
        || !e.getBrand().getWorkspace().getId().equals(q.getWorkspaceId())
        || !e.getFetchedReference().equals(q.reference())
        || !("quarantine/writes/" + q.getWorkspaceId() + "/" + q.getId() + ".html")
            .equals(q.getObjectKey())
        || q.getRetainedBytes() < 1
        || q.getRetainedBytes() > MAX_SOURCE_BYTES) throw unavailable();
    return new UrlReviewContent(
        s.getId(),
        scan.getId(),
        e.getChecksum(),
        e.getFetchedChecksum(),
        s.getContentChecksum(),
        s.getPayload(),
        m,
        s.getFetchJobId(),
        s.getFetchAttempt(),
        s.getFetchedAt(),
        s.getExtractedAt(),
        scan.getScannedAt().plus(maxAge).truncatedTo(java.time.temporal.ChronoUnit.MICROS),
        q.getRetainedBytes(),
        s.getMediaType());
  }

  public void verifyBytes(byte[] bytes) {
    if (bytes == null
        || bytes.length != sourceBytes
        || bytes.length < 1
        || bytes.length > MAX_SOURCE_BYTES) throw unavailable();
    try {
      if (!HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
          .equals(fetchedChecksum)) throw unavailable();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static IllegalStateException unavailable() {
    return new IllegalStateException("URL review prerequisites are not current");
  }
}
