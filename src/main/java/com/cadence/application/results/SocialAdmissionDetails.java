package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record SocialAdmissionDetails(
    EvidenceAdmissionDetails review,
    UUID snapshotId,
    Instant capturedAt,
    Instant bindingExpiresAt) {
  public static SocialAdmissionDetails from(EvidenceAdmission a, SocialTextSnapshot s) {
    return new SocialAdmissionDetails(
        EvidenceAdmissionDetails.from(a), s.getId(), s.getCapturedAt(), a.getBindingExpiresAt());
  }
}
