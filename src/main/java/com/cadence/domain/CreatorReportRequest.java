package com.cadence.domain;

import java.time.LocalDate;
import java.util.*;

public record CreatorReportRequest(
    UUID strategyId,
    long expectedStrategyVersion,
    LocalDate fromDate,
    LocalDate untilDate,
    String timezone,
    PlaybookContent.Channel platform,
    PerformanceQuery.Bucket bucket,
    List<UUID> recommendationIds) {
  public CreatorReportRequest {
    if (strategyId == null
        || expectedStrategyVersion < 0
        || platform == null
        || recommendationIds == null
        || recommendationIds.size() > 10
        || recommendationIds.stream().anyMatch(Objects::isNull)
        || new HashSet<>(recommendationIds).size() != recommendationIds.size())
      throw new IllegalArgumentException("Invalid report request");
    new PerformanceQuery(
        fromDate, untilDate, timezone, PerformanceQuery.Dimension.PILLAR, platform, 0, 50, bucket);
    recommendationIds = recommendationIds.stream().sorted().toList();
    if (bucket != PerformanceQuery.Bucket.PERIOD && !recommendationIds.isEmpty())
      throw new IllegalArgumentException("Recommendation reports require exact PERIOD window");
  }

  public PerformanceQuery query(int page) {
    return new PerformanceQuery(
        fromDate,
        untilDate,
        timezone,
        PerformanceQuery.Dimension.PILLAR,
        platform,
        page,
        50,
        bucket);
  }

  public String digest(UUID brand) {
    return MetricRow.sha(
        ("creator-report-request-v1|" + brand + "|" + this)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
