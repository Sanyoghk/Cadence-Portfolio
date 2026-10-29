package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.application.services.EvidenceIdentitySourcePolicy;
import com.cadence.domain.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class IdentityEvidenceUseCase {
  private final CadenceUserRepository users;
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final BrandIdentityRepository identities;
  private final BrandAnalysisAttemptRepository analyses;
  private final EvidenceIdentitySourcePolicy policy;
  private final com.cadence.application.services.IdentitySourceVerification verification;

  public IdentityEvidenceUseCase(
      CadenceUserRepository users,
      WorkspaceRepository workspaces,
      BrandRepository brands,
      BrandIdentityRepository identities,
      BrandAnalysisAttemptRepository analyses,
      EvidenceIdentitySourcePolicy policy,
      com.cadence.application.services.IdentitySourceVerification verification) {
    this.users = users;
    this.workspaces = workspaces;
    this.brands = brands;
    this.identities = identities;
    this.analyses = analyses;
    this.policy = policy;
    this.verification = verification;
  }

  private void requireOwner(CadenceUser actor, UUID workspaceId) {
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actor.getId()).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(workspaceId)))
      throw new AccessDeniedException("Evidence identity denied");
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public BrandIdentityDetails createFromAnalysis(
      UUID brandId, Set<UUID> scope, UUID analysisId, long expectedVersion, UUID actorId) {
    return verification.execute(
        () -> {
          var context = copyContext(brandId, scope, analysisId, expectedVersion, actorId);
          return policy.validate(context.brand(), context.receipt());
        },
        () -> createValidated(brandId, scope, analysisId, expectedVersion, actorId));
  }

  private record CopyContext(Brand brand, BrandAnalysisAttempt receipt) {}

  private CopyContext copyContext(
      UUID brandId, Set<UUID> scope, UUID analysisId, long expectedVersion, UUID actorId) {
    if (analysisId == null || expectedVersion < 0)
      throw new IllegalArgumentException("Invalid evidence identity input");
    var wsId =
        brands
            .findWorkspaceIdIn(brandId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(() -> new AccessDeniedException("Evidence identity denied"));
    workspaces
        .findLockedByIdAndIdIn(wsId, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    var brand =
        brands
            .findLockedWithWorkspaceIn(brandId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    requireOwner(actor, wsId);
    var receipt =
        analyses
            .findByIdAndBrandId(analysisId, brandId)
            .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));
    if (brand.getLockVersion() != expectedVersion || brand.getSignature() == null)
      throw new IllegalStateException("Analysis source is no longer current");
    return new CopyContext(brand, receipt);
  }

  private BrandIdentityDetails createValidated(
      UUID brandId, Set<UUID> scope, UUID analysisId, long expectedVersion, UUID actorId) {
    var context = copyContext(brandId, scope, analysisId, expectedVersion, actorId);
    var brand = context.brand();
    var receipt = context.receipt();
    var signature = brand.getSignature();
    var p = signature.getPalette();
    var content =
        new BrandIdentityContent(
            new BrandIdentityContent.Palette(
                p.getPrimary(), p.getSecondary(), p.getAccent(), p.getNeutral(), p.getBackground()),
            signature.getFonts(),
            signature.getVoiceUp(),
            signature.getVoiceDown(),
            signature.getSampleCaption());
    var latest =
        identities
            .findTopByBrandIdOrderByVersionDesc(brandId)
            .map(BrandIdentity::getVersion)
            .orElse(0);
    if (latest >= BrandIdentity.MAX_REVISIONS_PER_BRAND)
      throw new IllegalStateException("Identity revision limit reached");
    return BrandIdentityDetails.from(
        identities.saveAndFlush(
            BrandIdentity.fromEvidenceAnalysis(brand, latest + 1, content, receipt, actorId)));
  }

  @Transactional(
      readOnly = true,
      propagation = Propagation.REQUIRES_NEW,
      isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public IdentityEvidenceProvenance read(UUID id, Set<UUID> scope, UUID actorId) {
    var identity =
        identities
            .findByIdAndBrandWorkspaceIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found"));
    var actor =
        users
            .findById(actorId)
            .orElseThrow(() -> new AccessDeniedException("Evidence identity denied"));
    requireOwner(actor, identity.getBrand().getWorkspace().getId());
    return policy.describe(identity);
  }
}
