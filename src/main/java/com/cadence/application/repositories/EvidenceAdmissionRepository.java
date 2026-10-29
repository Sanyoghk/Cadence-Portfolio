package com.cadence.application.repositories;

import com.cadence.domain.EvidenceAdmission;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceAdmissionRepository extends JpaRepository<EvidenceAdmission, UUID> {
  org.springframework.data.domain.Page<EvidenceAdmission> findByEvidence_Id(
      UUID evidenceId, org.springframework.data.domain.Pageable page);

  @org.springframework.data.jpa.repository.Query(
      "select count(a)>0 from EvidenceAdmission a where a.evidence.id=:id and a.sourceVersion=:source and a.brandVersion=:brand and a.workspaceVersion=:workspace and a.socialSnapshotId=:snapshot and a.bindingExpiresAt=:expiry")
  boolean existsSocialContext(
      UUID id, long source, long brand, long workspace, UUID snapshot, java.time.Instant expiry);

  @org.springframework.data.jpa.repository.Query(
      "select count(a)>0 from EvidenceAdmission a where a.evidence.id=:id and a.sourceVersion=:source and a.brandVersion=:brand and a.workspaceVersion=:workspace and a.urlSnapshotId=:snapshot and a.latestScanId=:scan and a.scanExpiresAt=:expiry")
  boolean existsUrlContext(
      UUID id,
      long source,
      long brand,
      long workspace,
      UUID snapshot,
      UUID scan,
      java.time.Instant expiry);

  @org.springframework.data.jpa.repository.Query(
      "select a from EvidenceAdmission a where a.evidence.brand.id=:brandId and a.brandVersion=:brandVersion and a.workspaceVersion=:workspaceVersion and a.sourceVersion=a.evidence.lockVersion and ((a.evidence.status=com.cadence.domain.EvidenceStatus.PENDING and (a.policyVersion='text-review-v1' or a.policyVersion='pdf-text-review-v1' or a.policyVersion='social-text-review-v1')) or (a.evidence.status=com.cadence.domain.EvidenceStatus.FETCHED and a.policyVersion='url-text-review-v1')) and not exists(select w.id from EvidenceAdmissionWithdrawal w where w.admission.evidence.id=a.evidence.id and w.admission.sourceVersion=a.sourceVersion) and not exists(select newer.id from EvidenceAdmission newer where newer.evidence.id=a.evidence.id and newer.sourceVersion=a.sourceVersion and newer.brandVersion=a.brandVersion and newer.workspaceVersion=a.workspaceVersion and (newer.reviewedAt>a.reviewedAt or (newer.reviewedAt=a.reviewedAt and newer.id>a.id))) order by a.evidence.id")
  java.util.List<EvidenceAdmission> findCurrentReviewed(
      UUID brandId,
      long brandVersion,
      long workspaceVersion,
      org.springframework.data.domain.Pageable page);

  @org.springframework.data.jpa.repository.Query(
      "select count(a)>0 from EvidenceAdmission a where a.evidence.id=:id and a.sourceVersion=:source and a.brandVersion=:brand and a.workspaceVersion=:workspace and a.artifactId=:artifact and a.latestScanId=:scan and a.scanExpiresAt=:expiry")
  boolean existsPdfContext(
      UUID id,
      long source,
      long brand,
      long workspace,
      UUID artifact,
      UUID scan,
      java.time.Instant expiry);

  @org.springframework.data.jpa.repository.Query(
      "select a from EvidenceAdmission a where a.evidence.brand.id=:brandId and a.brandVersion=:brandVersion and a.workspaceVersion=:workspaceVersion and a.sourceVersion=a.evidence.lockVersion and a.evidence.type=com.cadence.domain.EvidenceType.TEXT and a.evidence.status=com.cadence.domain.EvidenceStatus.PENDING and not exists(select w.id from EvidenceAdmissionWithdrawal w where w.admission.evidence.id=a.evidence.id and w.admission.sourceVersion=a.sourceVersion) order by a.evidence.id")
  java.util.List<EvidenceAdmission> findCurrentText(
      UUID brandId,
      long brandVersion,
      long workspaceVersion,
      org.springframework.data.domain.Pageable pageable);

  @org.springframework.data.jpa.repository.Query(
      "select a from EvidenceAdmission a where a.id = :id and a.evidence.id = :evidenceId")
  java.util.Optional<EvidenceAdmission> findForEvidence(UUID id, UUID evidenceId);

  @org.springframework.data.jpa.repository.Query(
      "select count(a) > 0 from EvidenceAdmission a where a.evidence.id = :evidenceId and a.sourceVersion = :sourceVersion")
  boolean existsByEvidenceIdAndSourceVersion(UUID evidenceId, long sourceVersion);

  @org.springframework.data.jpa.repository.Query(
      "select count(a) > 0 from EvidenceAdmission a where a.evidence.id = :evidenceId and a.sourceVersion = :sourceVersion and a.brandVersion = :brandVersion and a.workspaceVersion = :workspaceVersion")
  boolean existsForContext(
      UUID evidenceId, long sourceVersion, long brandVersion, long workspaceVersion);

  @org.springframework.data.jpa.repository.Query(
      "select count(a) from EvidenceAdmission a where a.evidence.id = :evidenceId")
  long countForEvidence(UUID evidenceId);
}
