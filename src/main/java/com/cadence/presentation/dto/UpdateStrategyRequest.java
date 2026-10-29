package com.cadence.presentation.dto;

import com.cadence.domain.StrategyContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalTime;
import java.util.List;

/** Only editable content and its concurrency token; no status, audit or provenance fields. */
public record UpdateStrategyRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull @Valid Cadence cadence,
    @NotNull @Size(min = 1, max = 10) List<@NotNull @Valid Pillar> contentPillars,
    com.cadence.domain.PlaybookContent playbook) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void unknown(String key, Object value) {
    throw new IllegalArgumentException("Unknown Strategy field");
  }

  public record Cadence(
      @NotNull @Min(1) @Max(21) Integer postsPerWeek,
      @NotBlank @Size(max = 100) String timezone,
      @NotNull @Size(min = 1, max = 7) List<@NotNull LocalTime> bestTimes)
      implements com.cadence.domain.StrictPlaybookValue {}

  public record Pillar(
      @NotBlank @Size(max = 255) String name,
      @NotNull @Min(0) @Max(100) Integer weight,
      @NotBlank @Size(max = 1000) String description)
      implements com.cadence.domain.StrictPlaybookValue {}

  public StrategyContent content() {
    return new StrategyContent(
        contentPillars.stream()
            .map(p -> new StrategyContent.Pillar(p.name(), p.weight(), p.description()))
            .toList(),
        cadence.postsPerWeek(),
        cadence.timezone(),
        cadence.bestTimes());
  }
}
