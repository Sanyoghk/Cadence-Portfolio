package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.UrlAdmissionDetails;
import com.cadence.application.results.UrlAdmissionSnapshot;
import com.cadence.application.results.UrlReviewPreview;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class UrlReviewTransactions {
  public record Input(
      UUID evidenceId,
      UUID brandId,
      UUID workspaceId,
      UUID actorId,
      long sourceVersion,
      long brandVersion,
      long workspaceVersion,
      UUID objectId,
      long objectVersion,
      String reference,
      UrlReviewContent content) {}

  public record Snapshot(Input input, Instant deadline) {}

  private final BrandEvidenceRepository evidence;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final UrlTextSnapshotRepository snapshots;
  private final EvidenceScanResultRepository scans;
  private final QuarantineObjectRepository objects;
  private final Clock clock;
  private final DurableJobRepository jobs;
  private final Duration maxAge;
  private final EvidenceAdmissionRepository admissions;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;

  public UrlReviewTransactions(
      BrandEvidenceRepository evidence,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      UrlTextSnapshotRepository snapshots,
      EvidenceScanResultRepository scans,
      QuarantineObjectRepository objects,
      Clock clock,
      DurableJobRepository jobs,
      EvidenceAdmissionRepository admissions,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      @Value("${cadence.evidence.artifacts.max-scan-age:PT24H}") Duration maxAge) {
    if (maxAge == null
        || maxAge.compareTo(Duration.ofSeconds(1)) < 0
        || maxAge.compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("Invalid URL review scan age");
    this.evidence = evidence;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.snapshots = snapshots;
    this.scans = scans;
    this.objects = objects;
    this.clock = clock;
    this.jobs = jobs;
    this.admissions = admissions;
    this.withdrawals = withdrawals;
    this.maxAge = maxAge;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public Snapshot capture(UUID id, Set<UUID> scope, UUID actorId) {
    var input = current(id, scope, actorId);
    requireNotWithdrawn(input);
    return new Snapshot(input, clock.instant().plusSeconds(30));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#snapshot.input().actorId())")
  public UrlReviewPreview finish(Snapshot snapshot, Set<UUID> scope) {
    var captured = snapshot.input();
    var fresh = current(captured.evidenceId(), scope, captured.actorId());
    var now = clock.instant();
    if (!fresh.equals(captured) || !now.isBefore(snapshot.deadline()))
      throw new IllegalStateException("URL review snapshot changed");
    requireNotWithdrawn(fresh);
    var c = fresh.content();
    return new UrlReviewPreview(
        fresh.evidenceId(),
        c.snapshotId(),
        c.latestScanId(),
        fresh.sourceVersion(),
        fresh.brandVersion(),
        fresh.workspaceVersion(),
        c.sourceChecksum(),
        c.contentChecksum(),
        c.text(),
        c.metadata(),
        c.fetchJobId(),
        c.fetchAttempt(),
        c.fetchedChecksum(),
        c.fetchedAt(),
        c.extractedAt(),
        UrlReviewContent.POLICY_VERSION,
        c.scanExpiresAt(),
        now,
        true,
        false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public Snapshot captureAdmission(UUID id, Set<UUID> scope, UUID actorId, UrlReviewToken token) {
    var input = current(id, scope, actorId);
    requireAdmission(input, token);
    return new Snapshot(input, clock.instant().plusSeconds(30));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#snapshot.input().actorId())")
  public UrlAdmissionDetails admit(Snapshot snapshot, Set<UUID> scope, UrlReviewToken token) {
    var fresh = fresh(snapshot, scope);
    requireAdmission(fresh, token);
    var source =
        evidence.findLockedByIdAndBrandId(fresh.evidenceId(), fresh.brandId()).orElseThrow();
    return UrlAdmissionDetails.from(
        admissions.saveAndFlush(
            new EvidenceAdmission(source, fresh.actorId(), clock.instant(), fresh.content())));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public Snapshot captureSnapshot(UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    var input = current(id, scope, actorId);
    requireReceipt(input, admissionId);
    return new Snapshot(input, clock.instant().plusSeconds(30));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#snapshot.input().actorId())")
  public UrlAdmissionSnapshot snapshot(Snapshot snapshot, UUID admissionId, Set<UUID> scope) {
    var input = fresh(snapshot, scope);
    var a = requireReceipt(input, admissionId);
    return new UrlAdmissionSnapshot(UrlAdmissionDetails.from(a), a.getReviewedText());
  }

  private Input fresh(Snapshot snapshot, Set<UUID> scope) {
    var old = snapshot.input();
    var input = current(old.evidenceId(), scope, old.actorId());
    if (!input.equals(old) || !clock.instant().isBefore(snapshot.deadline()))
      throw new IllegalStateException("URL review snapshot changed");
    return input;
  }

  private void requireNotWithdrawn(Input input) {
    if (withdrawals.existsForSourceVersion(input.evidenceId(), input.sourceVersion()))
      throw new IllegalStateException("URL source version withdrawn");
  }

  private void requireAdmission(Input input, UrlReviewToken token) {
    if (token == null) throw new IllegalArgumentException("URL review selectors required");
    token.requireMatches(
        input.content(), input.sourceVersion(), input.brandVersion(), input.workspaceVersion());
    if (withdrawals.existsForSourceVersion(input.evidenceId(), input.sourceVersion())
        || admissions.existsUrlContext(
            input.evidenceId(),
            input.sourceVersion(),
            input.brandVersion(),
            input.workspaceVersion(),
            input.content().snapshotId(),
            input.content().latestScanId(),
            input.content().scanExpiresAt())
        || admissions.countForEvidence(input.evidenceId())
            >= EvidenceAdmission.MAX_REVIEWS_PER_EVIDENCE)
      throw new IllegalStateException("URL admission is unavailable");
  }

  private EvidenceAdmission requireReceipt(Input input, UUID id) {
    var a =
        admissions
            .findForEvidence(id, input.evidenceId())
            .orElseThrow(() -> new ResourceNotFoundException("URL admission not found"));
    if (withdrawals.existsForSourceVersion(input.evidenceId(), a.getSourceVersion()))
      throw new IllegalStateException("URL admission has been withdrawn");
    a.requireCurrentUrl(
        input.content(),
        input.sourceVersion(),
        input.brandVersion(),
        input.workspaceVersion(),
        clock.instant());
    return a;
  }

  private Input current(UUID id, Set<UUID> scope, UUID actorId) {
    var own =
        evidence
            .findOwnershipIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(() -> new AccessDeniedException("URL review denied"));
    var ws =
        workspaces
            .findLockedByIdAndIdIn(own.getWorkspaceId(), scope)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    var brand =
        brands
            .findLockedWithWorkspaceIn(own.getBrandId(), scope)
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(ws.getId())))
      throw new AccessDeniedException("URL review denied");
    var e =
        evidence
            .findLockedByIdAndBrandId(id, brand.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Evidence not found"));
    UrlTextSnapshot.requireSource(e);
    var a =
        snapshots
            .findByEvidenceIdAndSourceVersion(id, e.getLockVersion())
            .orElseThrow(
                () -> new IllegalStateException("URL acquisition snapshot is unavailable"));
    var fetchJob =
        jobs.findById(a.getFetchJobId())
            .orElseThrow(() -> new IllegalStateException("URL fetch receipt unavailable"));
    if (fetchJob.getStatus() != JobStatus.SUCCEEDED
        || fetchJob.getAttempts() != a.getFetchAttempt())
      throw new IllegalStateException("URL fetch receipt changed");
    var latest = scans.latestForEvidence(id, org.springframework.data.domain.PageRequest.of(0, 1));
    if (latest.isEmpty()) throw new IllegalStateException("URL scan is unavailable");
    String reference = e.getFetchedReference();
    if (!reference.startsWith("quarantine:"))
      throw new IllegalStateException("URL storage is unavailable");
    var q =
        objects
            .findLockedForEvidence(
                reference.substring("quarantine:".length()), id, brand.getId(), ws.getId())
            .orElseThrow(() -> new IllegalStateException("URL storage is unavailable"));
    var content = UrlReviewContent.from(e, a, latest.getFirst(), q, clock.instant(), maxAge);
    return new Input(
        id,
        brand.getId(),
        ws.getId(),
        actorId,
        e.getLockVersion(),
        brand.getLockVersion(),
        ws.getLockVersion(),
        q.getId(),
        q.getLockVersion(),
        reference,
        content);
  }
}
