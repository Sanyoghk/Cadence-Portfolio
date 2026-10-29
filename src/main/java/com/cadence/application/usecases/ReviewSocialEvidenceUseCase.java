package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.application.services.SocialAnalysisSourcePolicy;
import com.cadence.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class ReviewSocialEvidenceUseCase {
  private final BrandEvidenceRepository evidence;
  private final EvidenceAdmissionRepository admissions;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;
  private final CadenceUserRepository users;
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final SocialTextSnapshotRepository snapshots;
  private final SocialAnalysisSourcePolicy policy;
  private final Clock clock;

  public ReviewSocialEvidenceUseCase(
      BrandEvidenceRepository evidence,
      EvidenceAdmissionRepository admissions,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      CadenceUserRepository users,
      WorkspaceRepository workspaces,
      BrandRepository brands,
      SocialTextSnapshotRepository snapshots,
      SocialAnalysisSourcePolicy policy,
      Clock clock) {
    this.evidence = evidence;
    this.admissions = admissions;
    this.withdrawals = withdrawals;
    this.users = users;
    this.workspaces = workspaces;
    this.brands = brands;
    this.snapshots = snapshots;
    this.policy = policy;
    this.clock = clock;
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public SocialReviewPreview preview(UUID id, Set<UUID> scope, UUID actorId) {
    var e = locked(id, scope, actorId);
    requireNotWithdrawn(e);
    var o = policy.origin(e);
    var expiry = policy.binding(e, o);
    var snapshot =
        snapshots
            .findByEvidenceIdAndSourceVersion(id, e.getLockVersion())
            .orElseGet(
                () -> {
                  if (snapshots.countByEvidenceId(id) >= EvidenceAdmission.MAX_REVIEWS_PER_EVIDENCE)
                    throw new IllegalStateException("Social snapshot history limit reached");
                  return snapshots.saveAndFlush(new SocialTextSnapshot(e, o));
                });
    snapshot.requireCurrent(e, o);
    return new SocialReviewPreview(
        snapshot.getId(),
        id,
        e.getLockVersion(),
        e.getBrand().getLockVersion(),
        e.getBrand().getWorkspace().getLockVersion(),
        snapshot.getSourceChecksum(),
        snapshot.getContentChecksum(),
        snapshot.getPayload(),
        o.getProvider(),
        o.getCapturedAt(),
        expiry,
        SocialTextProjection.POLICY,
        "HISTORICAL_ONE_PAGE_TEXT_ONLY; PROVIDER_TEXT_MAY_BE_TRUNCATED; NO_MEDIA_OBSERVATIONS; HUMAN_REVIEW_REQUIRED");
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public SocialAdmissionDetails admit(
      UUID id, Set<UUID> scope, UUID actorId, SocialReviewToken token) {
    if (token == null) throw new IllegalArgumentException("Review selectors required");
    var e = locked(id, scope, actorId);
    requireNotWithdrawn(e);
    var o = policy.origin(e);
    var expiry = policy.binding(e, o);
    var snapshot =
        snapshots
            .findByIdAndEvidenceId(token.snapshotId(), id)
            .orElseThrow(() -> new ResourceNotFoundException("Social snapshot not found"));
    snapshot.requireCurrent(e, o);
    if (e.getLockVersion() != token.sourceVersion()
        || e.getBrand().getLockVersion() != token.brandVersion()
        || e.getBrand().getWorkspace().getLockVersion() != token.workspaceVersion()
        || !snapshot.getContentChecksum().equals(token.contentChecksum())
        || !expiry.equals(token.bindingExpiresAt()))
      throw new IllegalStateException("Social review is no longer current");
    if (admissions.existsSocialContext(
        id,
        e.getLockVersion(),
        token.brandVersion(),
        token.workspaceVersion(),
        snapshot.getId(),
        expiry)) throw new IllegalStateException("Social snapshot already admitted");
    if (admissions.countForEvidence(id) >= EvidenceAdmission.MAX_REVIEWS_PER_EVIDENCE)
      throw new IllegalStateException("Evidence review history limit reached");
    var a =
        admissions.saveAndFlush(
            new EvidenceAdmission(e, actorId, clock.instant(), snapshot, expiry));
    return SocialAdmissionDetails.from(a, snapshot);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public SocialAdmissionSnapshot snapshot(
      UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    var e = locked(id, scope, actorId);
    requireNotWithdrawn(e);
    var a =
        admissions
            .findForEvidence(admissionId, id)
            .orElseThrow(() -> new ResourceNotFoundException("Admission not found"));
    var s = policy.validate(a, e, e.getBrand().getLockVersion());
    return new SocialAdmissionSnapshot(SocialAdmissionDetails.from(a, s), s.getPayload());
  }

  private void requireNotWithdrawn(BrandEvidence e) {
    if (withdrawals.existsForSourceVersion(e.getId(), e.getLockVersion()))
      throw new IllegalStateException("Evidence source version withdrawn");
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
