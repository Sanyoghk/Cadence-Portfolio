package com.cadence.domain;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public record PerformanceQuery(
    LocalDate fromDate,
    LocalDate untilDate,
    String timezone,
    Dimension dimension,
    PlaybookContent.Channel platform,
    int page,
    int size,
    Bucket bucket) {
  public enum Bucket {
    DAY,
    PERIOD
  }

  public PerformanceQuery(
      LocalDate from,
      LocalDate until,
      String timezone,
      Dimension dimension,
      PlaybookContent.Channel platform,
      int page,
      int size) {
    this(from, until, timezone, dimension, platform, page, size, Bucket.DAY);
  }

  public enum Dimension {
    PLATFORM,
    PILLAR,
    FORMAT
  }

  public record Window(LocalDate date, Instant from, Instant until) {
    public long seconds() {
      return Duration.between(from, until).getSeconds();
    }
  }

  public PerformanceQuery {
    CalendarTime.zone(timezone);
    if (fromDate == null
        || untilDate == null
        || fromDate.isBefore(LocalDate.of(2000, 1, 2))
        || untilDate.isAfter(LocalDate.of(2199, 12, 31))
        || !fromDate.isBefore(untilDate)
        || ChronoUnit.DAYS.between(fromDate, untilDate) > 28
        || dimension == null
        || bucket == null
        || page < 0
        || page > 1000
        || size < 1
        || size > 50) throw new IllegalArgumentException("Invalid performance query bounds");
  }

  public List<Window> windows() {
    var z = CalendarTime.zone(timezone);
    if (bucket == Bucket.PERIOD)
      return from().isBefore(until()) ? List.of(new Window(fromDate, from(), until())) : List.of();
    return fromDate
        .datesUntil(untilDate)
        .map(
            d ->
                new Window(
                    d, d.atStartOfDay(z).toInstant(), d.plusDays(1).atStartOfDay(z).toInstant()))
        .filter(w -> w.from().isBefore(w.until()))
        .toList();
  }

  public Instant from() {
    return fromDate.atStartOfDay(CalendarTime.zone(timezone)).toInstant();
  }

  public int skippedLocalDays() {
    var z = CalendarTime.zone(timezone);
    return (int)
        fromDate
            .datesUntil(untilDate)
            .filter(
                d ->
                    !d.atStartOfDay(z)
                        .toInstant()
                        .isBefore(d.plusDays(1).atStartOfDay(z).toInstant()))
            .count();
  }

  public Instant until() {
    return untilDate.atStartOfDay(CalendarTime.zone(timezone)).toInstant();
  }
}
