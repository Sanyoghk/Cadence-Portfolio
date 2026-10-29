package com.cadence.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
public class RecommendationMetricLink {
  @Column(nullable = false, updatable = false)
  private UUID observationId;

  @Column(nullable = false, updatable = false)
  private UUID attributionId;

  @Column(nullable = false, updatable = false, length = 64)
  private String valueChecksum;

  protected RecommendationMetricLink() {}

  public RecommendationMetricLink(UUID observation, UUID attribution, String checksum) {
    if (observation == null
        || attribution == null
        || checksum == null
        || !checksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid supporting metric");
    observationId = observation;
    attributionId = attribution;
    valueChecksum = checksum;
  }

  public UUID getObservationId() {
    return observationId;
  }

  public UUID getAttributionId() {
    return attributionId;
  }

  public String getValueChecksum() {
    return valueChecksum;
  }
}
