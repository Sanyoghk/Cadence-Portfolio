package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "brand_identities")
public class BrandIdentity extends BaseEntity {
  public static final int MAX_REVISIONS_PER_BRAND = 1000;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "brand_id", nullable = false, updatable = false)
  private Brand brand;

  @Column(nullable = false, updatable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApprovalStatus status = ApprovalStatus.DRAFT;

  @Column(name = "revised_from_id", updatable = false)
  private UUID revisedFromId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "analysis_attempt_id", updatable = false)
  private BrandAnalysisAttempt analysisAttempt;

  @Column(name = "analysis_copied_by", updatable = false)
  private UUID analysisCopiedBy;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Embedded private ColorPalette palette;

  @ElementCollection
  @CollectionTable(name = "identity_fonts", joinColumns = @JoinColumn(name = "identity_id"))
  @OrderColumn(name = "item_order")
  @Column(name = "item_value", nullable = false, length = 100)
  private List<String> fonts = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "identity_voice_up", joinColumns = @JoinColumn(name = "identity_id"))
  @OrderColumn(name = "item_order")
  @Column(name = "item_value", nullable = false, length = 100)
  private List<String> voiceUp = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "identity_voice_down", joinColumns = @JoinColumn(name = "identity_id"))
  @OrderColumn(name = "item_order")
  @Column(name = "item_value", nullable = false, length = 100)
  private List<String> voiceDown = new ArrayList<>();

  @Column(name = "sample_caption", nullable = false, length = 1000)
  private String sampleCaption;

  protected BrandIdentity() {}

  public BrandIdentity(Brand brand, int version, BrandIdentityContent content) {
    if (brand == null || version < 1 || version > MAX_REVISIONS_PER_BRAND)
      throw new IllegalArgumentException("Invalid identity revision");
    this.brand = brand;
    this.version = version;
    replaceContent(content);
  }

  public void replaceContent(BrandIdentityContent content) {
    if (status != ApprovalStatus.DRAFT)
      throw new IllegalStateException("Only draft identities can be edited");
    if (content == null) throw new IllegalArgumentException("Identity content is required");
    var p = content.palette();
    palette = new ColorPalette(p.primary(), p.secondary(), p.accent(), p.neutral(), p.background());
    fonts.clear();
    fonts.addAll(content.fonts());
    voiceUp.clear();
    voiceUp.addAll(content.voiceUp());
    voiceDown.clear();
    voiceDown.addAll(content.voiceDown());
    sampleCaption = content.sampleCaption();
  }

  public static BrandIdentity fromAnalysis(
      Brand brand, int version, BrandIdentityContent content, BrandAnalysisAttempt attempt) {
    return fromReceipt(brand, version, content, attempt, null);
  }

  public static BrandIdentity fromEvidenceAnalysis(
      Brand brand,
      int version,
      BrandIdentityContent content,
      BrandAnalysisAttempt attempt,
      UUID actorId) {
    if (actorId == null) throw new IllegalArgumentException("Missing evidence copy actor");
    return fromReceipt(brand, version, content, attempt, actorId);
  }

  private static BrandIdentity fromReceipt(
      Brand brand,
      int version,
      BrandIdentityContent content,
      BrandAnalysisAttempt attempt,
      UUID actorId) {
    if (attempt == null
        || attempt.getId() == null
        || brand == null
        || !attempt.getBrandId().equals(brand.getId())
        || attempt.getStatus() != BrandAnalysisAttempt.Status.SUCCEEDED
        || (attempt.getRequestedBy() != null) != (actorId != null)
        || content == null
        || !content.checksum().equals(attempt.getOutputChecksum()))
      throw new IllegalStateException("Analysis does not match identity source");
    var identity = new BrandIdentity(brand, version, content);
    identity.analysisAttempt = attempt;
    identity.analysisCopiedBy = actorId;
    return identity;
  }

  public BrandAnalysisAttempt getAnalysisAttempt() {
    return analysisAttempt;
  }

  public UUID getAnalysisCopiedBy() {
    return analysisCopiedBy;
  }

  public void approve(UUID actorId, Instant now) {
    if (status != ApprovalStatus.DRAFT)
      throw new IllegalStateException("Only draft identities can be approved");
    if (actorId == null || now == null || getCreatedAt() == null || now.isBefore(getCreatedAt()))
      throw new IllegalArgumentException(
          "Approval requires persisted identity, actor and valid time");
    getContent();
    approvedBy = actorId;
    approvedAt = now;
    status = ApprovalStatus.APPROVED;
  }

  public BrandIdentity createDraftVersion(int nextVersion, BrandIdentityContent content) {
    if (status == ApprovalStatus.DRAFT || getId() == null)
      throw new IllegalStateException("Only persisted approved identities can create revisions");
    if (nextVersion <= version)
      throw new IllegalArgumentException("Revision must follow source version");
    var draft = new BrandIdentity(brand, nextVersion, content);
    draft.revisedFromId = getId();
    return draft;
  }

  public UUID getRevisedFromId() {
    return revisedFromId;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public BrandIdentityContent getContent() {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette(
            palette.getPrimary(),
            palette.getSecondary(),
            palette.getAccent(),
            palette.getNeutral(),
            palette.getBackground()),
        fonts,
        voiceUp,
        voiceDown,
        sampleCaption);
  }

  public Brand getBrand() {
    return brand;
  }

  public int getVersion() {
    return version;
  }

  public ApprovalStatus getStatus() {
    return status;
  }
}
