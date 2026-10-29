package com.cadence.domain;

import java.math.*;
import java.time.*;
import java.util.*;

public final class PerformanceMetrics {
  private PerformanceMetrics() {}

  public static final int MIN_POSTS = 5;
  public static final long STALE_SECONDS = 7 * 86400;

  public enum Formula {
    ENGAGEMENT_PER_REACH,
    SAVES_PER_REACH,
    CLICKS_PER_REACH,
    SIGNUPS_PER_CLICK
  }

  public enum Issue {
    MISSING,
    SPARSE,
    ZERO_DENOMINATOR,
    INCONSISTENT_PAIR,
    STALE,
    UNKNOWN_ATTRIBUTION
  }

  public record Pair(UUID postId, Long numerator, Long denominator, boolean stale) {}

  public record PerformanceRate(
      Formula formula,
      String unit,
      BigDecimal percent,
      Long numeratorTotal,
      Long denominatorTotal,
      int knownPairs,
      int usablePosts,
      int missingPairs,
      int zeroDenominatorPairs,
      int inconsistentPairs,
      int stalePairs,
      int minimumPosts,
      long minimumDenominator,
      boolean comparisonEligible,
      List<Issue> issues) {}

  public static PerformanceRate calculate(
      Formula formula, List<Pair> pairs, boolean unknownAttribution) {
    if (formula == null
        || pairs == null
        || pairs.size() > 1000
        || pairs.stream().anyMatch(Objects::isNull)
        || pairs.stream().map(Pair::postId).distinct().count() != pairs.size())
      throw new IllegalArgumentException("Invalid bounded metric sample");
    long numerator = 0, denominator = 0;
    int known = 0, missing = 0, zero = 0, inconsistent = 0, stale = 0, consistent = 0;
    var usable = new HashSet<UUID>();
    for (var p : pairs) {
      if (p.postId() == null) throw new IllegalArgumentException("Post sample required");
      for (var n : new Long[] {p.numerator(), p.denominator()})
        if (n != null && (n < 0 || n > MetricRow.MAX_COUNT))
          throw new IllegalArgumentException("Metric count outside policy");
      if (p.numerator() == null || p.denominator() == null) {
        missing++;
        continue;
      }
      known++;
      if (p.denominator() == 0 && p.numerator() > 0) {
        inconsistent++;
        continue;
      }
      consistent++;
      if (p.denominator() == 0) zero++;
      else usable.add(p.postId());
      if (p.stale()) stale++;
      numerator = Math.addExact(numerator, p.numerator());
      denominator = Math.addExact(denominator, p.denominator());
    }
    long min = formula == Formula.SIGNUPS_PER_CLICK ? 20 : 100;
    var issues = new ArrayList<Issue>();
    if (missing > 0 || pairs.isEmpty()) issues.add(Issue.MISSING);
    if (usable.size() < MIN_POSTS || denominator < min) issues.add(Issue.SPARSE);
    if (zero > 0) issues.add(Issue.ZERO_DENOMINATOR);
    if (inconsistent > 0) issues.add(Issue.INCONSISTENT_PAIR);
    if (stale > 0) issues.add(Issue.STALE);
    if (unknownAttribution) issues.add(Issue.UNKNOWN_ATTRIBUTION);
    var rate =
        denominator == 0
            ? null
            : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    return new PerformanceRate(
        formula,
        "PERCENT",
        rate,
        consistent == 0 ? null : numerator,
        consistent == 0 ? null : denominator,
        known,
        usable.size(),
        missing,
        zero,
        inconsistent,
        stale,
        MIN_POSTS,
        min,
        issues.isEmpty(),
        List.copyOf(issues));
  }
}
