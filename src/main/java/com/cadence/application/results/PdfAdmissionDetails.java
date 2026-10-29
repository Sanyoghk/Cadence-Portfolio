package com.cadence.application.results;

import com.cadence.domain.EvidenceAdmission;
import java.time.Instant;
import java.util.UUID;

public record PdfAdmissionDetails(
    UUID id,
    UUID evidenceId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    UUID reviewedBy,
    Instant reviewedAt,
    String policyVersion,
    UUID artifactId,
    UUID originalScanId,
    UUID latestScanId,
    String rendererVersion,
    Instant scanExpiresAt,
    boolean aiConsumptionEnabled) {
  public static PdfAdmissionDetails from(EvidenceAdmission a) {
    return new PdfAdmissionDetails(
        a.getId(),
        a.getEvidenceId(),
        a.getSourceVersion(),
        a.getBrandVersion(),
        a.getWorkspaceVersion(),
        a.getSourceChecksum(),
        a.getContentChecksum(),
        a.getReviewedBy(),
        a.getReviewedAt(),
        a.getPolicyVersion(),
        a.getArtifactId(),
        a.getOriginalScanId(),
        a.getLatestScanId(),
        a.getRendererVersion(),
        a.getScanExpiresAt(),
        true);
  }
}
