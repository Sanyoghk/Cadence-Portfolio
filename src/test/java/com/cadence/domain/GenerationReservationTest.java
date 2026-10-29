package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerationReservationTest {
  private final Instant start = Instant.parse("2026-10-16T00:00:00Z");

  private GenerationReservation reservation() {
    return new GenerationReservation(
        UUID.randomUUID(),
        null,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "DECLARED",
        "a".repeat(64),
        "fixture-v1",
        "synthetic-fixed-v1",
        "upper=1",
        start,
        start.plusSeconds(86400),
        start.plusSeconds(60),
        1);
  }

  private GenerationUsage usage(long amount) {
    return new GenerationUsage(
        GenerationUsage.Origin.SYNTHETIC, "SYNTHETIC_CREDIT", 0, amount, "b".repeat(64));
  }

  @Test
  void deadlineIsExclusiveAndDispatchNeverRenewsIt() {
    var r = reservation();
    assertThatThrownBy(() -> r.dispatch(1, start.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class);
    r.dispatch(1, start.plusSeconds(59));
    assertThat(r.getDeadline()).isEqualTo(start.plusSeconds(60));
    assertThatThrownBy(() -> r.dispatch(2, start.plusSeconds(59)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void unknownIsNotZeroAndCannotBeReleased() {
    var r = reservation();
    r.dispatch(1, start);
    assertThat(r.getActual()).isNull();
    assertThatThrownBy(r::release).isInstanceOf(IllegalStateException.class);
    r.settle(usage(Long.MAX_VALUE), start.plusSeconds(864000));
    assertThat(r.getActual()).isEqualTo(Long.MAX_VALUE);
    r.settle(usage(Long.MAX_VALUE), start.plusSeconds(864001));
    assertThatThrownBy(() -> r.settle(usage(0), start.plusSeconds(864002)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void releaseRequiresNoExecutionAndCannotBeResurrected() {
    var r = reservation();
    r.release();
    r.release();
    assertThat(r.getState()).isEqualTo(GenerationReservation.State.RELEASED);
    assertThatThrownBy(() -> r.dispatch(1, start)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> r.settle(usage(0), start)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void usageIsTypedBoundedAndCannotMixNamespacesOrUnits() {
    var r = reservation();
    r.dispatch(1, start);
    assertThatThrownBy(
            () ->
                r.settle(
                    new GenerationUsage(
                        GenerationUsage.Origin.MEASURED, "SYNTHETIC_CREDIT", 0, 1, "b".repeat(64)),
                    start))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                r.settle(
                    new GenerationUsage(
                        GenerationUsage.Origin.SYNTHETIC, "USD", 2, 1, "b".repeat(64)),
                    start))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> usage(-1)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new GenerationUsage(GenerationUsage.Origin.SYNTHETIC, "USD", 10, 1, "b".repeat(64)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> r.settle(usage(1), start.minusNanos(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void neutralMeasuredUsageRequiresMatchingLiveQuoteWithoutEnablingAnyProvider() {
    var quote =
        new GenerationQuote(GenerationQuote.Namespace.LIVE, "NEUTRAL_UNIT", 3, 10, "test-quote-v1");
    var r =
        new GenerationReservation(
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "STRATEGY",
            "a".repeat(64),
            "test-policy",
            quote,
            "upper=10",
            start,
            start.plusSeconds(86400),
            start.plusSeconds(60));
    r.dispatch(1, start);
    assertThatThrownBy(
            () ->
                r.settle(
                    new GenerationUsage(
                        GenerationUsage.Origin.SYNTHETIC, "NEUTRAL_UNIT", 3, 4, "a".repeat(64)),
                    start))
        .isInstanceOf(IllegalArgumentException.class);
    r.settle(
        new GenerationUsage(GenerationUsage.Origin.MEASURED, "NEUTRAL_UNIT", 3, 12, "a".repeat(64)),
        start);
    assertThat(r.getActual()).isEqualTo(12);
    assertThat(r.getUsageOrigin()).isEqualTo(GenerationUsage.Origin.MEASURED);
    assertThatThrownBy(
            () ->
                new GenerationQuote(
                    GenerationQuote.Namespace.LIVE, "SYNTHETIC_CREDIT", 0, 1, "test"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
