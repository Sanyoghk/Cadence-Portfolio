package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** No adapter calls here: each phase commits before control returns to orchestration. */
@Service
@org.springframework.security.access.prepost.PreAuthorize("denyAll()")
public class BrandAnalysisTransactions {
  public record Snapshot(
      UUID brandId, BrandAnalysisCommand command, UUID actorId, Set<UUID> pdfAdmissions) {
    public Snapshot {
      pdfAdmissions = Set.copyOf(pdfAdmissions);
    }

    public Snapshot(UUID brandId, BrandAnalysisCommand command, UUID actorId) {
      this(brandId, command, actorId, Set.of());
    }

    public Snapshot(UUID brandId, BrandAnalysisCommand command) {
      this(brandId, command, null);
    }
  }

  public record Completion(boolean published, BrandSignature signature) {}

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final BrandAnalysisAttemptRepository attempts;
  private final Clock clock;
  private final CadenceUserRepository users;
  private final BrandEvidenceRepository evidence;
  private final EvidenceAdmissionRepository admissions;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;
  private final BrandAnalysisSourceRepository sources;
  private final BrandAnalysisCitationRepository citations;
  private final PdfAnalysisSourcePolicy pdfPolicy;
  private final UrlAnalysisSourcePolicy urlPolicy;
  private final SocialAnalysisSourcePolicy social;

  public BrandAnalysisTransactions(
      BrandRepository brands,
      WorkspaceRepository workspaces,
      BrandAnalysisAttemptRepository attempts,
      Clock clock,
      CadenceUserRepository users,
      BrandEvidenceRepository evidence,
      EvidenceAdmissionRepository admissions,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      BrandAnalysisSourceRepository sources,
      BrandAnalysisCitationRepository citations,
      PdfAnalysisSourcePolicy pdfPolicy,
      UrlAnalysisSourcePolicy urlPolicy,
      SocialAnalysisSourcePolicy social) {
    this.social = social;
    this.brands = brands;
    this.workspaces = workspaces;
    this.attempts = attempts;
    this.clock = clock;
    this.users = users;
    this.evidence = evidence;
    this.admissions = admissions;
    this.withdrawals = withdrawals;
    this.sources = sources;
    this.citations = citations;
    this.pdfPolicy = pdfPolicy;
    this.urlPolicy = urlPolicy;
  }

  private Brand locked(UUID id, Set<UUID> scope) {
    UUID workspaceId =
        brands
            .findWorkspaceIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    workspaces
        .findLockedById(workspaceId)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    return brands
        .findLockedWithWorkspaceIn(id, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public Snapshot start(UUID brandId, Set<UUID> scope) {
    Brand brand = locked(brandId, scope);
    return begin(brand, java.util.List.of(), null);
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public Snapshot startEvidence(UUID brandId, Set<UUID> scope, UUID actorId) {
    Brand brand = lockedOwner(brandId, scope, actorId);
    requireActive(brand);
    var selected =
        admissions.findCurrentReviewed(
            brandId,
            brand.getLockVersion(),
            brand.getWorkspace().getLockVersion(),
            org.springframework.data.domain.PageRequest.of(
                0, BrandAnalysisCommand.MAX_SOURCES + 1));
    if (selected.isEmpty() || selected.size() > BrandAnalysisCommand.MAX_SOURCES)
      throw new IllegalStateException("No bounded current evidence input available");
    var input = new java.util.ArrayList<AnalysisTextSource>();
    for (var a : selected) {
      if (!current(a, brand))
        throw new IllegalStateException("Evidence input is no longer current");
      input.add(textSource(a));
    }
    if (input.stream().mapToInt(AnalysisTextSource::utf8Bytes).sum()
        > BrandAnalysisCommand.MAX_SOURCE_BYTES)
      throw new IllegalStateException("Evidence input limit exceeded");
    var pdfs =
        selected.stream()
            .filter(a -> PdfReviewContent.POLICY_VERSION.equals(a.getPolicyVersion()))
            .map(EvidenceAdmission::getId)
            .collect(java.util.stream.Collectors.toSet());
    var snapshot = begin(brand, input, actorId, pdfs);
    for (int i = 0; i < selected.size(); i++)
      sources.saveAndFlush(
          new BrandAnalysisSource(snapshot.command().analysisId(), selected.get(i), i));
    return snapshot;
  }

  private Snapshot begin(Brand brand, java.util.List<AnalysisTextSource> selected, UUID actorId) {
    return begin(brand, selected, actorId, Set.of());
  }

  private Snapshot begin(
      Brand brand, java.util.List<AnalysisTextSource> selected, UUID actorId, Set<UUID> pdfs) {
    UUID brandId = brand.getId();
    requireActive(brand);
    Instant now = Instant.now(clock);
    var running = attempts.findByBrandIdAndStatus(brandId, BrandAnalysisAttempt.Status.RUNNING);
    if (running.isPresent()) {
      if (now.isBefore(running.get().getDeadline()))
        throw new IllegalStateException("Brand analysis is already running");
      running.get().finish(BrandAnalysisAttempt.Status.FAILED, now);
      attempts.saveAndFlush(running.get());
    }
    // Validate declared input before creating a persisted RUNNING attempt.
    var source =
        new BrandAnalysisCommand(
            UUID.randomUUID(),
            brand.getLockVersion(),
            brand.getWorkspace().getLockVersion(),
            brand.getName(),
            brand.getWorkspace().getIndustry(),
            selected);
    var attempt =
        attempts.saveAndFlush(
            actorId == null
                ? new BrandAnalysisAttempt(
                    brandId,
                    source.brandVersion(),
                    source.workspaceVersion(),
                    source.sourceChecksum(),
                    now)
                : new BrandAnalysisAttempt(
                    brandId,
                    source.brandVersion(),
                    source.workspaceVersion(),
                    source.sourceChecksum(),
                    now,
                    actorId,
                    source.brandName(),
                    source.industry()));
    return new Snapshot(
        brandId,
        new BrandAnalysisCommand(
            attempt.getId(),
            source.brandVersion(),
            source.workspaceVersion(),
            source.brandName(),
            source.industry(),
            selected),
        actorId,
        pdfs);
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and (#snapshot.actorId() == null or @access.isCurrentUser(authentication,#snapshot.actorId()))")
  public Completion complete(Snapshot snapshot, Set<UUID> scope, BrandAnalysisResult result) {
    Brand brand =
        snapshot.actorId() == null
            ? locked(snapshot.brandId(), scope)
            : lockedOwner(snapshot.brandId(), scope, snapshot.actorId());
    var attempt = attempts.findById(snapshot.command().analysisId()).orElseThrow();
    requireAttempt(snapshot, attempt);
    Instant now = Instant.now(clock);
    if (attempt.getStatus() != BrandAnalysisAttempt.Status.RUNNING)
      return new Completion(false, null);
    if (!now.isBefore(attempt.getDeadline())
        || !active(brand)
        || brand.getLockVersion() != snapshot.command().brandVersion()
        || brand.getWorkspace().getLockVersion() != snapshot.command().workspaceVersion()
        || !currentSources(snapshot, brand)) {
      attempt.finish(BrandAnalysisAttempt.Status.SUPERSEDED, now);
      attempts.saveAndFlush(attempt);
      return new Completion(false, null);
    }
    snapshot.command().validateCitations(result.citations());
    if (!snapshot.command().sources().isEmpty()
        && (result.warnings().contains(BrandAnalysisResult.Warning.DECLARED_FIELDS_ONLY)
            || !result.warnings().contains(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED)))
      throw new IllegalArgumentException("Invalid evidence analysis provenance");
    for (int i = 0; i < result.citations().size(); i++) {
      var c = result.citations().get(i);
      citations.saveAndFlush(
          new BrandAnalysisCitation(attempt.getId(), i, c.admissionId(), c.quote(), c.fieldPath()));
    }
    BrandSignature signature = result.toSignature();
    brand.updateSignature(signature);
    brands.saveAndFlush(brand);
    // Hibernate skips a value-identical update; publication must still advance the source fence.
    if (brand.getLockVersion() == snapshot.command().brandVersion()) {
      entityManager.lock(brand, jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
    }
    attempt.succeed(
        now,
        result.provider(),
        result.modelVersion(),
        result.promptVersion(),
        result.warnings().stream()
            .distinct()
            .map(Enum::name)
            .collect(java.util.stream.Collectors.joining(",")),
        result.contentChecksum());
    attempts.saveAndFlush(attempt);
    return new Completion(true, signature);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and (#snapshot.actorId() == null or @access.isCurrentUser(authentication,#snapshot.actorId()))")
  public void fail(Snapshot snapshot, Set<UUID> scope) {
    locked(snapshot.brandId(), scope);
    var attempt = attempts.findById(snapshot.command().analysisId()).orElseThrow();
    requireAttempt(snapshot, attempt);
    if (attempt.getStatus() == BrandAnalysisAttempt.Status.RUNNING) {
      attempt.finish(BrandAnalysisAttempt.Status.FAILED, Instant.now(clock));
      attempts.saveAndFlush(attempt);
    }
  }

  private void requireAttempt(Snapshot snapshot, BrandAnalysisAttempt attempt) {
    if (!attempt.getBrandId().equals(snapshot.brandId())
        || attempt.getBrandVersion() != snapshot.command().brandVersion()
        || attempt.getWorkspaceVersion() != snapshot.command().workspaceVersion()
        || !java.util.Objects.equals(attempt.getRequestedBy(), snapshot.actorId())
        || !attempt.getSourceChecksum().equals(snapshot.command().sourceChecksum()))
      throw new IllegalStateException("Analysis snapshot mismatch");
  }

  private Brand lockedOwner(UUID id, Set<UUID> scope, UUID actorId) {
    brands
        .findWorkspaceIdIn(id, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(
                () ->
                    new org.springframework.security.access.AccessDeniedException(
                        "Evidence analysis denied"));
    var brand = locked(id, scope);
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(brand.getWorkspace().getId())))
      throw new org.springframework.security.access.AccessDeniedException(
          "Evidence analysis denied");
    return brand;
  }

  private AnalysisTextSource textSource(EvidenceAdmission a) {
    return AnalysisTextSource.from(a);
  }

  private boolean current(EvidenceAdmission a, Brand brand) {
    var source = evidence.findLockedByIdAndBrandId(a.getEvidenceId(), brand.getId()).orElse(null);
    if (source == null
        || withdrawals.existsForSourceVersion(source.getId(), a.getSourceVersion())
        || a.getSourceVersion() != source.getLockVersion()
        || a.getBrandVersion() != brand.getLockVersion()
        || a.getWorkspaceVersion() != brand.getWorkspace().getLockVersion()
        || !a.getSourceChecksum().equals(source.getChecksum())) return false;
    try {
      if (PdfReviewContent.POLICY_VERSION.equals(a.getPolicyVersion())) {
        pdfPolicy.validate(a, source, brand);
        return true;
      }
      if (UrlReviewContent.POLICY_VERSION.equals(a.getPolicyVersion())) {
        urlPolicy.capture(a, source, brand, brand.getLockVersion());
        return true;
      }
      if (SocialTextProjection.POLICY.equals(a.getPolicyVersion())) {
        social.validate(a, source, brand.getLockVersion());
        return true;
      }
      social.requireOrdinaryText(source);
      if (!EvidenceAdmission.POLICY_VERSION.equals(a.getPolicyVersion())) return false;
      return EvidenceAdmission.reviewableText(source).equals(a.getReviewedText())
          && EvidenceAdmission.checksum(a.getReviewedText()).equals(a.getContentChecksum());
    } catch (IllegalStateException invalid) {
      return false;
    }
  }

  private boolean currentSources(Snapshot snapshot, Brand brand) {
    if (snapshot.actorId() != null
        && !new BrandAnalysisCommand(
                snapshot.command().analysisId(),
                brand.getLockVersion(),
                brand.getWorkspace().getLockVersion(),
                brand.getName(),
                brand.getWorkspace().getIndustry(),
                snapshot.command().sources())
            .sourceChecksum()
            .equals(snapshot.command().sourceChecksum())) return false;
    var captured = sources.findByAttemptIdOrderByPositionAsc(snapshot.command().analysisId());
    if (!captured.stream()
        .filter(s -> PdfReviewContent.POLICY_VERSION.equals(s.getAdmission().getPolicyVersion()))
        .map(s -> s.getAdmission().getId())
        .collect(java.util.stream.Collectors.toSet())
        .equals(snapshot.pdfAdmissions()))
      throw new IllegalStateException("PDF analysis source set mismatch");
    if (!captured.stream()
            .map(s -> textSource(s.getAdmission()))
            .toList()
            .equals(snapshot.command().sources())
        || (snapshot.actorId() != null && captured.isEmpty()))
      throw new IllegalStateException("Analysis source receipt mismatch");
    for (var source : captured) if (!current(source.getAdmission(), brand)) return false;
    return true;
  }

  private boolean active(Brand brand) {
    return brand.getStatus() == BrandStatus.ACTIVE
        && brand.getWorkspace().getStatus() == WorkspaceStatus.ACTIVE;
  }

  private void requireActive(Brand brand) {
    if (!active(brand)) throw new IllegalStateException("Archived brand or workspace");
  }

  @Transactional(
      propagation = Propagation.REQUIRES_NEW,
      isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  @org.springframework.security.access.prepost.PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#snapshot.actorId())")
  public void preflight(Snapshot snapshot, Set<UUID> scope) {
    var brand = lockedOwner(snapshot.brandId(), scope, snapshot.actorId());
    var attempt = attempts.findById(snapshot.command().analysisId()).orElseThrow();
    requireAttempt(snapshot, attempt);
    if (attempt.getStatus() != BrandAnalysisAttempt.Status.RUNNING
        || !clock.instant().isBefore(attempt.getDeadline())
        || !active(brand)
        || brand.getLockVersion() != snapshot.command().brandVersion()
        || brand.getWorkspace().getLockVersion() != snapshot.command().workspaceVersion()
        || !currentSources(snapshot, brand))
      throw new IllegalStateException("Evidence analysis prerequisites changed");
  }
}
