package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record MetricAttributionDetails(
    UUID id,
    int attributionNumber,
    UUID previousId,
    UUID observationId,
    UUID postId,
    UUID postRevisionId,
    long postRevisionVersion,
    String contentChecksum,
    PostFormat format,
    String pillarId,
    String pillarName,
    String pillarOrigin,
    UUID strategyId,
    int strategyRevision,
    String strategyChecksum,
    UUID attributedBy,
    Instant attributedAt) {
  public static MetricAttributionDetails from(MetricAttribution a) {
    var p = a.getObservation().getPost();
    return new MetricAttributionDetails(
        a.getId(),
        a.getAttributionNumber(),
        a.getPrevious() == null ? null : a.getPrevious().getId(),
        a.getObservation().getId(),
        p.getId(),
        a.getRevision().getId(),
        a.getRevisionVersion(),
        a.getContentChecksum(),
        a.getRevision().getFormat(),
        a.getPillarId(),
        a.getPillarName(),
        a.getPillarOrigin(),
        p.getStrategy().getId(),
        p.getSourceRevision(),
        p.getSourceChecksum(),
        a.getActorId(),
        a.getAttributedAt());
  }
}
