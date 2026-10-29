package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StrategyGenerationContractTest {
  private StrategyGenerationResult result(
      List<StrategyGenerationResult.Pillar> pillars,
      int frequency,
      String timezone,
      List<LocalTime> times) {
    return new StrategyGenerationResult(
        pillars, frequency, timezone, times, List.of(), "provider", "model-v1", "prompt-v1");
  }

  private final StrategyGenerationResult.Pillar pillar =
      new StrategyGenerationResult.Pillar("Education", 100, "Teach");

  @Test
  void rejectsInvalidAllocationAndDuplicateNames() {
    for (var values :
        List.of(
            List.<StrategyGenerationResult.Pillar>of(),
            Collections.nCopies(11, pillar),
            List.of(new StrategyGenerationResult.Pillar("A", 50, "A")),
            List.of(
                new StrategyGenerationResult.Pillar("A", 50, "A"),
                new StrategyGenerationResult.Pillar("a", 50, "B"))))
      assertThatThrownBy(() -> result(values, 3, "UTC", List.of(LocalTime.NOON)))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsOversizedTextInvalidCadenceAndTimezone() {
    assertThatThrownBy(() -> new StrategyGenerationResult.Pillar("A", 100, "x".repeat(1001)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StrategyGenerationResult.Pillar("A", 101, "A"))
        .isInstanceOf(IllegalArgumentException.class);
    for (int n : List.of(0, 22, Integer.MAX_VALUE))
      assertThatThrownBy(() -> result(List.of(pillar), n, "UTC", List.of(LocalTime.NOON)))
          .isInstanceOf(IllegalArgumentException.class);
    for (String zone : List.of("Not/A_Zone", " ", "UTC\u0000"))
      assertThatThrownBy(() -> result(List.of(pillar), 3, zone, List.of(LocalTime.NOON)))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsEmptyDuplicateAndUnrepresentablePublishingTimes() {
    for (var times :
        List.of(
            List.<LocalTime>of(),
            List.of(LocalTime.NOON, LocalTime.NOON),
            Collections.nCopies(8, LocalTime.NOON),
            List.of(LocalTime.of(12, 0, 0, 1))))
      assertThatThrownBy(() -> result(List.of(pillar), 3, "UTC", times))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void outputHasNoMutableAliasesOrStatusField() {
    var source = new ArrayList<>(List.of(pillar));
    var times = new ArrayList<>(List.of(LocalTime.NOON));
    var r = result(source, 3, "UTC", times);
    source.clear();
    times.clear();
    assertThat(r.pillars()).containsExactly(pillar);
    assertThat(r.bestTimes()).containsExactly(LocalTime.NOON);
    assertThatThrownBy(() -> r.pillars().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThat(
            Arrays.stream(StrategyGenerationResult.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
        .doesNotContain("status", "approved");
  }

  @Test
  void missingSourceAndControlCharactersAreRejected() {
    assertThatThrownBy(
            () ->
                new StrategyGenerationCommand(
                    UUID.randomUUID(), 0, 0, "Brand", "Creator", null, Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new StrategyGenerationResult.Pillar("unsafe\u0000name", 100, "Description"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
