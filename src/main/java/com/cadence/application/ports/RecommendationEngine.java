package com.cadence.application.ports;

import com.cadence.domain.RecommendationProposal;

/** Bounded provider-neutral typed contract. Live external adapter is not activated. */
public interface RecommendationEngine {
  record Attribution(
      String origin,
      String ruleVersion,
      String provider,
      String modelVersion,
      String promptVersion) {}

  Attribution attribution();

  RecommendationProposal recommend(RecommendationInput input);
}
