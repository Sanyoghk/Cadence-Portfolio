package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PerformanceMetricsTest {
  @Test
  void independentlyComputedWeightedRatioNotAverageOfRates() {
    var rows = new ArrayList<PerformanceMetrics.Pair>();
    long[] n = {1, 2, 9, 8, 5}, d = {10, 20, 30, 40, 50};
    for (int i = 0; i < 5; i++)
      rows.add(new PerformanceMetrics.Pair(UUID.randomUUID(), n[i], d[i], false));
    var r =
        PerformanceMetrics.calculate(PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH, rows, false);
    assertThat(r.numeratorTotal()).isEqualTo(25);
    assertThat(r.denominatorTotal()).isEqualTo(150);
    assertThat(r.percent()).isEqualByComparingTo("16.6667");
    assertThat(r.comparisonEligible()).isTrue();
  }

  @Test
  void unknownZeroAndInconsistentAreNotInventedValues() {
    var rows =
        List.of(
            new PerformanceMetrics.Pair(UUID.randomUUID(), null, 20L, false),
            new PerformanceMetrics.Pair(UUID.randomUUID(), 0L, 0L, false),
            new PerformanceMetrics.Pair(UUID.randomUUID(), 1L, 0L, false));
    var r =
        PerformanceMetrics.calculate(PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH, rows, true);
    assertThat(r.percent()).isNull();
    assertThat(r.numeratorTotal()).isZero();
    assertThat(r.denominatorTotal()).isZero();
    assertThat(r.knownPairs()).isEqualTo(2);
    assertThat(r.missingPairs()).isEqualTo(1);
    assertThat(r.inconsistentPairs()).isEqualTo(1);
    assertThat(r.issues())
        .contains(
            PerformanceMetrics.Issue.MISSING,
            PerformanceMetrics.Issue.ZERO_DENOMINATOR,
            PerformanceMetrics.Issue.INCONSISTENT_PAIR,
            PerformanceMetrics.Issue.UNKNOWN_ATTRIBUTION);
  }

  @Test
  void emptyAndAllUnknownRemainNull() {
    for (var rows :
        List.of(
            List.<PerformanceMetrics.Pair>of(),
            List.of(new PerformanceMetrics.Pair(UUID.randomUUID(), null, null, false)))) {
      var r = PerformanceMetrics.calculate(PerformanceMetrics.Formula.SAVES_PER_REACH, rows, false);
      assertThat(r.numeratorTotal()).isNull();
      assertThat(r.percent()).isNull();
      assertThat(r.issues())
          .contains(PerformanceMetrics.Issue.MISSING, PerformanceMetrics.Issue.SPARSE);
    }
  }

  @Test
  void pairCoverageAndStaleGuard() {
    var rows = new ArrayList<PerformanceMetrics.Pair>();
    for (int i = 0; i < 5; i++)
      rows.add(new PerformanceMetrics.Pair(UUID.randomUUID(), 0L, 20L, i == 0));
    var r = PerformanceMetrics.calculate(PerformanceMetrics.Formula.SIGNUPS_PER_CLICK, rows, false);
    assertThat(r.percent()).isEqualByComparingTo("0.0000");
    assertThat(r.usablePosts()).isEqualTo(5);
    assertThat(r.issues()).containsExactly(PerformanceMetrics.Issue.STALE);
    assertThat(r.comparisonEligible()).isFalse();
  }

  @Test
  void countThresholdAndPostThresholdIndependent() {
    var five =
        java.util.stream.IntStream.range(0, 5)
            .mapToObj(i -> new PerformanceMetrics.Pair(UUID.randomUUID(), 1L, 19L, false))
            .toList();
    assertThat(
            PerformanceMetrics.calculate(
                    PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH, five, false)
                .issues())
        .contains(PerformanceMetrics.Issue.SPARSE);
    assertThat(
            PerformanceMetrics.calculate(
                    PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH,
                    List.of(new PerformanceMetrics.Pair(UUID.randomUUID(), 10L, 1000L, false)),
                    false)
                .issues())
        .contains(PerformanceMetrics.Issue.SPARSE);
  }

  @Test
  void maximumTotalsAndAboveHundredDoNotOverflowOrClamp() {
    var rows =
        java.util.stream.IntStream.range(0, 1000)
            .mapToObj(
                i -> new PerformanceMetrics.Pair(UUID.randomUUID(), MetricRow.MAX_COUNT, 1L, false))
            .toList();
    var r =
        PerformanceMetrics.calculate(PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH, rows, false);
    assertThat(r.numeratorTotal()).isEqualTo(1_000_000_000_000_000L);
    assertThat(r.percent()).isEqualByComparingTo("100000000000000.0000");
    assertThat(r.comparisonEligible()).isTrue();
    for (Long n : List.of(-1L, Long.MAX_VALUE))
      assertThatThrownBy(
              () ->
                  PerformanceMetrics.calculate(
                      PerformanceMetrics.Formula.ENGAGEMENT_PER_REACH,
                      List.of(new PerformanceMetrics.Pair(UUID.randomUUID(), n, 1L, false)),
                      false))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void duplicatePostsAndOverCapDenied() {
    var p = new PerformanceMetrics.Pair(UUID.randomUUID(), 1L, 20L, false);
    assertThatThrownBy(
            () ->
                PerformanceMetrics.calculate(
                    PerformanceMetrics.Formula.SAVES_PER_REACH, List.of(p, p), false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                PerformanceMetrics.calculate(
                    PerformanceMetrics.Formula.SAVES_PER_REACH,
                    Collections.nCopies(1001, p),
                    false))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void timezoneDstAndSkippedDayWindowsAreExplicit() {
    var gap =
        new PerformanceQuery(
                LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 10),
                "America/New_York",
                PerformanceQuery.Dimension.PLATFORM,
                null,
                0,
                50)
            .windows();
    assertThat(gap.stream().map(PerformanceQuery.Window::seconds)).containsExactly(82800L, 86400L);
    assertThat(gap.getFirst().from()).isEqualTo(Instant.parse("2026-03-08T05:00:00Z"));
    assertThat(
            new PerformanceQuery(
                    LocalDate.of(2025, 11, 2),
                    LocalDate.of(2025, 11, 3),
                    "America/New_York",
                    PerformanceQuery.Dimension.PLATFORM,
                    null,
                    0,
                    50)
                .windows()
                .getFirst()
                .seconds())
        .isEqualTo(90000);
    assertThat(
            new PerformanceQuery(
                    LocalDate.of(2011, 12, 30),
                    LocalDate.of(2011, 12, 31),
                    "Pacific/Apia",
                    PerformanceQuery.Dimension.PLATFORM,
                    null,
                    0,
                    50)
                .windows())
        .isEmpty();
  }

  @Test
  void boundsAndZoneAllowlist() {
    assertThatThrownBy(
            () ->
                new PerformanceQuery(
                    LocalDate.of(2026, 1, 1),
                    LocalDate.of(2026, 2, 1),
                    "UTC",
                    PerformanceQuery.Dimension.FORMAT,
                    null,
                    0,
                    50))
        .isInstanceOf(IllegalArgumentException.class);
    for (var z : List.of("../UTC", "+03:00", "not-a-zone"))
      assertThatThrownBy(
              () ->
                  new PerformanceQuery(
                      LocalDate.of(2026, 1, 1),
                      LocalDate.of(2026, 1, 2),
                      z,
                      PerformanceQuery.Dimension.FORMAT,
                      null,
                      0,
                      50))
          .isInstanceOf(IllegalArgumentException.class);
  }
}
