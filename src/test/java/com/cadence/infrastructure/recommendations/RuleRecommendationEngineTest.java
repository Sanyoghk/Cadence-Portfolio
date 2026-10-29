package com.cadence.infrastructure.recommendations;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.RecommendationInput;
import com.cadence.domain.RecommendationProposal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class RuleRecommendationEngineTest {
  private RecommendationInput input(List<RecommendationInput.Metric> rows) {
    return new RecommendationInput(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"),
        rows,
        List.of(
            new RecommendationInput.PillarWeight("education", 60),
            new RecommendationInput.PillarWeight("community", 40)),
        new RecommendationProposal(
            "community",
            "education",
            5,
            rows.stream().map(RecommendationInput.Metric::observationId).toList()));
  }

  @Test
  void independentlyCalculatedWeightedRatiosDetermineDirectionInsteadOfEchoingCandidate() {
    var rows = new ArrayList<RecommendationInput.Metric>();
    long[] n = {1, 2, 9, 8, 5}, d = {10, 20, 30, 40, 50};
    for (int i = 0; i < 5; i++) {
      rows.add(new RecommendationInput.Metric(UUID.randomUUID(), "education", d[i], n[i]));
      rows.add(new RecommendationInput.Metric(UUID.randomUUID(), "community", 100, 20));
    }
    var result = new RuleRecommendationEngine().recommend(input(rows));
    assertThat(result.fromPillar()).isEqualTo("education");
    assertThat(result.toPillar()).isEqualTo("community");
    assertThat(result.points()).isEqualTo(5);
    assertThat(result.citations())
        .containsExactlyInAnyOrderElementsOf(
            rows.stream().map(RecommendationInput.Metric::observationId).toList());
  }

  @Test
  void FourVersusSixDoesNotMasqueradeAsFivePerCohort() {
    var rows = new ArrayList<RecommendationInput.Metric>();
    for (int i = 0; i < 10; i++)
      rows.add(
          new RecommendationInput.Metric(
              UUID.randomUUID(), i < 4 ? "education" : "community", 100, i < 4 ? 10 : 30));
    assertThatThrownBy(() -> new RuleRecommendationEngine().recommend(input(rows)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void twoPercentagePointsAreAnExplicitBoundary() {
    for (int n : new int[] {11, 12}) {
      var rows = new ArrayList<RecommendationInput.Metric>();
      for (int i = 0; i < 5; i++) {
        rows.add(new RecommendationInput.Metric(UUID.randomUUID(), "education", 100, 10));
        rows.add(new RecommendationInput.Metric(UUID.randomUUID(), "community", 100, n));
      }
      if (n == 11)
        assertThatThrownBy(() -> new RuleRecommendationEngine().recommend(input(rows)))
            .isInstanceOf(IllegalArgumentException.class);
      else
        assertThat(new RuleRecommendationEngine().recommend(input(rows)).toPillar())
            .isEqualTo("community");
    }
  }
}
