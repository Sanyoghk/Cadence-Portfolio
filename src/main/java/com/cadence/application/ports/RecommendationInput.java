package com.cadence.application.ports;

import com.cadence.domain.*;
import java.time.*;
import java.util.*;

/**
 * Minimal local rule/model input. Audit actors, tenant IDs and private references stay server-side.
 */
public record RecommendationInput(
    Instant windowFrom,
    Instant windowUntil,
    List<Metric> metrics,
    List<PillarWeight> pillars,
    RecommendationProposal expected) {
  public record Metric(UUID observationId, String pillarId, long reach, long engagement) {
    public Metric {
      if (observationId == null
          || pillarId == null
          || !pillarId.matches("[A-Za-z0-9_-]{1,64}")
          || reach < 1
          || reach > MetricRow.MAX_COUNT
          || engagement < 0
          || engagement > MetricRow.MAX_COUNT)
        throw new IllegalArgumentException("Invalid recommendation metric input");
    }
  }

  public record PillarWeight(String id, int weight) {
    public PillarWeight {
      if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || weight < 0 || weight > 100)
        throw new IllegalArgumentException("Invalid recommendation pillar input");
    }
  }

  public RecommendationInput {
    if (windowFrom == null
        || windowUntil == null
        || !windowFrom.isBefore(windowUntil)
        || Duration.between(windowFrom, windowUntil).compareTo(Duration.ofDays(29)) > 0
        || metrics == null
        || metrics.size() < 10
        || metrics.size() > 100
        || pillars == null
        || pillars.size() < 2
        || pillars.size() > 10
        || expected == null)
      throw new IllegalArgumentException("Invalid bounded recommendation input");
    metrics = List.copyOf(metrics);
    pillars = List.copyOf(pillars);
    if (metrics.stream().map(Metric::observationId).distinct().count() != metrics.size()
        || !new HashSet<>(metrics.stream().map(Metric::observationId).toList())
            .equals(new HashSet<>(expected.citations()))
        || pillars.stream().mapToInt(PillarWeight::weight).sum() != 100
        || pillars.stream().map(PillarWeight::id).distinct().count() != pillars.size())
      throw new IllegalArgumentException("Invalid recommendation input binding");
  }

  public static RecommendationInput from(RecommendationCommand c) {
    var weights = new ArrayList<PillarWeight>();
    for (int i = 0; i < c.playbook().pillarIds().size(); i++)
      weights.add(
          new PillarWeight(c.playbook().pillarIds().get(i), c.content().pillars().get(i).weight()));
    return new RecommendationInput(
        c.query().from(),
        c.query().until(),
        c.sources().stream()
            .map(s -> new Metric(s.observationId(), s.pillarId(), s.reach(), s.engagement()))
            .toList(),
        weights,
        c.expected());
  }
}
