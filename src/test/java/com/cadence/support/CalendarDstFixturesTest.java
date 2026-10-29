package com.cadence.support;

import static org.assertj.core.api.Assertions.*;

import com.cadence.domain.CalendarTime;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CalendarDstFixturesTest {
  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-10-25T00:00:00Z", "2026-10-25T01:30:00Z", "2026-10-29T12:00:00Z",
        "2027-04-04T02:00:00Z", "2027-10-31T02:00:00Z", "2028-02-29T12:00:00Z"
      })
  void bothRealOffsetsStayWithinTheUnchangedSchedulingHorizon(String value) {
    var now = Instant.parse(value);
    var fixture = CalendarDstFixtures.nextFold(now);
    var fold = fixture.transition();
    var local = fold.getDateTimeAfter().plusMinutes(30);
    var offsets = fixture.zone().getRules().getValidOffsets(local);
    assertThat(offsets).containsExactly(fold.getOffsetBefore(), fold.getOffsetAfter());
    for (var offset : offsets) {
      var time = new CalendarTime(fixture.zone().getId(), local, offset);
      assertThatCode(() -> time.requireFuture(now)).doesNotThrowAnyException();
    }
    assertThat(local.toInstant(offsets.get(0))).isNotEqualTo(local.toInstant(offsets.get(1)));
  }

  @Test
  void reproducesTheLateOctoberLondonFixtureFailureWithoutRelaxingDomainValidation() {
    var now = Instant.parse("2026-10-29T12:00:00Z");
    var rules = ZoneId.of("Europe/London").getRules();
    var fold = rules.nextTransition(now);
    while (fold.isGap()) fold = rules.nextTransition(fold.getInstant().plusSeconds(1));
    var oldFixture =
        new CalendarTime(
            "Europe/London", fold.getDateTimeAfter().plusMinutes(30), fold.getOffsetBefore());
    assertThatThrownBy(() -> oldFixture.requireFuture(now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(CalendarDstFixtures.nextFold(now).zone()).isEqualTo(ZoneId.of("Australia/Sydney"));
  }
}
