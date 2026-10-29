package com.cadence.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** Immutable admission binding survives failed pure-rule attempts. No spend is implied. */
@Entity
@Table(name = "recommendation_keys")
public class RecommendationKey extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID actorId;

  @Column(nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(nullable = false, updatable = false)
  private UUID brandId;

  @Column(nullable = false, updatable = false, length = 20)
  private String operation;

  @Column(nullable = false, updatable = false, length = 64)
  private String keyDigest;

  @Column(nullable = false, updatable = false, length = 64)
  private String requestDigest;

  protected RecommendationKey() {}

  public RecommendationKey(
      UUID actor, UUID workspace, UUID brand, String operation, String key, String digest) {
    if (actor == null
        || workspace == null
        || brand == null
        || !java.util.Set.of("GENERATE", "APPLY", "DISMISS").contains(operation)
        || key == null
        || !key.matches("[0-9a-f]{64}")
        || digest == null
        || !digest.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid recommendation key binding");
    actorId = actor;
    workspaceId = workspace;
    brandId = brand;
    this.operation = operation;
    keyDigest = key;
    requestDigest = digest;
  }

  public String getRequestDigest() {
    return requestDigest;
  }
}
