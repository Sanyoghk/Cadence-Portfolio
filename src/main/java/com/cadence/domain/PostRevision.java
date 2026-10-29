package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "post_revisions")
public class PostRevision extends BaseEntity {
  public enum State {
    DRAFT,
    APPROVED,
    SUPERSEDED,
    LEGACY
  }

  @ManyToOne(optional = false)
  @JoinColumn(name = "post_id", nullable = false, updatable = false)
  private ContentPost post;

  @Column(name = "revision_number", nullable = false, updatable = false)
  private int revisionNumber;

  @Column(name = "revised_from_id", updatable = false)
  private UUID revisedFromId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private State status;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 5000)
  private String caption;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostFormat format;

  @Column(name = "media_prompt", length = 2000)
  private String mediaPrompt;

  @Column(name = "video_script", length = 5000)
  private String videoScript;

  @ElementCollection
  @CollectionTable(name = "post_revision_hashtags", joinColumns = @JoinColumn(name = "revision_id"))
  @OrderColumn(name = "hashtag_order")
  @Column(name = "hashtag", nullable = false, length = 100)
  private List<String> hashtags = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "post_revision_media", joinColumns = @JoinColumn(name = "revision_id"))
  @OrderColumn(name = "asset_order")
  @Column(name = "asset_id", nullable = false)
  private List<UUID> mediaAssetIds = new ArrayList<>();

  public List<UUID> getMediaAssetIds() {
    return List.copyOf(mediaAssetIds);
  }

  public void replaceMedia(List<UUID> ids, UUID actor, Instant now) {
    if (status != State.DRAFT) throw new IllegalStateException("Only draft media may be changed");
    if (ids == null
        || ids.size() > 10
        || ids.stream().anyMatch(Objects::isNull)
        || new HashSet<>(ids).size() != ids.size()
        || actor == null
        || now == null
        || now.isBefore(authoredAt)) throw new IllegalArgumentException("Invalid media edit");
    mediaAssetIds.clear();
    mediaAssetIds.addAll(ids);
    editedBy = actor;
    editedAt = now;
  }

  @Column(name = "authored_by", updatable = false)
  private UUID authoredBy;

  @Column(name = "authored_at", updatable = false)
  private Instant authoredAt;

  @Column(name = "edited_by")
  private UUID editedBy;

  @Column(name = "edited_at")
  private Instant editedAt;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @Column(name = "legacy_status", updatable = false)
  private String legacyStatus;

  @Column(name = "legacy_scheduled_at", updatable = false)
  private Instant legacyScheduledAt;

  protected PostRevision() {}

  @Column(name = "generation_batch_id", updatable = false)
  private UUID generationBatchId;

  @Column(name = "generation_slot_id", length = 64, updatable = false)
  private String generationSlotId;

  public UUID getGenerationBatchId() {
    return generationBatchId;
  }

  public String getGenerationSlotId() {
    return generationSlotId;
  }

  public static PostRevision generated(
      ContentPost post,
      PostContent content,
      UUID actor,
      Instant now,
      UUID batch,
      String slot,
      PostRevision prior) {
    var result = new PostRevision(post, content, actor, now);
    if (batch == null || slot == null || !slot.matches("[A-Za-z0-9_-]{1,64}"))
      throw new IllegalArgumentException("Generation receipt required");
    result.generationBatchId = batch;
    result.generationSlotId = slot;
    result.editedBy = null;
    result.editedAt = null;
    if (prior != null) {
      if (prior.getId() == null
          || prior.getPost() != post
          || !Set.of(State.DRAFT, State.APPROVED).contains(prior.status)
          || prior.revisionNumber >= 1000)
        throw new IllegalStateException("Cannot regenerate revision");
      result.revisionNumber = prior.revisionNumber + 1;
      result.revisedFromId = prior.getId();
      if (prior.status == State.DRAFT) prior.status = State.SUPERSEDED;
    }
    return result;
  }

  public PostRevision(ContentPost post, PostContent content, UUID actor, Instant now) {
    if (post == null || post.isLegacy() || actor == null || now == null)
      throw new IllegalArgumentException("Invalid Post revision");
    this.post = post;
    revisionNumber = 1;
    status = State.DRAFT;
    authoredBy = actor;
    authoredAt = now;
    replace(content, actor, now);
  }

  public void replace(PostContent content, UUID actor, Instant now) {
    if (status != State.DRAFT) throw new IllegalStateException("Only draft Post can be edited");
    if (content == null || actor == null || now == null || now.isBefore(authoredAt))
      throw new IllegalArgumentException("Invalid Post edit");
    content.validateFor(post.getPlatform());
    title = content.title();
    caption = content.caption();
    format = content.format();
    mediaPrompt = content.mediaPrompt();
    videoScript = content.videoScript();
    hashtags.clear();
    hashtags.addAll(content.hashtags());
    editedBy = actor;
    editedAt = now;
  }

  public PostRevision branch(PostContent content, UUID actor, Instant now) {
    if (status != State.APPROVED || revisionNumber >= 1000)
      throw new IllegalStateException("Cannot branch Post revision");
    var result = new PostRevision(post, content, actor, now);
    result.revisionNumber = revisionNumber + 1;
    result.revisedFromId = getId();
    result.mediaAssetIds.addAll(mediaAssetIds);
    return result;
  }

  public void approve(UUID actor, Instant now) {
    if (status != State.DRAFT) throw new IllegalStateException("Only draft Post can be approved");
    if (actor == null || now == null || now.isBefore(editedAt == null ? authoredAt : editedAt))
      throw new IllegalArgumentException("Approval requires actor and time");
    content().validateFor(post.getPlatform());
    status = State.APPROVED;
    approvedBy = actor;
    approvedAt = now;
  }

  public PostContent content() {
    return new PostContent(title, caption, hashtags, format, mediaPrompt, videoScript);
  }

  public ContentPost getPost() {
    return post;
  }

  public int getRevisionNumber() {
    return revisionNumber;
  }

  public UUID getRevisedFromId() {
    return revisedFromId;
  }

  public State getStatus() {
    return status;
  }

  public String getTitle() {
    return title;
  }

  public String getCaption() {
    return caption;
  }

  public PostFormat getFormat() {
    return format;
  }

  public List<String> getHashtags() {
    return List.copyOf(hashtags);
  }

  public String getMediaPrompt() {
    return mediaPrompt;
  }

  public String getVideoScript() {
    return videoScript;
  }

  public UUID getAuthoredBy() {
    return authoredBy;
  }

  public Instant getAuthoredAt() {
    return authoredAt;
  }

  public UUID getEditedBy() {
    return editedBy;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getLegacyStatus() {
    return legacyStatus;
  }

  public Instant getLegacyScheduledAt() {
    return legacyScheduledAt;
  }
}
