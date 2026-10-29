package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CalendarTimeTest {
  @Test
  void foldRequiresValidExplicitOffsetAndRoundTripsBothInstants() {
    var local = LocalDateTime.parse("2027-10-31T01:30:00");
    var first = new CalendarTime("Europe/London", local, ZoneOffset.ofHours(1));
    var second = new CalendarTime("Europe/London", local, ZoneOffset.UTC);
    assertThat(Duration.between(first.instant(), second.instant())).isEqualTo(Duration.ofHours(1));
    for (var time : new CalendarTime[] {first, second}) {
      assertThat(time.instant().atZone(ZoneId.of(time.timezone())).toLocalDateTime())
          .isEqualTo(local);
      assertThat(time.rulesVersion()).isNotBlank();
    }
    assertThatThrownBy(() -> new CalendarTime("Europe/London", local, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CalendarTime("Europe/London", local, ZoneOffset.ofHours(2)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void gapNeverShiftsAndNonHourTransitionWorks() {
    assertThatThrownBy(
            () ->
                new CalendarTime(
                    "Europe/London", LocalDateTime.parse("2027-03-28T01:30:00"), ZoneOffset.UTC))
        .isInstanceOf(IllegalArgumentException.class);
    var local = LocalDateTime.parse("2027-04-04T01:45:00");
    assertThat(
            Duration.between(
                new CalendarTime("Australia/Lord_Howe", local, ZoneOffset.ofHours(11)).instant(),
                new CalendarTime("Australia/Lord_Howe", local, ZoneOffset.ofHoursMinutes(10, 30))
                    .instant()))
        .isEqualTo(Duration.ofMinutes(30));
  }

  @ParameterizedTest
  @ValueSource(strings = {"+01:00", "GMT+5", "../UTC", "No/Zone", ""})
  void rejectsArbitrarySystemTimezone(String zone) {
    assertThatThrownBy(() -> CalendarTime.zone(zone)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void minuteHorizonAndLocalDayIntervalBounds() {
    Instant now = Instant.parse("2027-01-01T00:00:00Z");
    assertThatThrownBy(
            () ->
                new CalendarTime("UTC", LocalDateTime.parse("2027-01-01T00:00:01"), ZoneOffset.UTC))
        .isInstanceOf(IllegalArgumentException.class);
    var time =
        new CalendarTime("UTC", LocalDateTime.ofInstant(now, ZoneOffset.UTC), ZoneOffset.UTC);
    assertThatThrownBy(() -> time.requireFuture(now)).isInstanceOf(IllegalArgumentException.class);
    new CalendarTime("UTC", time.localTime().plusDays(366), ZoneOffset.UTC).requireFuture(now);
    assertThatThrownBy(
            () ->
                new CalendarTime(
                        "UTC", time.localTime().plusDays(366).plusMinutes(1), ZoneOffset.UTC)
                    .requireFuture(now))
        .isInstanceOf(IllegalArgumentException.class);
    var from = ZonedDateTime.of(2027, 10, 15, 0, 0, 0, 0, ZoneId.of("Europe/London"));
    CalendarTime.interval(from.toInstant(), from.plusDays(28).toInstant(), "Europe/London");
    assertThatThrownBy(
            () ->
                CalendarTime.interval(
                    from.toInstant(),
                    from.plusDays(28).plusSeconds(1).toInstant(),
                    "Europe/London"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
