package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.services.QuarantineLifecyclePolicy;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarantineObjectTest {
  private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");

  private QuarantineObject object() {
    return new QuarantineObject(
        UUID.randomUUID(),
        UUID.randomUUID(),
        10,
        "pdf",
        NOW,
        Duration.ofSeconds(10),
        Duration.ofSeconds(10));
  }

  @Test
  void publicationIsAllowedOnlyInsideTheReservationDeadlineAndWithinItsByteBudget() {
    QuarantineObject object = object();
    assertThatThrownBy(() -> object.requireWritable(NOW.minusNanos(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> object.requireWritable(NOW.plusSeconds(10)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> object.reference(UUID.randomUUID(), 11, NOW))
        .isInstanceOf(IllegalArgumentException.class);
    object.reference(UUID.randomUUID(), 5, NOW.plusSeconds(9));
    assertThat(object.getRetainedBytes()).isEqualTo(5);
    assertThatThrownBy(() -> object.requireWritable(NOW.plusSeconds(9)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> object.beginDeletion(NOW.plusSeconds(100), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void cleanupNeverReopensItsKeyAndQuotaRequiresASecondConfirmedDeletionAfterDrain() {
    QuarantineObject object = object();
    assertThatThrownBy(() -> object.beginDeletion(NOW.plusSeconds(19), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalStateException.class);
    object.beginDeletion(NOW.plusSeconds(20), Duration.ofSeconds(10));
    assertThatThrownBy(() -> object.reference(UUID.randomUUID(), 5, NOW.plusSeconds(21)))
        .isInstanceOf(IllegalStateException.class);
    object.deletionConfirmed(NOW.plusSeconds(20), Duration.ofSeconds(10), Duration.ofSeconds(10));
    assertThat(object.isQuotaReleased()).isFalse();
    assertThatThrownBy(() -> object.beginDeletion(NOW.plusSeconds(29), Duration.ofSeconds(10)))
        .isInstanceOf(IllegalStateException.class);
    object.beginDeletion(NOW.plusSeconds(30), Duration.ofSeconds(10));
    object.deletionConfirmed(NOW.plusSeconds(30), Duration.ofSeconds(10), Duration.ofSeconds(10));
    assertThat(object.isQuotaReleased()).isTrue();
    assertThat(object.getState()).isEqualTo(QuarantineObject.State.TOMBSTONED);
    assertThat(object.getCleanupAttempts()).isEqualTo(2);
  }

  @Test
  void configurationRejectsUnboundedCleanupAndDrainShorterThanWriterDeadline() {
    assertThatThrownBy(() -> policy(100, Duration.ofSeconds(9), 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy(100, Duration.ofSeconds(10), 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy(0, Duration.ofSeconds(10), 20))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(policy(100, Duration.ofSeconds(10), 20)).isNotNull();
  }

  private QuarantineLifecyclePolicy policy(int hourlyWrites, Duration drain, int batch) {
    return new QuarantineLifecyclePolicy(
        100,
        2,
        hourlyWrites,
        Duration.ofSeconds(10),
        Duration.ofSeconds(10),
        drain,
        Duration.ofSeconds(10),
        batch);
  }
}
