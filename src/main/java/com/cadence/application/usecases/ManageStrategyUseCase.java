package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.StrategyDetails;
import com.cadence.domain.*;
import java.time.Clock;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class ManageStrategyUseCase {
  public record UpdatedStrategy(StrategyDetails details, boolean created) {}

  private final StrategyRepository strategies;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final Clock clock;

  public ManageStrategyUseCase(
      StrategyRepository strategies,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      Clock clock) {
    this.strategies = strategies;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public UpdatedStrategy update(
      UUID id, Set<UUID> scope, long expectedVersion, StrategyContent content) {
    return updatePlaybook(id, scope, expectedVersion, content, null);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public UpdatedStrategy updatePlaybook(
      UUID id,
      Set<UUID> scope,
      long expectedVersion,
      StrategyContent content,
      PlaybookContent playbook) {
    if (content == null) throw new IllegalArgumentException("Strategy content is required");
    strategies
        .findBrandIdIn(id, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Strategy not found"));
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof com.cadence.security.CadencePrincipal))
      throw new AccessDeniedException("Edit denied");
    UUID actorId = ((com.cadence.security.CadencePrincipal) authentication.getPrincipal()).userId();
    var actor =
        users.findLockedById(actorId).orElseThrow(() -> new AccessDeniedException("Edit denied"));
    Strategy strategy = locked(id, scope);
    UUID editWorkspace = strategy.getBrand().getWorkspace().getId();
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(editWorkspace)))
      throw new AccessDeniedException("Edit denied");
    if (strategy.getContentFormatVersion() == 2 && playbook == null)
      throw new IllegalStateException("Full Playbook replacement required");
    requireVersion(strategy, expectedVersion);
    boolean created = strategy.getStatus() != ApprovalStatus.DRAFT;
    if (created) {
      int latest =
          strategies
              .findTopByBrandIdOrderByVersionDesc(strategy.getBrand().getId())
              .orElseThrow()
              .getVersion();
      if (latest == Integer.MAX_VALUE)
        throw new IllegalStateException("Strategy version limit reached");
      strategy = strategy.createDraftVersion(latest + 1);
    }
    if (playbook == null) strategy.replaceContent(content);
    else strategy.replacePlaybook(content, playbook);
    strategy.recordHumanEdit(actorId, clock.instant());
    strategies.saveAndFlush(strategy);
    return new UpdatedStrategy(StrategyDetails.from(strategy), created);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope) and @access.isCurrentUser(authentication, #actorId)")
  public StrategyDetails approve(UUID id, Set<UUID> scope, long expectedVersion, UUID actorId) {
    // Resolve visibility before account checks so foreign and missing resources share a 404.
    strategies
        .findBrandIdIn(id, scope)
        .orElseThrow(() -> new ResourceNotFoundException("Strategy not found"));
    // Account precedes Workspace when an operation uses both (same as Workspace admission).
    var actor =
        users
            .findLockedById(actorId)
            .orElseThrow(() -> new AccessDeniedException("Approval denied"));
    Strategy strategy = locked(id, scope);
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || users.findWithWorkspacesById(actorId).orElseThrow().getWorkspaces().stream()
            .noneMatch(w -> w.getId().equals(strategy.getBrand().getWorkspace().getId())))
      throw new AccessDeniedException("Approval denied");
    requireVersion(strategy, expectedVersion);
    // No content mutation occurs in this transaction; approve the committed draft we just locked.
    strategy.approve(actorId, clock.instant());
    strategies.flush();
    return StrategyDetails.from(strategy);
  }

  private Strategy locked(UUID id, Set<UUID> scope) {
    UUID brandId =
        strategies
            .findBrandIdIn(id, scope)
            .orElseThrow(() -> new ResourceNotFoundException("Strategy not found"));
    UUID workspaceId = brands.findWorkspaceIdIn(brandId, scope).orElseThrow();
    var workspace = workspaces.findLockedByIdAndIdIn(workspaceId, scope).orElseThrow();
    var brand = brands.findLockedWithWorkspaceIn(brandId, scope).orElseThrow();
    if (workspace.getStatus() != WorkspaceStatus.ACTIVE || brand.getStatus() != BrandStatus.ACTIVE)
      throw new IllegalStateException("Cannot change strategy for archived parent");
    return strategies
        .findLockedByIdAndBrandId(id, brandId)
        .orElseThrow(() -> new ResourceNotFoundException("Strategy not found"));
  }

  private void requireVersion(Strategy strategy, long expected) {
    if (expected < 0) throw new IllegalArgumentException("Invalid expected version");
    if (strategy.getLockVersion() != expected)
      throw new IllegalStateException("Strategy was modified");
  }
}
