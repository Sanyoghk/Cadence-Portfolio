package com.cadence.application.results;

import com.cadence.domain.ApprovalStatus;
import com.cadence.domain.Strategy;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Immutable read snapshot; all lazy values are copied inside the application transaction. */
public record StrategyDetails(
    UUID id,
    UUID brandId,
    UUID workspaceId,
    int version,
    long lockVersion,
    ApprovalStatus status,
    UUID revisedFromId,
    UUID approvedBy,
    Instant approvedAt,
    Cadence cadence,
    List<Pillar> contentPillars,
    Generation generation,
    Instant createdAt,
    Instant updatedAt,
    int contentFormatVersion,
    String completeness,
    com.cadence.domain.PlaybookContent playbook,
    UUID editedBy,
    Instant editedAt,
    boolean readyContentSource) {
  public record Cadence(int postsPerWeek, String timezone, List<LocalTime> bestTimes) {
    public Cadence {
      bestTimes = List.copyOf(bestTimes);
    }
  }

  public record Pillar(String name, int weight, String description) {}

  public record Generation(
      UUID id,
      String provider,
      String modelVersion,
      String promptVersion,
      List<String> warnings,
      com.cadence.domain.ApprovedIdentitySource identitySource,
      Integer contentFormatVersion,
      String outputChecksum) {
    public Generation {
      warnings = List.copyOf(warnings);
    }
  }

  public StrategyDetails {
    contentPillars = List.copyOf(contentPillars);
  }

  public static StrategyDetails from(Strategy strategy) {
    if (strategy.getContentFormatVersion() == 2) strategy.validateContent();
    var cadence = strategy.getCadence();
    var generation = strategy.getGeneration();
    return new StrategyDetails(
        strategy.getId(),
        strategy.getBrand().getId(),
        strategy.getBrand().getWorkspace().getId(),
        strategy.getVersion(),
        strategy.getLockVersion(),
        strategy.getStatus(),
        strategy.getRevisedFromId(),
        strategy.getApprovedBy(),
        strategy.getApprovedAt(),
        new Cadence(cadence.getPostsPerWeek(), cadence.getTimezone(), cadence.getBestTimes()),
        strategy.getContentPillars().stream()
            .map(p -> new Pillar(p.getName(), p.getWeight(), p.getDescription()))
            .toList(),
        generation == null
            ? null
            : new Generation(
                generation.getId(),
                generation.getProvider(),
                generation.getModelVersion(),
                generation.getPromptVersion(),
                generation.getWarnings().isEmpty()
                    ? List.of()
                    : List.of(generation.getWarnings().split(",")),
                generation.getIdentitySource(),
                generation.getContentFormat(),
                generation.getOutputChecksum()),
        strategy.getCreatedAt(),
        strategy.getUpdatedAt(),
        strategy.getContentFormatVersion(),
        strategy.getContentFormatVersion() == 2 ? "COMPLETE" : "LEGACY_PARTIAL",
        strategy.getPlaybook(),
        strategy.getEditedBy(),
        strategy.getEditedAt(),
        strategy.isReadyContentSource());
  }
}
