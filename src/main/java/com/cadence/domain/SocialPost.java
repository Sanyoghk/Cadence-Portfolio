package com.cadence.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Legacy persistence only. New workflows use ContentPost/PostRevision (ADR0031). */
@Entity
@Table(name = "social_posts")
public class SocialPost extends BaseEntity {

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 5000)
  private String caption;

  @ElementCollection
  @CollectionTable(name = "social_post_hashtags", joinColumns = @JoinColumn(name = "post_id"))
  @Column(name = "hashtag", nullable = false, length = 100)
  @OrderColumn(name = "hashtag_order")
  private List<String> hashtags = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostFormat format;

  @Column(name = "scheduled_at")
  private Instant scheduledAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private PostStatus status = PostStatus.DRAFT;

  @ManyToOne(optional = false)
  @JoinColumn(name = "strategy_id", nullable = false)
  private Strategy strategy;

  protected SocialPost() {}

  public SocialPost(
      Strategy strategy, String title, String caption, List<String> hashtags, PostFormat format) {
    setStrategy(strategy);
    setTitle(title);
    setCaption(caption);
    setHashtags(hashtags);
    setFormat(format);

    this.status = PostStatus.DRAFT;
  }

  public void approve() {
    if (status != PostStatus.DRAFT) {
      throw new IllegalStateException("Only draft posts can be approved");
    }

    this.status = PostStatus.APPROVED;
  }

  public void schedule(Instant date) {
    if (date == null) {
      throw new IllegalArgumentException("Scheduled date must not be null");
    }

    if (date.isBefore(Instant.now())) {
      throw new IllegalArgumentException("Scheduled date must be in the future");
    }

    if (status != PostStatus.APPROVED) {
      throw new IllegalStateException("Only approved posts can be scheduled");
    }

    this.scheduledAt = date;
    this.status = PostStatus.SCHEDULED;
  }

  public void regenerate() {
    if (status == PostStatus.PUBLISHED) {
      throw new IllegalStateException("Published posts cannot be regenerated");
    }

    this.status = PostStatus.DRAFT;
    this.scheduledAt = null;
  }

  public void markPublished() {
    if (status != PostStatus.SCHEDULED) {
      throw new IllegalStateException("Only scheduled posts can be marked as published");
    }

    this.status = PostStatus.PUBLISHED;
  }

  public void markFailed() {
    if (status != PostStatus.SCHEDULED) {
      throw new IllegalStateException("Only scheduled posts can fail during publishing");
    }

    this.status = PostStatus.FAILED;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    requireDraftForEditing();
    this.title = DomainText.required(title, "Post title", 255);
  }

  public String getCaption() {
    return caption;
  }

  public void setCaption(String caption) {
    requireDraftForEditing();
    this.caption = DomainText.required(caption, "Post caption", 5000);
  }

  public List<String> getHashtags() {
    return List.copyOf(hashtags);
  }

  public void setHashtags(List<String> hashtags) {
    requireDraftForEditing();
    this.hashtags.clear();

    if (hashtags == null) {
      return;
    }

    List<String> normalizedHashtags =
        hashtags.stream()
            .filter(hashtag -> hashtag != null)
            .map(String::trim)
            .filter(hashtag -> !hashtag.isBlank())
            .map(this::normalizeHashtag)
            .peek(
                hashtag -> {
                  if (hashtag.length() > 100) {
                    throw new IllegalArgumentException("Hashtag must not exceed 100 characters");
                  }
                })
            .distinct()
            .toList();

    this.hashtags.addAll(normalizedHashtags);
  }

  private String normalizeHashtag(String hashtag) {
    if (hashtag.startsWith("#")) {
      return hashtag;
    }

    return "#" + hashtag;
  }

  public PostFormat getFormat() {
    return format;
  }

  public void setFormat(PostFormat format) {
    requireDraftForEditing();
    if (format == null) {
      throw new IllegalArgumentException("Post format must not be null");
    }

    this.format = format;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public PostStatus getStatus() {
    return status;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  private void setStrategy(Strategy strategy) {
    if (strategy == null) {
      throw new IllegalArgumentException("Post strategy must not be null");
    }

    this.strategy = strategy;
  }

  private void requireDraftForEditing() {
    if (status != null && status != PostStatus.DRAFT) {
      throw new IllegalStateException("Only draft posts can be edited");
    }
  }
}
