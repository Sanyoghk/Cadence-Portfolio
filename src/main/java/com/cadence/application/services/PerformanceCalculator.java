package com.cadence.application.services;

import com.cadence.application.results.PerformanceReport;
import com.cadence.application.results.PerformanceReport.*;
import com.cadence.domain.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.zone.ZoneRulesProvider;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PerformanceCalculator {
  public record Calculation(
      List<PerformanceGroup> groups, int selected, int duplicates, int incompatible) {}

  private record Key(
      PerformanceQuery.Window window,
      PlaybookContent.Channel platform,
      UUID strategy,
      Integer strategyRevision,
      String pillar,
      String pillarName,
      PostFormat format) {
    String stable() {
      return window.date() + "|" + platform + "|" + strategy + "|" + pillar + "|" + format;
    }
  }

  public PerformanceReport calculate(
      UUID brand,
      UUID workspace,
      PerformanceQuery q,
      Instant now,
      List<PerformanceSample> candidates) {
    if (candidates == null || candidates.size() > 1000)
      throw new IllegalStateException("Performance candidate cap exceeded");
    var windows = q.windows();
    var grouped = new HashMap<Key, List<PerformanceSample>>();
    var chosen = new HashMap<String, PerformanceSample>();
    int incompatible = 0, duplicates = 0;
    for (var s : candidates) {
      if (s.observedAt().isAfter(now)) throw new IllegalStateException("Future performance source");
      var w =
          windows.stream()
              .filter(v -> v.from().equals(s.windowFrom()) && v.until().equals(s.windowUntil()))
              .findFirst()
              .orElse(null);
      if (w == null) {
        incompatible++;
        continue;
      }
      String key = s.postId() + "|" + s.windowFrom();
      var old = chosen.get(key);
      if (old != null) {
        duplicates++;
        if (s.observedAt().isBefore(old.observedAt())
            || (s.observedAt().equals(old.observedAt())
                && s.revisionNumber() <= old.revisionNumber())) continue;
      }
      chosen.put(key, s);
    }
    for (var s : chosen.values()) {
      var w =
          windows.stream().filter(v -> v.from().equals(s.windowFrom())).findFirst().orElseThrow();
      boolean pillar = q.dimension() == PerformanceQuery.Dimension.PILLAR,
          format = q.dimension() == PerformanceQuery.Dimension.FORMAT;
      var k =
          new Key(
              w,
              s.platform(),
              pillar && s.pillarId() != null ? s.strategyId() : null,
              pillar && s.pillarId() != null ? s.strategyRevision() : null,
              pillar ? s.pillarId() : null,
              pillar ? s.pillarName() : null,
              format ? s.format() : null);
      grouped.computeIfAbsent(k, k2 -> new ArrayList<>()).add(s);
    }
    // Empty daily/platform buckets make absence explicit; never manufacture counters.
    for (var w : windows)
      for (var p : PlaybookContent.Channel.values())
        if ((q.platform() == null || q.platform() == p)
            && grouped.keySet().stream().noneMatch(k -> k.window().equals(w) && k.platform() == p))
          grouped.put(new Key(w, p, null, null, null, null, null), List.of());
    var groups = new ArrayList<PerformanceGroup>();
    var checksum = new StringBuilder("performance-v1|").append(q).append('|').append(now);
    candidates.stream()
        .sorted(Comparator.comparing(PerformanceSample::observationId))
        .forEach(
            s ->
                checksum
                    .append("|candidate:")
                    .append(s.observationId())
                    .append(':')
                    .append(s.valueChecksum())
                    .append(':')
                    .append(s.attributionId()));
    grouped.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getKey().stable()))
        .forEach(
            e -> {
              var k = e.getKey();
              var rows =
                  e.getValue().stream()
                      .sorted(Comparator.comparing(PerformanceSample::postId))
                      .toList();
              boolean unknown =
                  (q.dimension() == PerformanceQuery.Dimension.PILLAR && k.pillar() == null)
                      || (q.dimension() == PerformanceQuery.Dimension.FORMAT && k.format() == null);
              var rates = new ArrayList<PerformanceMetrics.PerformanceRate>();
              for (var f : PerformanceMetrics.Formula.values()) {
                var pairs =
                    rows.stream()
                        .map(
                            s ->
                                new PerformanceMetrics.Pair(
                                    s.postId(),
                                    switch (f) {
                                      case ENGAGEMENT_PER_REACH -> s.engagement();
                                      case SAVES_PER_REACH -> s.saves();
                                      case CLICKS_PER_REACH -> s.profileToSiteClicks();
                                      case SIGNUPS_PER_CLICK -> s.signups();
                                    },
                                    f == PerformanceMetrics.Formula.SIGNUPS_PER_CLICK
                                        ? s.profileToSiteClicks()
                                        : s.reach(),
                                    stale(s, now)))
                        .toList();
                rates.add(PerformanceMetrics.calculate(f, pairs, unknown));
              }
              rows.forEach(
                  s ->
                      checksum
                          .append('|')
                          .append(s.observationId())
                          .append(':')
                          .append(s.valueChecksum())
                          .append(':')
                          .append(s.attributionId()));
              var comparison =
                  "performance-v1|"
                      + q.bucket()
                      + "|"
                      + q.timezone()
                      + "|"
                      + ZoneRulesProvider.getVersions(q.timezone()).lastKey()
                      + "|"
                      + k.platform()
                      + "|"
                      + k.window().seconds()
                      + "|"
                      + q.dimension()
                      + (q.dimension() == PerformanceQuery.Dimension.PILLAR
                          ? "|" + k.strategy() + "|" + k.strategyRevision()
                          : "");
              groups.add(
                  new PerformanceGroup(
                      k.window().date(),
                      k.window().from(),
                      k.window().until(),
                      k.window().seconds(),
                      k.platform(),
                      k.strategy(),
                      k.strategyRevision(),
                      k.pillar(),
                      k.pillarName(),
                      k.format(),
                      rows.size(),
                      (int) rows.stream().filter(s -> stale(s, now)).count(),
                      comparison,
                      List.copyOf(rates),
                      rows.stream().map(PerformanceContribution::from).toList()));
            });
    int start = Math.min(groups.size(), Math.multiplyExact(q.page(), q.size()));
    int end = Math.min(groups.size(), start + q.size());
    return new PerformanceReport(
        "performance-v1",
        brand,
        workspace,
        q.timezone(),
        ZoneRulesProvider.getVersions(q.timezone()).lastKey(),
        q.fromDate(),
        q.untilDate(),
        now,
        q.dimension(),
        q.bucket(),
        q.page(),
        q.size(),
        groups.size(),
        candidates.size(),
        chosen.size(),
        duplicates,
        incompatible,
        q.skippedLocalDays(),
        PerformanceMetrics.STALE_SECONDS,
        MetricRow.sha(checksum.toString().getBytes(StandardCharsets.UTF_8)),
        List.of(
            "HUMAN_ASSERTED_NOT_PROVIDER_VERIFIED",
            "REACH_SUM_NOT_UNIQUE_AUDIENCE",
            "CO_REPORTED_COUNTS_NOT_CAUSAL_CONVERSION",
            "DESCRIPTIVE_NOT_STATISTICAL_SIGNIFICANCE",
            "ONLY_EXACT_BUCKET_WINDOWS",
            "POST_EXPOSURE_AGE_UNKNOWN",
            "COMPARE_ONLY_EQUAL_COMPARISON_KEYS",
            "UNKNOWN_ATTRIBUTION_NOT_CURRENT_CONTENT",
            "HISTORICAL_VALUES_MAY_BE_STALE"),
        List.copyOf(groups.subList(start, end)));
  }

  private static boolean stale(PerformanceSample s, Instant now) {
    return s.observedAt().isBefore(now.minusSeconds(PerformanceMetrics.STALE_SECONDS));
  }
}
