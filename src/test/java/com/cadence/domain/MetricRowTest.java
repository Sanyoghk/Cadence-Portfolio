package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MetricRowTest {
  @Test
  void rawReceiptBoundsAndExclusiveDeadline() {
    var b = new Brand("Metrics", new Workspace("Studio", "Creator"));
    var now = Instant.parse("2026-01-01T00:00:00Z");
    var job = new DurableJob("METRICS_IMPORT", "metrics:pending", "a-key", 3, now);
    var receipt =
        new MetricImport(
            b,
            job,
            UUID.randomUUID(),
            "bounded-metrics-key-001",
            "CSV",
            new byte[131072],
            200,
            now);
    assertThat(receipt.raw()).hasSize(131072);
    var copy = receipt.raw();
    copy[0] = 1;
    assertThat(receipt.raw()[0]).isZero();
    assertThatThrownBy(
            () ->
                new MetricImport(
                    b,
                    job,
                    UUID.randomUUID(),
                    "bounded-metrics-key-001",
                    "CSV",
                    new byte[131073],
                    1,
                    now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MetricImport(
                    b,
                    job,
                    UUID.randomUUID(),
                    "bounded-metrics-key-001",
                    "CSV",
                    new byte[1],
                    201,
                    now))
        .isInstanceOf(IllegalArgumentException.class);
    var one =
        new MetricImport(
            b, job, UUID.randomUUID(), "bounded-metrics-key-002", "CSV", new byte[1], 1, now);
    assertThatThrownBy(
            () -> one.publish(List.of(UUID.randomUUID()), "worker", 1, one.getDeadline()))
        .isInstanceOf(IllegalStateException.class);
    one.publish(List.of(UUID.randomUUID()), "worker", 1, one.getDeadline().minusNanos(1000));
    assertThatThrownBy(() -> one.publish(List.of(UUID.randomUUID()), "worker", 1, now))
        .isInstanceOf(IllegalStateException.class);
  }

  private MetricRow row(Long n, Instant from, Instant until, Instant at) {
    return new MetricRow(
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        PlaybookContent.Channel.INSTAGRAM,
        from,
        until,
        at,
        n,
        null,
        null,
        null,
        null,
        null);
  }

  @Test
  void missingDifferentFromKnownZeroAndBounds() {
    var from = Instant.parse("2026-01-01T00:00:00Z");
    var until = from.plusSeconds(86400);
    assertThat(row(0L, from, until, until).reach()).isZero();
    assertThat(row(MetricRow.MAX_COUNT, from, until, until).digest()).hasSize(64);
    for (Long n : new Long[] {null, -1L, MetricRow.MAX_COUNT + 1, Long.MAX_VALUE})
      assertThatThrownBy(() -> row(n, from, until, until))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidIntervalAndPrecision() {
    var from = Instant.parse("2026-01-01T00:00:00Z");
    for (var until : List.of(from, from.minusSeconds(1), from.plus(Duration.ofDays(367))))
      assertThatThrownBy(() -> row(1L, from, until, from.plus(Duration.ofDays(400))))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> row(1L, from.plusNanos(1), from.plusSeconds(1), from.plusSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> row(1L, from, from.plusSeconds(2), from.plusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
