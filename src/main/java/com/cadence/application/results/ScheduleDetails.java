package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.*;
import java.util.UUID;

public record ScheduleDetails(
    UUID id,
    UUID postId,
    UUID revisionId,
    int scheduleVersion,
    PostScheduleEvent.Action action,
    PlaybookContent.Channel platform,
    long revisionLockVersion,
    String contentChecksum,
    String timezone,
    LocalDateTime localTime,
    ZoneOffset offset,
    Instant scheduledAt,
    String rulesVersion,
    UUID actorId,
    Instant occurredAt,
    boolean current,
    Eligibility eligibility,
    LocalDateTime displayLocalTime,
    ZoneOffset displayOffset) {
  public enum Eligibility {
    READY,
    CANCELLED,
    CONTENT_CHANGED,
    PARENT_ARCHIVED,
    SOURCE_UNAVAILABLE,
    HISTORICAL
  }

  public static ScheduleDetails from(
      PostScheduleEvent e, boolean current, Eligibility eligibility, String displayZone) {
    var local = e.getScheduledAt().atZone(CalendarTime.zone(displayZone));
    return new ScheduleDetails(
        e.getId(),
        e.getPost().getId(),
        e.getRevision().getId(),
        e.getEventNumber(),
        e.getAction(),
        e.getPost().getPlatform(),
        e.getRevisionLockVersion(),
        e.getContentChecksum(),
        e.getTimezone(),
        e.getLocalTime(),
        ZoneOffset.ofTotalSeconds(e.getOffsetSeconds()),
        e.getScheduledAt(),
        e.getRulesVersion(),
        e.getActorId(),
        e.getOccurredAt(),
        current,
        eligibility,
        local.toLocalDateTime(),
        local.getOffset());
  }
}
