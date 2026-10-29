package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.BuiltStrategy;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class StrategyBuildTransactions {
  public record Snapshot(
      UUID brandId,
      StrategyGenerationCommand command,
      ApprovedIdentitySource source,
      Set<UUID> scope) {
    public Snapshot {
      scope = Set.copyOf(scope);
      Objects.requireNonNull(source);
    }
  }

  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final StrategyRepository strategies;
  private final Clock clock;
  private final BrandIdentityRepository identities;
  private final CadenceUserRepository users;
  private final EvidenceIdentitySourcePolicy evidencePolicy;
  private final IdentitySourceVerification verification;

  public StrategyBuildTransactions(
      BrandRepository brands,
      WorkspaceRepository workspaces,
      StrategyRepository strategies,
      Clock clock,
      BrandIdentityRepository identities,
      CadenceUserRepository users,
      EvidenceIdentitySourcePolicy evidencePolicy,
      IdentitySourceVerification verification) {
    this.brands = brands;
    this.workspaces = workspaces;
    this.strategies = strategies;
    this.clock = clock;
    this.identities = identities;
    this.users = users;
    this.evidencePolicy = evidencePolicy;
    this.verification = verification;
  }

  private Brand locked(UUID id, Set<UUID> scope) {
    UUID actorId = actor();
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(
                () ->
                    new org.springframework.security.access.AccessDeniedException(
                        "Strategy generation denied"));
    var workspace =
        brands
            .findWorkspaceIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    workspaces
        .findLockedById(workspace)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    var brand =
        brands
            .findLockedWithWorkspaceIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(workspace)))
      throw new org.springframework.security.access.AccessDeniedException(
          "Strategy generation denied");
    return brand;
  }

  private void requireActive(Brand brand) {
    if (brand.getStatus() != BrandStatus.ACTIVE
        || brand.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Cannot generate strategy for archived brand or workspace");
  }

  private UUID actor() {
    var auth =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof com.cadence.security.CadencePrincipal p))
      throw new org.springframework.security.access.AccessDeniedException(
          "Strategy generation denied");
    return p.userId();
  }

  private BrandIdentity selected(Brand brand) {
    var latest =
        identities
            .findTopByBrandIdOrderByVersionDesc(brand.getId())
            .orElseThrow(() -> new IllegalStateException("Approved Identity required"));
    var identity = identities.findLockedByIdAndBrandId(latest.getId(), brand.getId()).orElseThrow();
    if (identity.getStatus() != ApprovalStatus.APPROVED
        || identity.getApprovedBy() == null
        || identity.getApprovedAt() == null)
      throw new IllegalStateException("Approved Identity required");
    return identity;
  }

  private ApprovedIdentitySource source(BrandIdentity identity) {
    var analysis = evidencePolicy.origin(identity).getAnalysisAttempt();
    return new ApprovedIdentitySource(
        identity.getId(),
        identity.getVersion(),
        identity.getLockVersion(),
        identity.getContent().checksum(),
        identity.getApprovedBy(),
        identity.getApprovedAt(),
        analysis == null ? null : analysis.getId(),
        actor());
  }

  private StrategyGenerationCommand.Signature signature(BrandIdentity identity) {
    var c = identity.getContent();
    var p = c.palette();
    return new StrategyGenerationCommand.Signature(
        new BrandAnalysisResult.Palette(
            p.primary(), p.secondary(), p.accent(), p.neutral(), p.background()),
        c.fonts(),
        c.voiceUp(),
        c.voiceDown(),
        c.sampleCaption());
  }

  private int nextVersion(UUID brandId) {
    int current =
        strategies.findTopByBrandIdOrderByVersionDesc(brandId).map(Strategy::getVersion).orElse(0);
    if (current == Integer.MAX_VALUE)
      throw new IllegalStateException("Strategy version limit reached");
    return current + 1;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public Snapshot snapshot(UUID brandId, Set<UUID> scope) {
    Brand brand = locked(brandId, scope);
    requireActive(brand);
    nextVersion(brandId);
    var identity = selected(brand);
    evidencePolicy.validateForApproval(identity, brand);
    return new Snapshot(
        brandId,
        new StrategyGenerationCommand(
            UUID.randomUUID(),
            brand.getLockVersion(),
            brand.getWorkspace().getLockVersion(),
            brand.getName(),
            brand.getWorkspace().getIndustry(),
            signature(identity),
            Instant.now(clock)),
        source(identity),
        scope);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public void preflight(Snapshot snapshot, Set<UUID> scope) {
    verification.execute(() -> current(snapshot, scope), () -> Boolean.TRUE);
  }

  private List<EvidencePhysicalSource> current(Snapshot snapshot, Set<UUID> scope) {
    if (!snapshot.scope().equals(scope) || !snapshot.source().requestedBy().equals(actor()))
      throw new org.springframework.security.access.AccessDeniedException(
          "Strategy generation denied");
    Brand brand = locked(snapshot.brandId(), scope);
    requireActive(brand);
    var command = snapshot.command();
    var identity = selected(brand);
    if (!clock.instant().isBefore(command.deadline())
        || brand.getLockVersion() != command.brandVersion()
        || brand.getWorkspace().getLockVersion() != command.workspaceVersion()
        || !brand.getName().strip().equals(command.brandName())
        || !brand.getWorkspace().getIndustry().strip().equals(command.industry())
        || !source(identity).equals(snapshot.source())
        || !signature(identity).equals(command.signature()))
      throw new IllegalStateException("Strategy generation source changed or deadline expired");
    return evidencePolicy.validateForApproval(identity, brand);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public BuiltStrategy complete(
      Snapshot snapshot, Set<UUID> scope, StrategyGenerationResult result) {
    Objects.requireNonNull(result).requireCompletePlaybook();
    return verification.execute(
        () -> current(snapshot, scope), () -> publish(snapshot, scope, result));
  }

  private BuiltStrategy publish(
      Snapshot snapshot, Set<UUID> scope, StrategyGenerationResult result) {
    Brand brand = locked(snapshot.brandId(), scope);
    var command = snapshot.command();
    var strategy =
        new Strategy(
            brand,
            nextVersion(brand.getId()),
            new PublishingCadence(result.postsPerWeek(), result.timezone(), result.bestTimes()),
            result.pillars().stream()
                .map(p -> new ContentPillar(p.name(), p.weight(), p.description()))
                .toList());
    strategy.replacePlaybook(result.content(), result.playbook());
    strategy.attachGeneration(
        new StrategyGenerationProvenance(
            command.generationId(),
            command.brandVersion(),
            command.workspaceVersion(),
            command.startedAt(),
            result.provider(),
            result.modelVersion(),
            result.promptVersion(),
            result.warnings().stream()
                .distinct()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.joining(","))));
    strategy.getGeneration().attachIdentity(snapshot.source());
    strategy.getGeneration().attachOutput(result.content(), result.playbook());
    strategies.saveAndFlush(strategy);
    return new BuiltStrategy(
        strategy.getId(), brand.getId(), strategy.getVersion(), strategy.getStatus(), result);
  }
}
