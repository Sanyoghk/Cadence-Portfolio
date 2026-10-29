package com.cadence.application.results;

import com.cadence.domain.BrandEvidence;
import com.cadence.domain.EvidenceAdmission;
import java.util.UUID;

public record EvidenceReview(
    UUID evidenceId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    String text,
    String policyVersion) {
  public static EvidenceReview from(BrandEvidence evidence) {
    String text = EvidenceAdmission.reviewableText(evidence);
    return new EvidenceReview(
        evidence.getId(),
        evidence.getLockVersion(),
        evidence.getBrand().getLockVersion(),
        evidence.getBrand().getWorkspace().getLockVersion(),
        evidence.getChecksum(),
        EvidenceAdmission.checksum(text),
        text,
        EvidenceAdmission.POLICY_VERSION);
  }
}
