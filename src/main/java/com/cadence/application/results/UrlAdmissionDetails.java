package com.cadence.application.results;

import com.cadence.domain.EvidenceAdmission;
import java.time.Instant;
import java.util.UUID;

public record UrlAdmissionDetails(
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
    UUID snapshotId,
    UUID originalScanId,
    UUID latestScanId,
    Instant scanExpiresAt,
    boolean aiConsumptionEnabled) {
  public static UrlAdmissionDetails from(EvidenceAdmission a) {
    return new UrlAdmissionDetails(
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
        a.getUrlSnapshotId(),
        a.getOriginalScanId(),
        a.getLatestScanId(),
        a.getScanExpiresAt(),
        true);
  }
}
