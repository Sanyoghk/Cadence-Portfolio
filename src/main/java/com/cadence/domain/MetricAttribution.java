package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

/** Explicit present-day human assertion, not retroactive provider or approval audit. */
@Entity
@Table(name = "metric_attributions")
public class MetricAttribution extends BaseEntity {
  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "observation_id", updatable = false)
  private MetricObservation observation;

  @Column(nullable = false, updatable = false)
  private int attributionNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "previous_id", updatable = false, unique = true)
  private MetricAttribution previous;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "revision_id", updatable = false)
  private PostRevision revision;

  @Column(nullable = false, updatable = false)
  private long revisionVersion;

  @Column(nullable = false, updatable = false, length = 64)
  private String contentChecksum;

  @Column(updatable = false, length = 64)
  private String pillarId;

  @Column(updatable = false, length = 255)
  private String pillarName;

  @Column(nullable = false, updatable = false, length = 16)
  private String pillarOrigin;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private Instant attributedAt;

  protected MetricAttribution() {}

  public MetricAttribution(
      MetricObservation o, PostRevision r, long expected, String pillar, UUID actor, Instant now) {
    this(o, r, expected, pillar, actor, now, null);
  }

  public MetricAttribution(
      MetricObservation o,
      PostRevision r,
      long expected,
      String pillar,
      UUID actor,
      Instant now,
      MetricAttribution prior) {
    if (o == null
        || r == null
        || actor == null
        || now == null
        || r.getStatus() != PostRevision.State.APPROVED
        || r.getApprovedAt() == null
        || r.getApprovedAt().isAfter(o.getObservedAt())
        || !r.getPost().getId().equals(o.getPost().getId())
        || r.getLockVersion() != expected)
      throw new IllegalStateException("Invalid historical metrics attribution");
    if (prior != null
        && (!prior.observation.getId().equals(o.getId())
            || prior.attributionNumber >= 100
            || now.isBefore(prior.attributedAt)))
      throw new IllegalStateException("Attribution revision limit or history conflict");
    previous = prior;
    attributionNumber = prior == null ? 1 : prior.attributionNumber + 1;
    var p = o.getPost();
    var playbook = p.getStrategy().getPlaybook();
    if (p.getOutlineSlotId() != null) {
      var derived =
          playbook.fourWeekOutline().stream()
              .flatMap(w -> w.slots().stream())
              .filter(s -> s.slotId().equals(p.getOutlineSlotId()))
              .findFirst()
              .orElseThrow()
              .pillarId();
      if (pillar != null && !pillar.equals(derived))
        throw new IllegalArgumentException("Pillar conflicts with original outline");
      pillar = derived;
      pillarOrigin = "OUTLINE";
    } else pillarOrigin = pillar == null ? "UNKNOWN" : "HUMAN";
    if (pillar != null) {
      int at = playbook.pillarIds().indexOf(pillar);
      if (at < 0) throw new IllegalArgumentException("Pillar not in captured Strategy");
      pillarName = p.getStrategy().getContentPillars().get(at).getName();
    }
    observation = o;
    revision = r;
    revisionVersion = expected;
    pillarId = pillar;
    actorId = actor;
    attributedAt = now;
    contentChecksum = PostScheduleEvent.checksum(r.content());
  }

  public UUID getActorId() {
    return actorId;
  }

  public int getAttributionNumber() {
    return attributionNumber;
  }

  public MetricAttribution getPrevious() {
    return previous;
  }

  public Instant getAttributedAt() {
    return attributedAt;
  }

  public MetricObservation getObservation() {
    return observation;
  }

  public PostRevision getRevision() {
    return revision;
  }

  public long getRevisionVersion() {
    return revisionVersion;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }

  public String getPillarId() {
    return pillarId;
  }

  public String getPillarName() {
    return pillarName;
  }

  public String getPillarOrigin() {
    return pillarOrigin;
  }
}
