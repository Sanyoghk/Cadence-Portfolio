package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.BrandIdentityDetails;
import com.cadence.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
@Transactional(readOnly = true)
public class BrandIdentityUseCase {
  public record UpdatedIdentity(BrandIdentityDetails details, boolean created) {}

  private final BrandIdentityRepository identities;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final Clock clock;
  private final com.cadence.application.services.IdentitySourceVerification verification;
  private final BrandAnalysisAttemptRepository analyses;
  private final com.cadence.application.services.EvidenceIdentitySourcePolicy evidencePolicy;

  public BrandIdentityUseCase(
      BrandIdentityRepository identities,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      Clock clock,
      BrandAnalysisAttemptRepository analyses,
      com.cadence.application.services.EvidenceIdentitySourcePolicy evidencePolicy,
      com.cadence.application.services.IdentitySourceVerification verification) {
    this.identities = identities;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.clock = clock;
    this.analyses = analyses;
    this.evidencePolicy = evidencePolicy;
    this.verification = verification;
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public BrandIdentityDetails createFromAnalysis(
      UUID brandId, Set<UUID> scope, UUID analysisId, long expectedVersion) {
    if (expectedVersion < 0) throw new IllegalArgumentException("Invalid analysis source");
    var brand = lockedBrand(brandId, scope);
    // Do not search the analysis history using an arbitrary client-supplied version.
    if (analysisId == null && (expectedVersion < 1 || brand.getLockVersion() != expectedVersion))
      throw new IllegalStateException("Analysis source is no longer current");
    var attempt =
        analysisId == null
            ? analyses
                .findFirstByBrandIdAndBrandVersionAndWorkspaceVersionAndStatusOrderByStartedAtDescIdDesc(
                    brandId,
                    expectedVersion - 1,
                    brand.getWorkspace().getLockVersion(),
                    BrandAnalysisAttempt.Status.SUCCEEDED)
                .orElseThrow(() -> new IllegalStateException("No current successful analysis"))
            : analyses
                .findByIdAndBrandId(analysisId, brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));
    if (brand.getLockVersion() != expectedVersion
        || attempt.getStatus() != BrandAnalysisAttempt.Status.SUCCEEDED
        || attempt.getBrandVersion() != expectedVersion - 1
        || attempt.getWorkspaceVersion() != brand.getWorkspace().getLockVersion()
        || brand.getSignature() == null)
      throw new IllegalStateException("Analysis source is no longer current");
    var signature = brand.getSignature();
    var palette = signature.getPalette();
    var content =
        new BrandIdentityContent(
            new BrandIdentityContent.Palette(
                palette.getPrimary(),
                palette.getSecondary(),
                palette.getAccent(),
                palette.getNeutral(),
                palette.getBackground()),
            signature.getFonts(),
            signature.getVoiceUp(),
            signature.getVoiceDown(),
            signature.getSampleCaption());
    int latest =
        identities
            .findTopByBrandIdOrderByVersionDesc(brandId)
            .map(BrandIdentity::getVersion)
            .orElse(0);
    if (latest >= BrandIdentity.MAX_REVISIONS_PER_BRAND)
      throw new IllegalStateException("Identity revision limit reached");
    return BrandIdentityDetails.from(
        identities.saveAndFlush(BrandIdentity.fromAnalysis(brand, latest + 1, content, attempt)));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public BrandIdentityDetails create(UUID brandId, Set<UUID> scope, BrandIdentityContent content) {
    if (content == null) throw new IllegalArgumentException("Identity content is required");
    var brand = lockedBrand(brandId, scope);
    int latest =
        identities
            .findTopByBrandIdOrderByVersionDesc(brandId)
            .map(BrandIdentity::getVersion)
            .orElse(0);
    if (latest >= BrandIdentity.MAX_REVISIONS_PER_BRAND)
      throw new IllegalStateException("Identity revision limit reached");
    return BrandIdentityDetails.from(
        identities.saveAndFlush(new BrandIdentity(brand, latest + 1, content)));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public UpdatedIdentity update(
      UUID id, Set<UUID> scope, long expectedVersion, BrandIdentityContent content) {
    if (expectedVersion < 0 || content == null)
      throw new IllegalArgumentException("Invalid identity update");
    var brandId =
        identities
            .findBrandIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found"));
    lockedBrand(brandId, scope);
    var identity =
        identities
            .findLockedByIdAndBrandId(id, brandId)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found"));
    if (identity.getLockVersion() != expectedVersion)
      throw new IllegalStateException("Identity was modified");
    boolean created = identity.getStatus() != ApprovalStatus.DRAFT;
    if (created) {
      int latest =
          identities.findTopByBrandIdOrderByVersionDesc(brandId).orElseThrow().getVersion();
      if (latest >= BrandIdentity.MAX_REVISIONS_PER_BRAND)
        throw new IllegalStateException("Identity revision limit reached");
      identity = identity.createDraftVersion(latest + 1, content);
    } else identity.replaceContent(content);
    identities.saveAndFlush(identity);
    return new UpdatedIdentity(BrandIdentityDetails.from(identity), created);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public BrandIdentityDetails approve(
      UUID id, Set<UUID> scope, long expectedVersion, UUID actorId) {
    return verification.execute(
        () -> {
          var identity = approvalContext(id, scope, expectedVersion, actorId);
          return evidencePolicy.validateForApproval(identity, identity.getBrand());
        },
        () -> {
          var identity = approvalContext(id, scope, expectedVersion, actorId);
          identity.approve(actorId, clock.instant());
          identities.flush();
          return BrandIdentityDetails.from(identity);
        });
  }

  private BrandIdentity approvalContext(
      UUID id, Set<UUID> scope, long expectedVersion, UUID actorId) {
    if (expectedVersion < 0) throw new IllegalArgumentException("Invalid expected version");
    var brandId =
        identities
            .findBrandIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found"));
    // Account lock precedes all parent/revision locks, including membership writers.
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(() -> new AccessDeniedException("Approval denied"));
    var brand = lockedBrand(brandId, scope);
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(brand.getWorkspace().getId())))
      throw new AccessDeniedException("Approval denied");
    var identity =
        identities
            .findLockedByIdAndBrandId(id, brandId)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found"));
    if (identity.getLockVersion() != expectedVersion)
      throw new IllegalStateException("Identity was modified");
    // Fresh transaction reads committed draft; approval never binds editable request content.
    return identity;
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public BrandIdentityDetails findById(UUID id, Set<UUID> scope) {
    return BrandIdentityDetails.from(
        identities
            .findByIdAndBrandWorkspaceIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Identity not found")));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public Page<BrandIdentityDetails> findAll(UUID brandId, Set<UUID> scope, Pageable page) {
    if (page == null
        || page.isUnpaged()
        || page.getPageSize() > 100
        || page.getPageNumber() > 10000
        || page.getSort().stream()
            .anyMatch(
                o -> !Set.of("id", "version", "createdAt", "updatedAt").contains(o.getProperty())))
      throw new IllegalArgumentException("Invalid identity pagination");
    brands
        .findByIdAndWorkspaceIdIn(brandId, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    return identities.findAllByBrandId(brandId, page).map(BrandIdentityDetails::from);
  }

  private Brand lockedBrand(UUID id, Set<UUID> scope) {
    var workspaceId =
        brands
            .findWorkspaceIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    var workspace =
        workspaces
            .findLockedByIdAndIdIn(workspaceId, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    var brand =
        brands
            .findLockedWithWorkspaceIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    if (workspace.getStatus() != WorkspaceStatus.ACTIVE || brand.getStatus() != BrandStatus.ACTIVE)
      throw new IllegalStateException("Cannot change identity for archived parent");
    return brand;
  }
}
