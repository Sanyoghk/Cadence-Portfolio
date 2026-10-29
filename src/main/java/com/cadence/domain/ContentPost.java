package com.cadence.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/** Stable identity and immutable captured creative source. */
@Entity
@Table(name = "content_posts")
public class ContentPost extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", nullable = false, updatable = false)
  private Brand brand;

  @ManyToOne(optional = false)
  @JoinColumn(name = "strategy_id", nullable = false, updatable = false)
  private Strategy strategy;

  @Enumerated(EnumType.STRING)
  @Column(updatable = false)
  private PlaybookContent.Channel platform;

  @Column(nullable = false, updatable = false)
  private boolean legacy;

  @Column(name = "source_revision", updatable = false)
  private Integer sourceRevision;

  @Column(name = "source_lock_version", updatable = false)
  private Long sourceLockVersion;

  @Column(name = "source_approved_by", updatable = false)
  private UUID sourceApprovedBy;

  @Column(name = "source_approved_at", updatable = false)
  private Instant sourceApprovedAt;

  @Column(name = "source_checksum", length = 64, updatable = false)
  private String sourceChecksum;

  @Column(name = "authored_by", updatable = false)
  private UUID authoredBy;

  @Column(name = "authored_at", updatable = false)
  private Instant authoredAt;

  @Column(name = "outline_slot_id", length = 64, updatable = false)
  private String outlineSlotId;

  public String getOutlineSlotId() {
    return outlineSlotId;
  }

  public void bindOutlineSlot(String slot) {
    if (getId() != null
        || outlineSlotId != null
        || slot == null
        || !slot.matches("[A-Za-z0-9_-]{1,64}"))
      throw new IllegalStateException("Immutable outline binding");
    if (strategy.getPlaybook().fourWeekOutline().stream()
        .flatMap(w -> w.slots().stream())
        .noneMatch(s -> s.slotId().equals(slot) && s.channel() == platform))
      throw new IllegalArgumentException("Invalid outline slot");
    outlineSlotId = slot;
  }

  protected ContentPost() {}

  public ContentPost(Strategy strategy, PlaybookContent.Channel platform, UUID actor, Instant now) {
    if (strategy == null
        || !strategy.isReadyContentSource()
        || platform == null
        || actor == null
        || now == null) throw new IllegalStateException("Approved full Strategy required");
    strategy.validateContent();
    if (strategy.getPlaybook().channels().stream().noneMatch(c -> c.channel() == platform))
      throw new IllegalStateException("Platform absent from Strategy");
    this.brand = strategy.getBrand();
    this.strategy = strategy;
    this.platform = platform;
    sourceRevision = strategy.getVersion();
    sourceLockVersion = strategy.getLockVersion();
    sourceApprovedBy = strategy.getApprovedBy();
    sourceApprovedAt = strategy.getApprovedAt();
    sourceChecksum = checksum(strategy);
    authoredBy = actor;
    authoredAt = now;
  }

  public void requireCurrentSource() {
    if (legacy
        || !strategy.isReadyContentSource()
        || sourceRevision != strategy.getVersion()
        || sourceLockVersion != strategy.getLockVersion()
        || !sourceApprovedBy.equals(strategy.getApprovedBy())
        || !sourceApprovedAt.equals(strategy.getApprovedAt())
        || !sourceChecksum.equals(checksum(strategy)))
      throw new IllegalStateException("Post source unavailable or changed");
  }

  private static String checksum(Strategy strategy) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(
                      strategy
                          .getPlaybook()
                          .canonical(StrategyContent.from(strategy))
                          .getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  public Brand getBrand() {
    return brand;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public PlaybookContent.Channel getPlatform() {
    return platform;
  }

  public boolean isLegacy() {
    return legacy;
  }

  public Integer getSourceRevision() {
    return sourceRevision;
  }

  public Long getSourceLockVersion() {
    return sourceLockVersion;
  }

  public UUID getSourceApprovedBy() {
    return sourceApprovedBy;
  }

  public Instant getSourceApprovedAt() {
    return sourceApprovedAt;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public UUID getAuthoredBy() {
    return authoredBy;
  }

  public Instant getAuthoredAt() {
    return authoredAt;
  }
}
