package com.cadence.support;

import java.time.*;
import java.time.zone.ZoneOffsetTransition;
import java.util.*;

/** Select a real upcoming overlap without exceeding the production scheduling horizon. */
public final class CalendarDstFixtures {
  private CalendarDstFixtures() {}

  public record Fold(ZoneId zone, ZoneOffsetTransition transition) {}

  public static Fold nextFold(Instant now) {
    // Opposite hemispheres avoid the 371-day interval between some annual London folds.
    return List.of("Europe/London", "Australia/Sydney").stream()
        .map(
            name -> {
              var zone = ZoneId.of(name);
              var transition = zone.getRules().nextTransition(now);
              while (transition.isGap()) {
                transition = zone.getRules().nextTransition(transition.getInstant().plusSeconds(1));
              }
              return new Fold(zone, transition);
            })
        .min(Comparator.comparing(fold -> fold.transition().getInstant()))
        .orElseThrow();
  }
}
