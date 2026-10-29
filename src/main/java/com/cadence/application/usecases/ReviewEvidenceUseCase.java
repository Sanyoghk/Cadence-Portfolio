package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class ReviewEvidenceUseCase {
  private final BrandEvidenceRepository evidence;
  private final EvidenceAdmissionRepository admissions;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;
  private final CadenceUserRepository users;
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final Clock clock;
  private final com.cadence.application.services.SocialAnalysisSourcePolicy social;

  public ReviewEvidenceUseCase(
      BrandEvidenceRepository evidence,
      EvidenceAdmissionRepository admissions,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      CadenceUserRepository users,
      WorkspaceRepository workspaces,
      BrandRepository brands,
      Clock clock,
      com.cadence.application.services.SocialAnalysisSourcePolicy social) {
    this.social = social;
    this.evidence = evidence;
    this.admissions = admissions;
    this.withdrawals = withdrawals;
    this.users = users;
    this.workspaces = workspaces;
    this.brands = brands;
    this.clock = clock;
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public org.springframework.data.domain.Page<EvidenceAdmissionHistory> history(
      UUID id, Set<UUID> scope, UUID actorId, int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 20)
      throw new IllegalArgumentException("Invalid history pagination");
    locked(id, scope, actorId);
    var revoked = new HashMap<Long, EvidenceAdmissionWithdrawalRepository.SourceWithdrawal>();
    for (var w :
        withdrawals.historyForEvidence(id, org.springframework.data.domain.PageRequest.of(0, 1000)))
      revoked.putIfAbsent(w.getSourceVersion(), w);
    var sort =
        org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Order.desc("reviewedAt"),
            org.springframework.data.domain.Sort.Order.desc("id"));
    return admissions
        .findByEvidence_Id(id, org.springframework.data.domain.PageRequest.of(page, size, sort))
        .map(a -> EvidenceAdmissionHistory.from(a, revoked.get(a.getSourceVersion())));
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public EvidenceReview preview(UUID id, Set<UUID> scope, UUID actorId) {
    var source = locked(id, scope, actorId);
    social.requireOrdinaryText(source);
    return EvidenceReview.from(source);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public EvidenceAdmissionDetails admit(
      UUID id,
      Set<UUID> scope,
      UUID actorId,
      long sourceVersion,
      long brandVersion,
      long workspaceVersion,
      String contentChecksum) {
    if (sourceVersion < 0
        || brandVersion < 0
        || workspaceVersion < 0
        || contentChecksum == null
        || !contentChecksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid evidence review token");
    var source = locked(id, scope, actorId);
    social.requireOrdinaryText(source);
    var reviewed = EvidenceReview.from(source);
    if (reviewed.sourceVersion() != sourceVersion
        || reviewed.brandVersion() != brandVersion
        || reviewed.workspaceVersion() != workspaceVersion
        || !reviewed.contentChecksum().equals(contentChecksum))
      throw new IllegalStateException("Evidence review is no longer current");
    if (withdrawals.existsForSourceVersion(id, sourceVersion))
      throw new IllegalStateException("Evidence source version has been withdrawn");
    if (admissions.existsForContext(id, sourceVersion, brandVersion, workspaceVersion))
      throw new IllegalStateException("Evidence has already been admitted");
    if (admissions.countForEvidence(id) >= EvidenceAdmission.MAX_REVIEWS_PER_EVIDENCE)
      throw new IllegalStateException("Evidence review history limit reached");
    return EvidenceAdmissionDetails.from(
        admissions.saveAndFlush(new EvidenceAdmission(source, actorId, clock.instant())));
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public EvidenceAdmissionSnapshot snapshot(
      UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    var source = locked(id, scope, actorId);
    social.requireOrdinaryText(source);
    var a = admission(admissionId, id);
    String text = EvidenceAdmission.reviewableText(source);
    if (withdrawals.existsForSourceVersion(id, a.getSourceVersion())
        || a.getSourceVersion() != source.getLockVersion()
        || a.getBrandVersion() != source.getBrand().getLockVersion()
        || a.getWorkspaceVersion() != source.getBrand().getWorkspace().getLockVersion()
        || !a.getSourceChecksum().equals(source.getChecksum())
        || !a.getReviewedText().equals(text)
        || !a.getContentChecksum().equals(EvidenceAdmission.checksum(text))
        || !EvidenceAdmission.POLICY_VERSION.equals(a.getPolicyVersion()))
      throw new IllegalStateException("Evidence admission is no longer current");
    return new EvidenceAdmissionSnapshot(EvidenceAdmissionDetails.from(a), text);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public EvidenceWithdrawalDetails withdraw(
      UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    locked(id, scope, actorId);
    var a = admission(admissionId, id);
    if (withdrawals.existsForAdmission(admissionId))
      throw new IllegalStateException("Evidence admission has already been withdrawn");
    return EvidenceWithdrawalDetails.from(
        withdrawals.saveAndFlush(new EvidenceAdmissionWithdrawal(a, actorId, clock.instant())));
  }

  private EvidenceAdmission admission(UUID admissionId, UUID evidenceId) {
    return admissions
        .findForEvidence(admissionId, evidenceId)
        .orElseThrow(() -> new ResourceNotFoundException("Evidence admission not found"));
  }

  private BrandEvidence locked(UUID id, Set<UUID> scope, UUID actorId) {
    var ownership =
        evidence
            .findOwnershipIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(() -> new AccessDeniedException("Evidence review denied"));
    var workspace =
        workspaces
            .findLockedByIdAndIdIn(ownership.getWorkspaceId(), scope)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    brands
        .findLockedWithWorkspaceIn(ownership.getBrandId(), scope)
        .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(workspace.getId())))
      throw new AccessDeniedException("Evidence review denied");
    return evidence
        .findLockedByIdAndBrandId(id, ownership.getBrandId())
        .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
  }
}
