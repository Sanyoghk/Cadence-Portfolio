package com.cadence.domain;

import java.time.*;
import java.time.zone.ZoneRulesProvider;

/** Explicit offset disambiguates a fold; a gap is never shifted silently. */
public record CalendarTime(String timezone, LocalDateTime localTime, ZoneOffset offset) {
  public CalendarTime {
    zone(timezone);
    if (localTime == null
        || offset == null
        || localTime.getSecond() != 0
        || localTime.getNano() != 0
        || !zone(timezone).getRules().getValidOffsets(localTime).contains(offset))
      throw new IllegalArgumentException("Invalid calendar local time or offset");
  }

  public static ZoneId zone(String value) {
    if (value == null || value.length() > 100 || !ZoneId.getAvailableZoneIds().contains(value))
      throw new IllegalArgumentException("Invalid calendar timezone");
    return ZoneId.of(value);
  }

  public Instant instant() {
    return localTime.toInstant(offset);
  }

  public String rulesVersion() {
    return ZoneRulesProvider.getVersions(timezone).lastKey();
  }

  public void requireFuture(Instant now) {
    if (now == null || !instant().isAfter(now) || instant().isAfter(now.plus(Duration.ofDays(366))))
      throw new IllegalArgumentException("Schedule outside supported horizon");
  }

  public static void interval(Instant from, Instant until, String timezone) {
    var zone = zone(timezone);
    if (from == null
        || until == null
        || from.isBefore(Instant.parse("1900-01-01T00:00:00Z"))
        || until.isAfter(Instant.parse("2200-01-01T00:00:00Z"))
        || !from.isBefore(until)
        || until.isAfter(from.atZone(zone).plusDays(28).toInstant()))
      throw new IllegalArgumentException("Calendar interval must be at most 28 local days");
  }
}
