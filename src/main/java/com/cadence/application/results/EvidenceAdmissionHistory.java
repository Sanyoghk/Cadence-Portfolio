package com.cadence.application.results;

import com.cadence.application.repositories.EvidenceAdmissionWithdrawalRepository.SourceWithdrawal;
import com.cadence.domain.EvidenceAdmission;
import java.time.Instant;
import java.util.UUID;

/** Immutable metadata only. Historical receipts never imply current eligibility. */
public record EvidenceAdmissionHistory(
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
    UUID snapshotId,
    Instant scanExpiresAt,
    Instant bindingExpiresAt,
    boolean withdrawn,
    UUID withdrawnBy,
    Instant withdrawnAt) {
  public static EvidenceAdmissionHistory from(EvidenceAdmission a, SourceWithdrawal w) {
    return new EvidenceAdmissionHistory(
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
        a.getUrlSnapshotId() != null ? a.getUrlSnapshotId() : a.getSocialSnapshotId(),
        a.getScanExpiresAt(),
        a.getBindingExpiresAt(),
        w != null,
        w == null ? null : w.getWithdrawnBy(),
        w == null ? null : w.getWithdrawnAt());
  }
}
