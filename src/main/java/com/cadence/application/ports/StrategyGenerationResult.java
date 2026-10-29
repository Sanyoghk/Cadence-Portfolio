package com.cadence.application.ports;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

public record StrategyGenerationResult(
    List<Pillar> pillars,
    int postsPerWeek,
    String timezone,
    List<LocalTime> bestTimes,
    List<Warning> warnings,
    String provider,
    String modelVersion,
    String promptVersion,
    com.cadence.domain.PlaybookContent playbook) {
  /** Legacy result representation retained for historical fixtures; new publication requires v2. */
  public StrategyGenerationResult(
      List<Pillar> pillars,
      int postsPerWeek,
      String timezone,
      List<LocalTime> bestTimes,
      List<Warning> warnings,
      String provider,
      String modelVersion,
      String promptVersion) {
    this(
        pillars,
        postsPerWeek,
        timezone,
        bestTimes,
        warnings,
        provider,
        modelVersion,
        promptVersion,
        null);
  }

  public com.cadence.domain.StrategyContent content() {
    return new com.cadence.domain.StrategyContent(
        pillars.stream()
            .map(
                p ->
                    new com.cadence.domain.StrategyContent.Pillar(
                        p.name(), p.weight(), p.description()))
            .toList(),
        postsPerWeek,
        timezone,
        bestTimes);
  }

  public void requireCompletePlaybook() {
    if (playbook == null) throw new IllegalArgumentException("Complete Playbook required");
    playbook.validateWith(content());
  }

  public enum Warning {
    DEMONSTRATION_ONLY,
    HUMAN_REVIEW_REQUIRED
  }

  public record Pillar(String name, int weight, String description) {
    public Pillar {
      name = text(name, 255);
      description = text(description, 1000);
      if (weight < 0 || weight > 100) throw new IllegalArgumentException("Invalid pillar weight");
    }
  }

  public StrategyGenerationResult {
    if (pillars == null
        || pillars.isEmpty()
        || pillars.size() > 10
        || pillars.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid content pillars");
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
    if (warnings == null || warnings.size() > 10 || warnings.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid strategy warnings");
    warnings = List.copyOf(warnings);
    provider = identifier(provider);
    modelVersion = identifier(modelVersion);
    promptVersion = identifier(promptVersion);
    if (playbook != null)
      playbook.validateWith(
          new com.cadence.domain.StrategyContent(
              pillars.stream()
                  .map(
                      p ->
                          new com.cadence.domain.StrategyContent.Pillar(
                              p.name(), p.weight(), p.description()))
                  .toList(),
              postsPerWeek,
              timezone,
              bestTimes));
  }

  private static String text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > max
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid strategy text");
    return value.strip();
  }

  private static String identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
      throw new IllegalArgumentException("Invalid strategy provenance");
    return value;
  }
}
