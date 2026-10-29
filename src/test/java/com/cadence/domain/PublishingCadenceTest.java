package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublishingCadenceTest {

  @Test
  void shouldCreateCadenceAndDefensivelyCopyTimes() {
    List<LocalTime> source = new ArrayList<>(List.of(LocalTime.of(9, 0), LocalTime.of(18, 30)));

    PublishingCadence cadence = new PublishingCadence(5, "Asia/Almaty", source);
    source.clear();
    List<LocalTime> returned = cadence.getBestTimes();
    returned.clear();

    assertEquals(5, cadence.getPostsPerWeek());
    assertEquals("Asia/Almaty", cadence.getTimezone());
    assertEquals(List.of(LocalTime.of(9, 0), LocalTime.of(18, 30)), cadence.getBestTimes());
  }

  @Test
  void shouldTreatNullTimesAsEmptyList() {
    PublishingCadence cadence = new PublishingCadence(1, "UTC", null);

    assertTrue(cadence.getBestTimes().isEmpty());
  }

  @Test
  void shouldRejectInvalidPostFrequency() {
    assertThrows(IllegalArgumentException.class, () -> new PublishingCadence(0, "UTC", List.of()));
  }

  @Test
  void shouldRejectEmptyOrUnknownTimezone() {
    assertThrows(IllegalArgumentException.class, () -> new PublishingCadence(1, " ", List.of()));
    assertThrows(
        DateTimeException.class, () -> new PublishingCadence(1, "Not/A_Timezone", List.of()));
  }

  @Test
  void shouldRejectNullPublishingTime() {
    List<LocalTime> times = new ArrayList<>();
    times.add(null);

    assertThrows(IllegalArgumentException.class, () -> new PublishingCadence(1, "UTC", times));
  }
}
