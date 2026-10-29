package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuarantineWriteIntentTest {
  private static final UUID BRAND = UUID.randomUUID();
  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
  private static final String KEY = "quarantine/" + BRAND + "/" + "a".repeat(64) + ".pdf";

  @Test
  void bindsFileKeyToBrandAndCompletesOnlyOnce() {
    var intent = new QuarantineWriteIntent(BRAND, KEY, START);
    UUID evidenceId = UUID.randomUUID();
    intent.registered(evidenceId, START.plusSeconds(1));
    assertThat(intent.getEvidenceId()).isEqualTo(evidenceId);
    assertThat(intent.getRegisteredAt()).isEqualTo(START.plusSeconds(1));
    assertThatThrownBy(() -> intent.registered(UUID.randomUUID(), START.plusSeconds(2)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsForeignAndNoncanonicalKeysAndInvalidCompletion() {
    assertThatThrownBy(
            () ->
                new QuarantineWriteIntent(
                    BRAND,
                    "quarantine/" + UUID.randomUUID() + "/" + "a".repeat(64) + ".pdf",
                    START))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new QuarantineWriteIntent(BRAND, "quarantine/../file", START))
        .isInstanceOf(IllegalArgumentException.class);
    var intent = new QuarantineWriteIntent(BRAND, KEY, START);
    assertThatThrownBy(() -> intent.registered(UUID.randomUUID(), START.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> intent.registered(null, START))
        .isInstanceOf(IllegalStateException.class);
  }
}
