package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceAdmissionWithdrawalTest {
  @Test
  void preservesExactActorAdmissionAndTimeAndRejectsInvalidAudit() {
    var a = mock(EvidenceAdmission.class);
    var id = UUID.randomUUID();
    var actor = UUID.randomUUID();
    var now = Instant.now();
    when(a.getId()).thenReturn(id);
    when(a.getReviewedAt()).thenReturn(now.minusSeconds(1));
    var w = new EvidenceAdmissionWithdrawal(a, actor, now);
    assertThat(w.getAdmissionId()).isEqualTo(id);
    assertThat(w.getWithdrawnBy()).isEqualTo(actor);
    assertThat(w.getWithdrawnAt()).isEqualTo(now);
    assertThatThrownBy(() -> new EvidenceAdmissionWithdrawal(null, actor, now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmissionWithdrawal(a, null, now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmissionWithdrawal(a, actor, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmissionWithdrawal(a, actor, now.minusSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class);
    when(a.getId()).thenReturn(null);
    assertThatThrownBy(() -> new EvidenceAdmissionWithdrawal(a, actor, now))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
