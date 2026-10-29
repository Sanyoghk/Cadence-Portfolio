package com.cadence.domain;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

/** Complete immutable editable content; validated independently of HTTP/provider input. */
public record StrategyContent(
    List<Pillar> pillars, int postsPerWeek, String timezone, List<LocalTime> bestTimes) {
  public record Pillar(String name, int weight, String description) {
    public Pillar {
      name = text(name, 255);
      description = text(description, 1000);
      if (weight < 0 || weight > 100) throw new IllegalArgumentException("Invalid pillar weight");
    }
  }

  public StrategyContent {
    if (pillars == null
        || pillars.isEmpty()
        || pillars.size() > 10
        || pillars.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid strategy pillars");
    pillars = List.copyOf(pillars);
    if (pillars.stream().mapToInt(Pillar::weight).sum() != 100
        || pillars.stream().map(p -> p.name().toLowerCase(Locale.ROOT)).distinct().count()
            != pillars.size()) throw new IllegalArgumentException("Invalid pillar allocation");
    if (postsPerWeek < 1 || postsPerWeek > 21)
      throw new IllegalArgumentException("Invalid publishing frequency");
    timezone = text(timezone, 100);
    if (!ZoneId.getAvailableZoneIds().contains(timezone))
      throw new IllegalArgumentException("Invalid publishing timezone");
    if (bestTimes == null
        || bestTimes.isEmpty()
        || bestTimes.size() > 7
        || bestTimes.stream().anyMatch(t -> t == null || t.getNano() % 1000 != 0)
        || bestTimes.stream().distinct().count() != bestTimes.size())
      throw new IllegalArgumentException("Invalid publishing times");
    bestTimes = List.copyOf(bestTimes);
  }

  private static String text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > max
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid strategy text");
    return value.strip();
  }

  public static StrategyContent from(Strategy strategy) {
    var cadence = strategy.getCadence();
    return new StrategyContent(
        strategy.getContentPillars().stream()
            .map(p -> new Pillar(p.getName(), p.getWeight(), p.getDescription()))
            .toList(),
        cadence.getPostsPerWeek(),
        cadence.getTimezone(),
        cadence.getBestTimes());
  }

  PublishingCadence cadence() {
    return new PublishingCadence(postsPerWeek, timezone, bestTimes);
  }

  List<ContentPillar> domainPillars() {
    return pillars.stream()
        .map(p -> new ContentPillar(p.name(), p.weight(), p.description()))
        .toList();
  }
}
