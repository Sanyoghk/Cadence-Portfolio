package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Internal policy. Every entry authorizes persisted actor and owns parent locks. No HTTP principal
 * fabrication.
 */
@Service
public class AiGenerationSources {
  public record Input(
      BrandAnalysisCommand analysis,
      StrategyGenerationCommand strategy,
      ApprovedIdentitySource identity,
      ContentCapture content) {
    public Input(
        BrandAnalysisCommand analysis,
        StrategyGenerationCommand strategy,
        ApprovedIdentitySource identity) {
      this(analysis, strategy, identity, null);
    }
  }

  public record Capture(
      Brand brand, Input input, List<EvidencePhysicalSource> physical, Set<UUID> admissions) {}

  private final CadenceUserRepository users;
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final BrandIdentityRepository identities;
  private final EvidenceIdentitySourcePolicy identityPolicy;
  private final EvidenceAdmissionRepository admissions;
  private final BrandEvidenceRepository evidence;
  private final EvidenceAdmissionWithdrawalRepository withdrawals;
  private final PdfAnalysisSourcePolicy pdf;
  private final UrlAnalysisSourcePolicy url;
  private final SocialAnalysisSourcePolicy social;
  private final ContentSourcePolicy contentPolicy;

  public AiGenerationSources(
      CadenceUserRepository users,
      WorkspaceRepository workspaces,
      BrandRepository brands,
      BrandIdentityRepository identities,
      EvidenceIdentitySourcePolicy identityPolicy,
      EvidenceAdmissionRepository admissions,
      BrandEvidenceRepository evidence,
      EvidenceAdmissionWithdrawalRepository withdrawals,
      PdfAnalysisSourcePolicy pdf,
      UrlAnalysisSourcePolicy url,
      SocialAnalysisSourcePolicy social,
      ContentSourcePolicy contentPolicy) {
    this.contentPolicy = contentPolicy;
    this.users = users;
    this.workspaces = workspaces;
    this.brands = brands;
    this.identities = identities;
    this.identityPolicy = identityPolicy;
    this.admissions = admissions;
    this.evidence = evidence;
    this.withdrawals = withdrawals;
    this.pdf = pdf;
    this.url = url;
    this.social = social;
  }

  public Brand lockParents(UUID actorId, UUID brandId, Set<UUID> scope) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("Generation requires transaction");
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(
                () ->
                    new org.springframework.security.access.AccessDeniedException(
                        "Generation denied"));
    var ws =
        brands
            .findWorkspaceIdIn(brandId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    workspaces.findLockedById(ws).orElseThrow();
    var brand = brands.findLockedWithWorkspaceIn(brandId, scope).orElseThrow();
    return brand;
  }

  public Brand authorize(UUID actorId, UUID brandId, Set<UUID> scope) {
    var brand = lockParents(actorId, brandId, scope);
    var actor = users.findWithWorkspacesById(actorId).orElseThrow();
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || !users.hasWorkspaceMembership(actorId, brand.getWorkspace().getId()))
      throw new org.springframework.security.access.AccessDeniedException("Generation denied");
    return brand;
  }

  public Capture capture(
      UUID actor,
      UUID brandId,
      Set<UUID> scope,
      AiGenerationOperation.Kind kind,
      Instant submitted,
      Input frozen) {
    if (kind == AiGenerationOperation.Kind.CONTENT) {
      if (frozen == null || frozen.content() == null)
        throw new IllegalStateException("Content request required");
      return captureContent(actor, brandId, scope, frozen.content().request(), submitted, frozen);
    }
    var brand = authorize(actor, brandId, scope);
    if (brand.getStatus() != BrandStatus.ACTIVE
        || brand.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Inactive generation source");
    var physical = new ArrayList<EvidencePhysicalSource>();
    var ids = new LinkedHashSet<UUID>();
    Input input;
    UUID commandId =
        frozen == null
            ? UUID.randomUUID()
            : (kind == AiGenerationOperation.Kind.STRATEGY
                ? frozen.strategy().generationId()
                : frozen.analysis().analysisId());
    if (kind == AiGenerationOperation.Kind.STRATEGY) {
      var latest =
          identities
              .findTopByBrandIdOrderByVersionDesc(brandId)
              .orElseThrow(() -> new IllegalStateException("Approved Identity required"));
      var identity = identities.findLockedByIdAndBrandId(latest.getId(), brandId).orElseThrow();
      if (identity.getStatus() != ApprovalStatus.APPROVED
          || identity.getApprovedBy() == null
          || identity.getApprovedAt() == null)
        throw new IllegalStateException("Approved Identity required");
      physical.addAll(identityPolicy.validateForApproval(identity, brand));
      var receipt = identityPolicy.origin(identity).getAnalysisAttempt();
      var source =
          new ApprovedIdentitySource(
              identity.getId(),
              identity.getVersion(),
              identity.getLockVersion(),
              identity.getContent().checksum(),
              identity.getApprovedBy(),
              identity.getApprovedAt(),
              receipt == null ? null : receipt.getId(),
              actor);
      var c = identity.getContent();
      var palette = c.palette();
      var signature =
          new StrategyGenerationCommand.Signature(
              new BrandAnalysisResult.Palette(
                  palette.primary(),
                  palette.secondary(),
                  palette.accent(),
                  palette.neutral(),
                  palette.background()),
              c.fonts(),
              c.voiceUp(),
              c.voiceDown(),
              c.sampleCaption());
      input =
          new Input(
              null,
              new StrategyGenerationCommand(
                  commandId,
                  brand.getLockVersion(),
                  brand.getWorkspace().getLockVersion(),
                  brand.getName(),
                  brand.getWorkspace().getIndustry(),
                  signature,
                  submitted),
              source);
    } else {
      var selected = new ArrayList<EvidenceAdmission>();
      if (kind == AiGenerationOperation.Kind.EVIDENCE) {
        if (frozen == null)
          selected.addAll(
              admissions.findCurrentReviewed(
                  brandId,
                  brand.getLockVersion(),
                  brand.getWorkspace().getLockVersion(),
                  PageRequest.of(0, 6)));
        else
          for (var a : frozen.analysis().sources())
            selected.add(
                admissions
                    .findForEvidence(a.admissionId(), a.evidenceId())
                    .orElseThrow(() -> new IllegalStateException("Missing admitted source")));
        if (selected.isEmpty() || selected.size() > 5)
          throw new IllegalStateException("No bounded evidence");
      }
      var texts = new ArrayList<AnalysisTextSource>();
      for (var a : selected) {
        var e =
            evidence
                .findLockedByIdAndBrandId(a.getEvidenceId(), brandId)
                .orElseThrow(() -> new IllegalStateException("Foreign source"));
        if (a.getSourceVersion() != e.getLockVersion()
            || a.getBrandVersion() != brand.getLockVersion()
            || a.getWorkspaceVersion() != brand.getWorkspace().getLockVersion()
            || !a.getSourceChecksum().equals(e.getChecksum())
            || withdrawals.existsForSourceVersion(e.getId(), a.getSourceVersion()))
          throw new IllegalStateException("Stale source");
        if (PdfReviewContent.POLICY_VERSION.equals(a.getPolicyVersion()))
          physical.add(pdf.capture(a, e, brand, brand.getLockVersion()));
        else if (UrlReviewContent.POLICY_VERSION.equals(a.getPolicyVersion()))
          physical.add(url.capture(a, e, brand, brand.getLockVersion()));
        else if (SocialTextProjection.POLICY.equals(a.getPolicyVersion()))
          social.validate(a, e, brand.getLockVersion());
        else {
          social.requireOrdinaryText(e);
          if (!EvidenceAdmission.POLICY_VERSION.equals(a.getPolicyVersion())
              || !EvidenceAdmission.reviewableText(e).equals(a.getReviewedText())
              || !EvidenceAdmission.checksum(a.getReviewedText()).equals(a.getContentChecksum()))
            throw new IllegalStateException("Invalid source");
        }
        ids.add(a.getId());
        texts.add(AnalysisTextSource.from(a));
      }
      input =
          new Input(
              new BrandAnalysisCommand(
                  commandId,
                  brand.getLockVersion(),
                  brand.getWorkspace().getLockVersion(),
                  brand.getName(),
                  brand.getWorkspace().getIndustry(),
                  texts),
              null,
              null);
    }
    if (frozen != null && !input.equals(frozen))
      throw new IllegalStateException("Captured generation source changed");
    return new Capture(brand, input, List.copyOf(physical), Set.copyOf(ids));
  }

  public Capture captureContent(
      UUID actor,
      UUID brandId,
      Set<UUID> scope,
      ContentGenerationRequest request,
      Instant submitted,
      Input frozen) {
    var base =
        capture(
            actor,
            brandId,
            scope,
            AiGenerationOperation.Kind.STRATEGY,
            submitted,
            frozen == null
                ? null
                : new Input(null, frozen.content().identityContext(), frozen.identity()));
    var content =
        contentPolicy.capture(
            base.brand(),
            base.input().strategy(),
            base.input().identity(),
            request,
            frozen == null ? null : frozen.content());
    return new Capture(
        base.brand(),
        new Input(null, null, base.input().identity(), content),
        base.physical(),
        Set.of());
  }
}
