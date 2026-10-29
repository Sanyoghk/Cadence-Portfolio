package com.cadence.application.results;

import com.cadence.domain.EvidenceAdmissionWithdrawal;
import java.time.Instant;
import java.util.UUID;

public record EvidenceWithdrawalDetails(
    UUID id, UUID admissionId, UUID withdrawnBy, Instant withdrawnAt) {
  public static EvidenceWithdrawalDetails from(EvidenceAdmissionWithdrawal w) {
    return new EvidenceWithdrawalDetails(
        w.getId(), w.getAdmissionId(), w.getWithdrawnBy(), w.getWithdrawnAt());
  }
}
