package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "metric_observations")
public class MetricObservation extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "post_id", updatable = false)
  private ContentPost post;

  @ManyToOne(optional = false)
  @JoinColumn(name = "import_id", updatable = false)
  private MetricImport originImport;

  @ManyToOne
  @JoinColumn(name = "previous_id", updatable = false)
  private MetricObservation previous;

  @Column(updatable = false, nullable = false)
  private int revisionNumber;

  @Column(updatable = false, nullable = false)
  private Instant windowFrom;

  @Column(updatable = false, nullable = false)
  private Instant windowUntil;

  @Column(updatable = false, nullable = false)
  private Instant observedAt;

  @Column(updatable = false, nullable = false)
  private Instant ingestedAt;

  @Column(updatable = false, nullable = false, length = 64)
  private String valueChecksum;

  @Column(updatable = false)
  private Long reach;

  @Column(updatable = false)
  private Long engagement;

  @Column(updatable = false)
  private Long saves;

  @Column(updatable = false)
  private Long signups;

  @Column(updatable = false)
  private Long profileToSiteClicks;

  protected MetricObservation() {}

  public MetricObservation(
      ContentPost p, MetricImport origin, MetricRow row, MetricObservation prior, Instant now) {
    if (p == null
        || origin == null
        || row == null
        || now == null
        || !p.getId().equals(row.postId())
        || p.getPlatform() != row.platform()
        || p.isLegacy()
        || !p.getBrand().getId().equals(origin.getBrand().getId())
        || row.observedAt().isAfter(now)
        || (prior == null
            ? row.correctionOf() != null
            : !prior.getId().equals(row.correctionOf())
                || !prior.post.getId().equals(p.getId())
                || !prior.observedAt.equals(row.observedAt())
                || prior.revisionNumber >= 100))
      throw new IllegalStateException("Invalid metric source or correction");
    post = p;
    originImport = origin;
    previous = prior;
    revisionNumber = prior == null ? 1 : prior.revisionNumber + 1;
    windowFrom = row.windowFrom();
    windowUntil = row.windowUntil();
    observedAt = row.observedAt();
    ingestedAt = now;
    valueChecksum = row.digest();
    reach = row.reach();
    engagement = row.engagement();
    saves = row.saves();
    signups = row.signups();
    profileToSiteClicks = row.profileToSiteClicks();
  }

  public ContentPost getPost() {
    return post;
  }

  public MetricImport getOriginImport() {
    return originImport;
  }

  public MetricObservation getPrevious() {
    return previous;
  }

  public int getRevisionNumber() {
    return revisionNumber;
  }

  public Instant getWindowFrom() {
    return windowFrom;
  }

  public Instant getWindowUntil() {
    return windowUntil;
  }

  public Instant getObservedAt() {
    return observedAt;
  }

  public Instant getIngestedAt() {
    return ingestedAt;
  }

  public String getValueChecksum() {
    return valueChecksum;
  }

  public Long getReach() {
    return reach;
  }

  public Long getEngagement() {
    return engagement;
  }

  public Long getSaves() {
    return saves;
  }

  public Long getSignups() {
    return signups;
  }

  public Long getProfileToSiteClicks() {
    return profileToSiteClicks;
  }
}
