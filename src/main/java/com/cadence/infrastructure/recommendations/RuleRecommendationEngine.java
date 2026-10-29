package com.cadence.infrastructure.recommendations;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import org.springframework.stereotype.Component;

/** Pure local heuristic. Never presented as live AI inference. */
@Component
public class RuleRecommendationEngine implements RecommendationEngine {
  public Attribution attribution() {
    return new Attribution("HEURISTIC", "pillar-shift-v1", "cadence-rules", null, null);
  }

  public RecommendationProposal recommend(RecommendationInput input) {
    var grouped =
        input.metrics().stream()
            .collect(java.util.stream.Collectors.groupingBy(RecommendationInput.Metric::pillarId));
    if (grouped.size() != 2)
      throw new IllegalArgumentException("Two comparable pillar cohorts required");
    var rates = new java.util.HashMap<String, java.math.BigDecimal>();
    grouped.forEach(
        (id, rows) -> {
          long n = 0, d = 0;
          for (var row : rows) {
            n = Math.addExact(n, row.engagement());
            d = Math.addExact(d, row.reach());
          }
          if (rows.size() < 5 || d < 100)
            throw new IllegalArgumentException("Insufficient recommendation sample");
          rates.put(
              id,
              java.math.BigDecimal.valueOf(n)
                  .multiply(java.math.BigDecimal.valueOf(100))
                  .divide(java.math.BigDecimal.valueOf(d), 4, java.math.RoundingMode.HALF_UP));
        });
    var ordered =
        rates.keySet().stream()
            .sorted(
                java.util.Comparator.comparing((String id) -> rates.get(id))
                    .thenComparing(id -> id))
            .toList();
    String low = ordered.getFirst(), high = ordered.getLast();
    if (rates.get(high).subtract(rates.get(low)).compareTo(java.math.BigDecimal.valueOf(2)) < 0
        || input.pillars().stream().noneMatch(p -> p.id().equals(low) && p.weight() >= 5)
        || input.pillars().stream().noneMatch(p -> p.id().equals(high) && p.weight() <= 95))
      throw new IllegalArgumentException("No bounded pillar hypothesis");
    return new RecommendationProposal(
        low,
        high,
        5,
        input.metrics().stream().map(RecommendationInput.Metric::observationId).sorted().toList());
  }
}
