package com.cadence.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Human-reviewed input snapshot, not model output, a citation, or a safety certification. */
@Entity
@Table(
    name = "evidence_admissions",
    uniqueConstraints =
        @UniqueConstraint(
            columnNames = {
              "evidence_id",
              "source_version",
              "brand_version",
              "workspace_version",
              "artifact_id",
              "latest_scan_id",
              "scan_expires_at"
            }))
public class EvidenceAdmission extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "evidence_id", nullable = false, updatable = false)
  private BrandEvidence evidence;

  @Column(name = "source_version", nullable = false, updatable = false)
  private long sourceVersion;

  @Column(name = "brand_version", nullable = false, updatable = false)
  private long brandVersion;

  @Column(name = "workspace_version", nullable = false, updatable = false)
  private long workspaceVersion;

  @Column(name = "source_checksum", nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(name = "content_checksum", nullable = false, updatable = false, length = 64)
  private String contentChecksum;

  @Column(name = "reviewed_text", nullable = false, updatable = false, length = 20000)
  private String reviewedText;

  @Column(name = "reviewed_by", nullable = false, updatable = false)
  private UUID reviewedBy;

  @Column(name = "reviewed_at", nullable = false, updatable = false)
  private Instant reviewedAt;

  @Column(name = "policy_version", nullable = false, updatable = false, length = 32)
  private String policyVersion;

  @Column(name = "artifact_id", updatable = false)
  private UUID artifactId;

  @Column(name = "original_scan_id", updatable = false)
  private UUID originalScanId;

  @Column(name = "latest_scan_id", updatable = false)
  private UUID latestScanId;

  @Column(name = "renderer_version", length = 100, updatable = false)
  private String rendererVersion;

  @Column(name = "scan_expires_at", updatable = false)
  private Instant scanExpiresAt;

  @Column(name = "url_snapshot_id", updatable = false)
  private UUID urlSnapshotId;

  @Column(name = "social_snapshot_id", updatable = false)
  private UUID socialSnapshotId;

  @Column(name = "binding_expires_at", updatable = false)
  private Instant bindingExpiresAt;

  public UUID getSocialSnapshotId() {
    return socialSnapshotId;
  }

  public Instant getBindingExpiresAt() {
    return bindingExpiresAt;
  }

  public EvidenceAdmission(
      BrandEvidence evidence,
      UUID actor,
      Instant now,
      SocialTextSnapshot snapshot,
      Instant bindingExpiry) {
    if (actor == null
        || now == null
        || snapshot.getId() == null
        || !snapshot.getEvidenceId().equals(evidence.getId())
        || snapshot.getSourceVersion() != evidence.getLockVersion()
        || now.isBefore(snapshot.getCapturedAt())
        || bindingExpiry == null
        || !bindingExpiry.isAfter(now.plusSeconds(30)))
      throw new IllegalArgumentException("Invalid social admission");
    this.evidence = evidence;
    sourceVersion = evidence.getLockVersion();
    brandVersion = evidence.getBrand().getLockVersion();
    workspaceVersion = evidence.getBrand().getWorkspace().getLockVersion();
    sourceChecksum = snapshot.getSourceChecksum();
    reviewedText = snapshot.getPayload();
    contentChecksum = snapshot.getContentChecksum();
    reviewedBy = actor;
    reviewedAt = now;
    policyVersion = SocialTextProjection.POLICY;
    socialSnapshotId = snapshot.getId();
    bindingExpiresAt = bindingExpiry;
  }

  public UUID getUrlSnapshotId() {
    return urlSnapshotId;
  }

  public EvidenceAdmission(BrandEvidence evidence, UUID actor, Instant now, UrlReviewContent c) {
    UrlTextSnapshot.requireSource(evidence);
    if (actor == null
        || now == null
        || c == null
        || now.isBefore(evidence.getCapturedAt())
        || !now.isBefore(c.scanExpiresAt())
        || !evidence.getChecksum().equals(c.sourceChecksum()))
      throw new IllegalArgumentException("Invalid URL admission");
    this.evidence = evidence;
    sourceVersion = evidence.getLockVersion();
    brandVersion = evidence.getBrand().getLockVersion();
    workspaceVersion = evidence.getBrand().getWorkspace().getLockVersion();
    sourceChecksum = c.sourceChecksum();
    contentChecksum = c.contentChecksum();
    reviewedText = c.text();
    reviewedBy = actor;
    reviewedAt = now;
    policyVersion = UrlReviewContent.POLICY_VERSION;
    urlSnapshotId = c.snapshotId();
    originalScanId = c.latestScanId();
    latestScanId = c.latestScanId();
    scanExpiresAt = c.scanExpiresAt();
  }

  public void requireCurrentUrl(
      UrlReviewContent c, long source, long brand, long workspace, Instant now) {
    if (!UrlReviewContent.POLICY_VERSION.equals(policyVersion)
        || sourceVersion != source
        || brandVersion != brand
        || workspaceVersion != workspace
        || scanExpiresAt == null
        || !now.isBefore(scanExpiresAt)
        || !scanExpiresAt.equals(c.scanExpiresAt())
        || !Objects.equals(urlSnapshotId, c.snapshotId())
        || !Objects.equals(originalScanId, c.latestScanId())
        || !Objects.equals(latestScanId, c.latestScanId())
        || !Objects.equals(sourceChecksum, c.sourceChecksum())
        || !Objects.equals(contentChecksum, c.contentChecksum())
        || !Objects.equals(reviewedText, c.text()))
      throw new IllegalStateException("URL admission is no longer current");
  }

  public static final String POLICY_VERSION = "text-review-v1";
  public static final int MAX_REVIEWS_PER_EVIDENCE = 1000;

  protected EvidenceAdmission() {}

  public EvidenceAdmission(BrandEvidence evidence, UUID actor, Instant now) {
    String text = reviewableText(evidence);
    if (actor == null || now == null || now.isBefore(evidence.getCapturedAt()))
      throw new IllegalArgumentException("Invalid evidence reviewer");
    this.evidence = evidence;
    sourceVersion = evidence.getLockVersion();
    brandVersion = evidence.getBrand().getLockVersion();
    workspaceVersion = evidence.getBrand().getWorkspace().getLockVersion();
    sourceChecksum = evidence.getChecksum();
    reviewedText = text;
    contentChecksum = checksum(text);
    reviewedBy = actor;
    reviewedAt = now;
    policyVersion = POLICY_VERSION;
  }

  public EvidenceAdmission(
      BrandEvidence evidence, UUID actor, Instant now, PdfReviewContent content) {
    PdfReviewContent.requireSupported(evidence);
    if (actor == null
        || now == null
        || content == null
        || now.isBefore(evidence.getCapturedAt())
        || !now.isBefore(content.scanExpiresAt())
        || !content.sourceChecksum().equals(evidence.getChecksum()))
      throw new IllegalArgumentException("Invalid PDF admission review");
    this.evidence = evidence;
    sourceVersion = evidence.getLockVersion();
    brandVersion = evidence.getBrand().getLockVersion();
    workspaceVersion = evidence.getBrand().getWorkspace().getLockVersion();
    sourceChecksum = content.sourceChecksum();
    contentChecksum = content.contentChecksum();
    reviewedText = content.text();
    reviewedBy = actor;
    reviewedAt = now;
    policyVersion = PdfReviewContent.POLICY_VERSION;
    artifactId = content.artifactId();
    originalScanId = content.originalScanId();
    latestScanId = content.latestScanId();
    rendererVersion = content.rendererVersion();
    scanExpiresAt = content.scanExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  public UUID getArtifactId() {
    return artifactId;
  }

  public UUID getOriginalScanId() {
    return originalScanId;
  }

  public UUID getLatestScanId() {
    return latestScanId;
  }

  public String getRendererVersion() {
    return rendererVersion;
  }

  public Instant getScanExpiresAt() {
    return scanExpiresAt;
  }

  public void requireCurrentPdf(
      PdfReviewContent c, long source, long brand, long workspace, Instant now) {
    if (!PdfReviewContent.POLICY_VERSION.equals(policyVersion)
        || sourceVersion != source
        || brandVersion != brand
        || workspaceVersion != workspace
        || scanExpiresAt == null
        || !now.isBefore(scanExpiresAt)
        || !scanExpiresAt.equals(
            c.scanExpiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS))
        || !Objects.equals(artifactId, c.artifactId())
        || !Objects.equals(originalScanId, c.originalScanId())
        || !Objects.equals(latestScanId, c.latestScanId())
        || !Objects.equals(rendererVersion, c.rendererVersion())
        || !Objects.equals(sourceChecksum, c.sourceChecksum())
        || !Objects.equals(contentChecksum, c.contentChecksum())
        || !Objects.equals(reviewedText, c.text()))
      throw new IllegalStateException("PDF admission is no longer current");
  }

  public static String reviewableText(BrandEvidence source) {
    if (source == null
        || source.getId() == null
        || source.getType() != EvidenceType.TEXT
        || source.getStatus() != EvidenceStatus.PENDING
        || source.getBrand().getStatus() != BrandStatus.ACTIVE
        || source.getBrand().getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Evidence is not eligible for text review");
    String text = source.getSourceReference();
    if (text == null
        || text.isBlank()
        || text.length() > 20000
        || !validText(text)
        || !checksum("TEXT\n" + text).equals(source.getChecksum()))
      throw new IllegalStateException("Evidence content is not eligible for text review");
    return text;
  }

  private static boolean validText(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i))) return false;
      } else if (Character.isLowSurrogate(c)
          || (Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) return false;
    }
    return true;
  }

  public static String checksum(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable", impossible);
    }
  }

  public UUID getEvidenceId() {
    return evidence.getId();
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public long getBrandVersion() {
    return brandVersion;
  }

  public long getWorkspaceVersion() {
    return workspaceVersion;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public String getReviewedText() {
    return reviewedText;
  }

  public UUID getReviewedBy() {
    return reviewedBy;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getPolicyVersion() {
    return policyVersion;
  }
}
