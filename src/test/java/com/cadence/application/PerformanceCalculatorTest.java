package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.services.PerformanceCalculator;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PerformanceCalculatorTest {
  final PerformanceCalculator calculator = new PerformanceCalculator();
  final Instant now = Instant.parse("2026-01-10T12:00:00Z"),
      from = Instant.parse("2026-01-01T00:00:00Z"),
      until = Instant.parse("2026-01-02T00:00:00Z");

  PerformanceQuery q() {
    return new PerformanceQuery(
        LocalDate.of(2026, 1, 1),
        LocalDate.of(2026, 1, 2),
        "UTC",
        PerformanceQuery.Dimension.PILLAR,
        PlaybookContent.Channel.INSTAGRAM,
        0,
        50);
  }

  PerformanceSample sample(UUID strategy, Instant observed) {
    return new PerformanceSample(
        UUID.randomUUID(),
        UUID.randomUUID(),
        PlaybookContent.Channel.INSTAGRAM,
        from,
        until,
        observed,
        now,
        1,
        "a".repeat(64),
        UUID.randomUUID(),
        "b".repeat(64),
        "MANUAL_JSON",
        UUID.randomUUID(),
        strategy,
        1,
        "c".repeat(64),
        UUID.randomUUID(),
        from.minusSeconds(1),
        UUID.randomUUID(),
        1,
        UUID.randomUUID(),
        1L,
        "d".repeat(64),
        PostFormat.REEL,
        "education",
        "Education",
        "HUMAN",
        UUID.randomUUID(),
        now,
        100L,
        10L,
        1L,
        1L,
        2L);
  }

  @Test
  void freshnessBoundaryIsExclusiveAndDatabaseAsOfControlsState() {
    for (int i = 0; i < 2; i++) {
      var s =
          sample(
              UUID.randomUUID(),
              now.minusSeconds(PerformanceMetrics.STALE_SECONDS).minusNanos(i * 1000));
      var r = calculator.calculate(UUID.randomUUID(), UUID.randomUUID(), q(), now, List.of(s));
      assertThat(r.items().getFirst().staleRows()).isEqualTo(i);
      assertThat(
              r.items()
                  .getFirst()
                  .rates()
                  .getFirst()
                  .issues()
                  .contains(PerformanceMetrics.Issue.STALE))
          .isEqualTo(i == 1);
    }
  }

  @Test
  void samePillarNameInDifferentSourceStrategiesNeverMerges() {
    var a = UUID.randomUUID();
    var b = UUID.randomUUID();
    var r =
        calculator.calculate(
            UUID.randomUUID(),
            UUID.randomUUID(),
            q(),
            now,
            List.of(sample(a, until), sample(b, until)));
    assertThat(r.items()).hasSize(2);
    assertThat(r.items().stream().map(g -> g.pillarStrategyId())).containsExactlyInAnyOrder(a, b);
    assertThat(r.items().get(0).comparisonKey()).isNotEqualTo(r.items().get(1).comparisonKey());
  }

  @Test
  void overCapRejectsInsteadOfReturningPartialAggregate() {
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    q(),
                    now,
                    Collections.nCopies(1001, sample(UUID.randomUUID(), until))))
        .isInstanceOf(IllegalStateException.class);
  }
}
