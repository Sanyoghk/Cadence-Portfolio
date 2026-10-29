package com.cadence.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Append-only scheduling metadata, separate from immutable approved content. */
@Entity
@Table(name = "post_schedule_events")
public class PostScheduleEvent extends BaseEntity {
  public enum Action {
    SCHEDULE,
    CANCEL
  }

  @Column(name = "brand_id", nullable = false, updatable = false)
  private UUID brandId;

  @ManyToOne(optional = false)
  @JoinColumn(name = "post_id", updatable = false)
  private ContentPost post;

  @ManyToOne(optional = false)
  @JoinColumn(name = "revision_id", updatable = false)
  private PostRevision revision;

  @Column(name = "event_number", nullable = false, updatable = false)
  private int eventNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false)
  private Action action;

  @Column(name = "revision_lock_version", nullable = false, updatable = false)
  private long revisionLockVersion;

  @Column(name = "content_checksum", length = 64, nullable = false, updatable = false)
  private String contentChecksum;

  @Column(nullable = false, updatable = false, length = 100)
  private String timezone;

  @Column(name = "local_time", nullable = false, updatable = false)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.LOCAL_DATE_TIME)
  private LocalDateTime localTime;

  @Column(name = "offset_seconds", nullable = false, updatable = false)
  private int offsetSeconds;

  @Column(name = "scheduled_at", nullable = false, updatable = false)
  private Instant scheduledAt;

  @Column(name = "rules_version", nullable = false, updatable = false, length = 40)
  private String rulesVersion;

  @Column(name = "actor_id", nullable = false, updatable = false)
  private UUID actorId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected PostScheduleEvent() {}

  public PostScheduleEvent(
      PostRevision current,
      PostScheduleEvent prior,
      Action action,
      CalendarTime time,
      UUID actor,
      Instant now) {
    if (current == null
        || current.getId() == null
        || action == null
        || actor == null
        || now == null
        || (prior != null
            && (!prior.post.getId().equals(current.getPost().getId())
                || prior.eventNumber >= 1000)))
      throw new IllegalStateException("Invalid schedule transition");
    post = current.getPost();
    brandId = post.getBrand().getId();
    eventNumber = prior == null ? 1 : prior.eventNumber + 1;
    this.action = action;
    actorId = actor;
    occurredAt = now;
    if (prior != null && now.isBefore(prior.occurredAt))
      throw new IllegalStateException("Schedule clock changed");
    if (action == Action.CANCEL) {
      if (time != null || prior == null || prior.action != Action.SCHEDULE)
        throw new IllegalStateException("No active schedule to cancel");
      revision = prior.revision;
      revisionLockVersion = prior.revisionLockVersion;
      contentChecksum = prior.contentChecksum;
      timezone = prior.timezone;
      localTime = prior.localTime;
      offsetSeconds = prior.offsetSeconds;
      scheduledAt = prior.scheduledAt;
      rulesVersion = prior.rulesVersion;
    } else {
      if (eventNumber == 1000)
        throw new IllegalStateException("Final schedule event reserved for cancellation");
      if (time == null
          || current.getStatus() != PostRevision.State.APPROVED
          || current.getApprovedBy() == null)
        throw new IllegalStateException("Current approved content required");
      post.requireCurrentSource();
      time.requireFuture(now);
      revision = current;
      revisionLockVersion = current.getLockVersion();
      contentChecksum = checksum(current.content());
      timezone = time.timezone();
      localTime = time.localTime();
      offsetSeconds = time.offset().getTotalSeconds();
      scheduledAt = time.instant();
      rulesVersion = time.rulesVersion();
    }
  }

  public static String checksum(PostContent content) {
    var text = new StringBuilder("post-schedule-content-v1:");
    for (String field :
        new String[] {
          content.title(),
          content.caption(),
          content.format().name(),
          content.mediaPrompt(),
          content.videoScript(),
          Integer.toString(content.hashtags().size())
        }) part(text, field);
    content.hashtags().forEach(h -> part(text, h));
    return BrandAnalysisAttempt.checksum(text.toString());
  }

  private static void part(StringBuilder text, String field) {
    if (field == null) text.append("-1:");
    else text.append(field.getBytes(StandardCharsets.UTF_8).length).append(':').append(field);
  }

  public ContentPost getPost() {
    return post;
  }

  public PostRevision getRevision() {
    return revision;
  }

  public int getEventNumber() {
    return eventNumber;
  }

  public Action getAction() {
    return action;
  }

  public long getRevisionLockVersion() {
    return revisionLockVersion;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public String getTimezone() {
    return timezone;
  }

  public LocalDateTime getLocalTime() {
    return localTime;
  }

  public int getOffsetSeconds() {
    return offsetSeconds;
  }

  public Instant getScheduledAt() {
    return scheduledAt;
  }

  public String getRulesVersion() {
    return rulesVersion;
  }

  public UUID getActorId() {
    return actorId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
