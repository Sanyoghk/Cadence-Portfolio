package com.cadence.application.results;

import com.cadence.domain.EvidenceAdmission;
import java.time.Instant;
import java.util.UUID;

public record EvidenceAdmissionDetails(
    UUID id,
    UUID evidenceId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    UUID reviewedBy,
    Instant reviewedAt,
    String policyVersion) {
  public static EvidenceAdmissionDetails from(EvidenceAdmission a) {
    return new EvidenceAdmissionDetails(
        a.getId(),
        a.getEvidenceId(),
        a.getSourceVersion(),
        a.getBrandVersion(),
        a.getWorkspaceVersion(),
        a.getSourceChecksum(),
        a.getContentChecksum(),
        a.getReviewedBy(),
        a.getReviewedAt(),
        a.getPolicyVersion());
  }
}
