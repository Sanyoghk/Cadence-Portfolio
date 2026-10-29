package com.cadence.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** New audited contract; legacy Recommendation rows receive no invented provenance. */
@Entity
@Table(name = "recommendation_insights")
public class RecommendationInsight extends BaseEntity {
  @Column(nullable = false, updatable = false, length = 30)
  private String rulesVersion;

  public String getRulesVersion() {
    return rulesVersion;
  }

  public Instant getWindowFrom() {
    return windowFrom;
  }

  public Instant getWindowUntil() {
    return windowUntil;
  }

  @Column(nullable = false, updatable = false, unique = true)
  private UUID requestId;

  public UUID getRequestId() {
    return requestId;
  }

  @Column(nullable = false, updatable = false, length = 20)
  private String origin = "HEURISTIC";

  @Column(nullable = false, updatable = false, length = 64)
  private String ruleVersion = "pillar-shift-v1";

  @Column(nullable = false, updatable = false, length = 64)
  private String provider = "cadence-rules";

  @Column(updatable = false, length = 100)
  private String modelVersion;

  @Column(updatable = false, length = 100)
  private String promptVersion;

  @Column(nullable = false, updatable = false, length = 30)
  private String confidence = "UNCALIBRATED";

  @Column(nullable = false, updatable = false, length = 2000)
  private String explanation;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "brand_id", updatable = false)
  private Brand brand;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "strategy_id", updatable = false)
  private Strategy strategy;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, updatable = false, length = 64)
  private String keyDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @Column(nullable = false, updatable = false)
  private long sourceVersion;

  @Column(nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(nullable = false, updatable = false, length = 262144)
  private String snapshot;

  @Column(nullable = false, updatable = false, length = 64)
  private String snapshotChecksum;

  @Column(nullable = false, updatable = false, length = 64)
  private String fromPillar;

  @Column(nullable = false, updatable = false, length = 64)
  private String toPillar;

  @Column(nullable = false, updatable = false)
  private Instant windowFrom;

  @Column(nullable = false, updatable = false)
  private Instant windowUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private PlaybookContent.Channel platform;

  @Column(nullable = false, updatable = false)
  private Instant capturedAt;

  @ElementCollection
  @CollectionTable(
      name = "recommendation_metric_links",
      joinColumns = @JoinColumn(name = "recommendation_id"))
  @OrderColumn(name = "item_order")
  private List<RecommendationMetricLink> metrics = new ArrayList<>();

  protected RecommendationInsight() {}

  public RecommendationInsight(
      UUID requestId,
      Brand b,
      Strategy s,
      UUID actor,
      String key,
      String request,
      long sourceVersion,
      String sourceChecksum,
      String snapshot,
      RecommendationProposal proposal,
      Instant captured,
      PerformanceQuery query,
      List<RecommendationMetricLink> links) {
    if (b == null
        || s == null
        || actor == null
        || captured == null
        || snapshot == null
        || snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 262144
        || !s.getBrand().getId().equals(b.getId())
        || !s.isReadyContentSource()
        || sourceVersion != s.getLockVersion()
        || links == null
        || links.size() < 10
        || links.size() > 100) throw new IllegalStateException("Invalid recommendation snapshot");
    if (requestId == null) throw new IllegalArgumentException("Recommendation admission required");
    this.requestId = requestId;
    this.brand = b;
    this.strategy = s;
    actorId = actor;
    workspaceId = b.getWorkspace().getId();
    keyDigest = key;
    requestDigest = request;
    this.sourceVersion = sourceVersion;
    this.sourceChecksum = sourceChecksum;
    this.snapshot = snapshot;
    snapshotChecksum = MetricRow.sha(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    fromPillar = proposal.fromPillar();
    toPillar = proposal.toPillar();
    capturedAt = captured;
    windowFrom = query.from();
    windowUntil = query.until();
    platform = query.platform();
    rulesVersion = java.time.zone.ZoneRulesProvider.getVersions(query.timezone()).lastKey();
    metrics.addAll(links);
    explanation =
        "Consider testing a 5-point pillar-weight transfer from "
            + fromPillar
            + " to "
            + toPillar
            + ". Observed engagement/reach differs; causal improvement is unknown.";
  }

  public Brand getBrand() {
    return brand;
  }

  public String getOrigin() {
    return origin;
  }

  public String getRuleVersion() {
    return ruleVersion;
  }

  public String getProvider() {
    return provider;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getConfidence() {
    return confidence;
  }

  public String getExplanation() {
    return explanation;
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getKeyDigest() {
    return keyDigest;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public long getSourceVersion() {
    return sourceVersion;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public String getSnapshotChecksum() {
    return snapshotChecksum;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public List<RecommendationMetricLink> getMetrics() {
    return List.copyOf(metrics);
  }
}
