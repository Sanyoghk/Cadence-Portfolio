package com.cadence.domain;

import java.time.Instant;
import java.util.UUID;

/** Scalar normalized read model: deliberately excludes raw bytes/provider/private payloads. */
public record PerformanceSample(
    UUID observationId,
    UUID postId,
    PlaybookContent.Channel platform,
    Instant windowFrom,
    Instant windowUntil,
    Instant observedAt,
    Instant ingestedAt,
    int revisionNumber,
    String valueChecksum,
    UUID importId,
    String rawChecksum,
    String origin,
    UUID ingestedBy,
    UUID strategyId,
    int strategyRevision,
    String strategyChecksum,
    UUID sourceApprovedBy,
    Instant sourceApprovedAt,
    UUID attributionId,
    Integer attributionNumber,
    UUID postRevisionId,
    Long postRevisionVersion,
    String contentChecksum,
    PostFormat format,
    String pillarId,
    String pillarName,
    String pillarOrigin,
    UUID attributedBy,
    Instant attributedAt,
    Long reach,
    Long engagement,
    Long saves,
    Long signups,
    Long profileToSiteClicks) {
  public PerformanceSample {
    if (observationId == null
        || postId == null
        || platform == null
        || windowFrom == null
        || windowUntil == null
        || observedAt == null
        || !windowFrom.isBefore(windowUntil)
        || windowUntil.isAfter(observedAt))
      throw new IllegalArgumentException("Invalid performance source");
    for (var n : new Long[] {reach, engagement, saves, signups, profileToSiteClicks})
      if (n != null && (n < 0 || n > MetricRow.MAX_COUNT))
        throw new IllegalArgumentException("Corrupt performance count");
  }
}
