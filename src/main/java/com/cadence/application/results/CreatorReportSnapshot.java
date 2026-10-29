package com.cadence.application.results;

import com.cadence.application.ports.RecommendationEngine;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;

/** Dedicated export model. No audit actors, raw receipts, Identity text or private references. */
public record CreatorReportSnapshot(
    String format,
    UUID brandId,
    UUID workspaceId,
    Source strategy,
    CreatorReportRequest request,
    Instant capturedAt,
    String rulesVersion,
    String formulaVersion,
    int candidateRows,
    int selectedRows,
    int duplicateSnapshots,
    int incompatibleWindows,
    int skippedLocalDays,
    List<String> caveats,
    List<Group> groups,
    List<Advice> recommendations) {
  public record Source(UUID id, int revision, long version, String checksum) {
    public Source {
      if (id == null
          || revision < 1
          || version < 0
          || checksum == null
          || !checksum.matches("[0-9a-f]{64}"))
        throw new IllegalArgumentException("Invalid report Strategy reference");
    }
  }

  public record Observation(
      UUID id,
      UUID postId,
      int revision,
      String checksum,
      Instant observedAt,
      Instant ingestedAt,
      String origin,
      UUID attributionId,
      Integer attributionVersion,
      UUID postRevisionId,
      Long postRevisionVersion,
      String contentChecksum,
      String pillarId,
      PostFormat postFormat,
      Long reach,
      Long engagement,
      Long saves,
      Long signups,
      Long profileToSiteClicks) {
    public Observation {
      if (id == null
          || postId == null
          || revision < 1
          || checksum == null
          || !checksum.matches("[0-9a-f]{64}")
          || observedAt == null
          || ingestedAt == null
          || origin == null) throw new IllegalArgumentException("Invalid report observation");
      for (var n : new Long[] {reach, engagement, saves, signups, profileToSiteClicks})
        if (n != null && (n < 0 || n > MetricRow.MAX_COUNT))
          throw new IllegalArgumentException("Invalid report count");
    }

    public static Observation from(PerformanceReport.PerformanceContribution s) {
      return new Observation(
          s.observationId(),
          s.postId(),
          s.metricRevision(),
          s.valueChecksum(),
          s.observedAt(),
          s.ingestedAt(),
          s.origin(),
          s.attributionId(),
          s.attributionNumber(),
          s.postRevisionId(),
          s.postRevisionVersion(),
          s.contentChecksum(),
          s.pillarId(),
          s.format(),
          s.reach(),
          s.engagement(),
          s.saves(),
          s.signups(),
          s.profileToSiteClicks());
    }
  }

  public record Group(
      LocalDate date,
      Instant windowFrom,
      Instant windowUntil,
      long windowSeconds,
      String pillarId,
      String pillarName,
      int posts,
      int staleRows,
      String comparisonKey,
      List<PerformanceMetrics.PerformanceRate> rates,
      List<Observation> sources) {
    public Group {
      rates = List.copyOf(rates);
      sources = List.copyOf(sources);
      if (date == null
          || windowFrom == null
          || windowUntil == null
          || !windowFrom.isBefore(windowUntil)
          || Duration.between(windowFrom, windowUntil).getSeconds() != windowSeconds
          || posts != sources.size()
          || posts > 1000
          || staleRows < 0
          || staleRows > posts
          || rates.size() != 4) throw new IllegalArgumentException("Invalid report group");
    }

    public static Group from(PerformanceReport.PerformanceGroup g) {
      return new Group(
          g.date(),
          g.windowFrom(),
          g.windowUntil(),
          g.windowSeconds(),
          g.pillarId(),
          g.pillarName(),
          g.posts(),
          g.staleRows(),
          g.comparisonKey(),
          g.rates(),
          g.sources().stream().map(Observation::from).toList());
    }
  }

  public record Advice(
      UUID id,
      String snapshotChecksum,
      Instant capturedAt,
      Source strategy,
      RecommendationEngine.Attribution attribution,
      String confidence,
      String claimType,
      String explanation,
      List<String> uncertainty,
      String fromPillar,
      String toPillar,
      int points,
      String relationToCapturedMetrics,
      List<Observation> supportingObservations) {
    public Advice {
      uncertainty = List.copyOf(uncertainty);
      supportingObservations = List.copyOf(supportingObservations);
      if (supportingObservations.size() < 10
          || supportingObservations.size() > 100
          || !"UNCALIBRATED".equals(confidence)
          || !"HYPOTHESIS".equals(claimType)
          || !"HEURISTIC".equals(attribution.origin())
          || !"pillar-shift-v1".equals(attribution.ruleVersion())
          || !"cadence-rules".equals(attribution.provider())
          || attribution.modelVersion() != null
          || attribution.promptVersion() != null
          || !Set.of("MATCHES_CAPTURED_METRICS", "HISTORICAL_DIFFERS_FROM_CAPTURED_METRICS")
              .contains(relationToCapturedMetrics)
          || explanation == null
          || explanation.length() > 2000)
        throw new IllegalArgumentException("Invalid historical recommendation");
      new RecommendationProposal(
          fromPillar,
          toPillar,
          points,
          supportingObservations.stream().map(Observation::id).toList());
    }
  }

  public CreatorReportSnapshot {
    if (!"creator-report-v1".equals(format)
        || brandId == null
        || workspaceId == null
        || strategy == null
        || request == null
        || capturedAt == null
        || !"performance-v1".equals(formulaVersion)
        || candidateRows < 0
        || candidateRows > 1000
        || selectedRows < 0
        || selectedRows > candidateRows
        || groups == null
        || groups.size() > 1000
        || recommendations == null
        || recommendations.size() > 10)
      throw new IllegalArgumentException("Invalid bounded report capture");
    caveats = List.copyOf(caveats);
    groups = List.copyOf(groups);
    recommendations = List.copyOf(recommendations);
    if (!strategy.id().equals(request.strategyId())
        || strategy.version() != request.expectedStrategyVersion()
        || groups.stream().mapToInt(g -> g.sources().size()).sum() != selectedRows
        || groups.stream()
                .flatMap(g -> g.sources().stream())
                .map(Observation::id)
                .distinct()
                .count()
            != selectedRows
        || !recommendations.stream()
            .map(Advice::id)
            .sorted()
            .toList()
            .equals(request.recommendationIds()))
      throw new IllegalArgumentException("Invalid report source binding");
    var windows = request.query(0).windows();
    for (var g : groups) {
      if (windows.stream()
              .noneMatch(
                  w ->
                      w.date().equals(g.date())
                          && w.from().equals(g.windowFrom())
                          && w.until().equals(g.windowUntil()))
          || g.sources().stream()
              .anyMatch(
                  o ->
                      o.observedAt().isAfter(capturedAt)
                          || !Objects.equals(g.pillarId(), o.pillarId())))
        throw new IllegalArgumentException("Invalid report observation window");
      var expectedRates = new ArrayList<PerformanceMetrics.PerformanceRate>();
      for (var f : PerformanceMetrics.Formula.values()) {
        var pairs =
            g.sources().stream()
                .map(
                    o ->
                        new PerformanceMetrics.Pair(
                            o.postId(),
                            switch (f) {
                              case ENGAGEMENT_PER_REACH -> o.engagement();
                              case SAVES_PER_REACH -> o.saves();
                              case CLICKS_PER_REACH -> o.profileToSiteClicks();
                              case SIGNUPS_PER_CLICK -> o.signups();
                            },
                            f == PerformanceMetrics.Formula.SIGNUPS_PER_CLICK
                                ? o.profileToSiteClicks()
                                : o.reach(),
                            o.observedAt()
                                .isBefore(
                                    capturedAt.minusSeconds(PerformanceMetrics.STALE_SECONDS))))
                .toList();
        expectedRates.add(PerformanceMetrics.calculate(f, pairs, g.pillarId() == null));
      }
      if (!g.rates().equals(expectedRates))
        throw new IllegalArgumentException("Corrupt report formulas");
    }
  }
}
