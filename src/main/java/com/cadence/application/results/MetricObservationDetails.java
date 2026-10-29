package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record MetricObservationDetails(
    UUID id,
    UUID postId,
    PlaybookContent.Channel platform,
    UUID originImportId,
    String origin,
    UUID ingestedBy,
    Instant ingestedAt,
    Instant observedAt,
    Instant windowFrom,
    Instant windowUntil,
    String unit,
    int revisionNumber,
    UUID previousId,
    String valueChecksum,
    Long reach,
    Long engagement,
    Long saves,
    Long signups,
    Long profileToSiteClicks) {
  public static MetricObservationDetails from(MetricObservation o) {
    return new MetricObservationDetails(
        o.getId(),
        o.getPost().getId(),
        o.getPost().getPlatform(),
        o.getOriginImport().getId(),
        o.getOriginImport().getOrigin(),
        o.getOriginImport().getActorId(),
        o.getIngestedAt(),
        o.getObservedAt(),
        o.getWindowFrom(),
        o.getWindowUntil(),
        "COUNT",
        o.getRevisionNumber(),
        o.getPrevious() == null ? null : o.getPrevious().getId(),
        o.getValueChecksum(),
        o.getReach(),
        o.getEngagement(),
        o.getSaves(),
        o.getSignups(),
        o.getProfileToSiteClicks());
  }
}
