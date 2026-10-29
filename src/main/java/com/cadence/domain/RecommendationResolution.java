package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recommendation_resolutions")
public class RecommendationResolution extends BaseEntity {
  @Column(nullable = false, updatable = false, unique = true)
  private UUID requestId;

  public UUID getRequestId() {
    return requestId;
  }

  public enum Action {
    APPLY,
    DISMISS
  }

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "recommendation_id", updatable = false, unique = true)
  private RecommendationInsight recommendation;

  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private Action action;

  @Column(nullable = false, updatable = false, length = 64)
  private String keyDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "draft_id", updatable = false, unique = true)
  private Strategy draft;

  @Column(updatable = false, length = 64)
  private String draftChecksum;

  @Column(nullable = false, updatable = false)
  private Instant resolvedAt;

  protected RecommendationResolution() {}

  public RecommendationResolution(
      UUID requestId,
      RecommendationInsight r,
      UUID actor,
      Action action,
      String key,
      String digest,
      Strategy draft,
      Instant at) {
    if (r == null
        || actor == null
        || action == null
        || at == null
        || (action == Action.APPLY) != (draft != null))
      throw new IllegalArgumentException("Invalid recommendation resolution");
    if (requestId == null) throw new IllegalArgumentException("Recommendation admission required");
    this.requestId = requestId;
    this.recommendation = r;
    actorId = actor;
    workspaceId = r.getWorkspaceId();
    this.action = action;
    keyDigest = key;
    requestDigest = digest;
    this.draft = draft;
    resolvedAt = at;
    if (draft != null) {
      if (draft.getStatus() != ApprovalStatus.DRAFT
          || !r.getStrategy().getId().equals(draft.getRevisedFromId()))
        throw new IllegalStateException("Invalid recommendation draft");
      draftChecksum =
          BrandAnalysisAttempt.checksum(draft.getPlaybook().canonical(StrategyContent.from(draft)));
    }
  }

  public UUID getActorId() {
    return actorId;
  }

  public Action getAction() {
    return action;
  }

  public String getKeyDigest() {
    return keyDigest;
  }

  public String getRequestDigest() {
    return requestDigest;
  }

  public Strategy getDraft() {
    return draft;
  }

  public String getDraftChecksum() {
    return draftChecksum;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public RecommendationInsight getRecommendation() {
    return recommendation;
  }
}
