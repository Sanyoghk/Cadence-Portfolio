package com.cadence.domain;

import java.util.*;

/** A hypothesis, never a causal claim or arbitrary executable/model text. */
public record RecommendationProposal(
    String fromPillar, String toPillar, int points, List<UUID> citations) {
  public RecommendationProposal {
    if (fromPillar == null
        || toPillar == null
        || fromPillar.equals(toPillar)
        || !fromPillar.matches("[A-Za-z0-9_-]{1,64}")
        || !toPillar.matches("[A-Za-z0-9_-]{1,64}")
        || points != 5
        || citations == null
        || citations.size() < 10
        || citations.size() > 100
        || citations.stream().anyMatch(Objects::isNull)
        || new HashSet<>(citations).size() != citations.size())
      throw new IllegalArgumentException("Invalid recommendation proposal");
    citations = List.copyOf(citations);
  }

  public StrategyContent apply(StrategyContent content, PlaybookContent playbook) {
    int from = playbook.pillarIds().indexOf(fromPillar),
        to = playbook.pillarIds().indexOf(toPillar);
    if (from < 0
        || to < 0
        || content.pillars().get(from).weight() < points
        || content.pillars().get(to).weight() > 100 - points)
      throw new IllegalStateException("Recommendation patch unavailable");
    var pillars = new ArrayList<>(content.pillars());
    for (int i : new int[] {from, to}) {
      var p = pillars.get(i);
      pillars.set(
          i,
          new StrategyContent.Pillar(
              p.name(), p.weight() + (i == from ? -points : points), p.description()));
    }
    return new StrategyContent(
        pillars, content.postsPerWeek(), content.timezone(), content.bestTimes());
  }
}
