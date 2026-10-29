package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.CadenceUserRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.CadenceUser;
import com.cadence.domain.UserRole;
import com.cadence.domain.Workspace;
import com.cadence.domain.WorkspaceStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("denyAll()")
@Transactional(readOnly = true)
public class WorkspaceManagementUseCase {

  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final int maxWorkspaces;
  private final com.cadence.application.ports.EvidenceTransactionIsolation isolation;

  public WorkspaceManagementUseCase(
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      com.cadence.application.ports.EvidenceTransactionIsolation isolation,
      @org.springframework.beans.factory.annotation.Value("${cadence.workspaces.max-per-account:3}")
          int maxWorkspaces) {
    this.workspaces = workspaces;
    this.users = users;
    this.isolation = isolation;
    if (maxWorkspaces < 1 || maxWorkspaces > 10) {
      throw new IllegalArgumentException("Workspace limit must be between 1 and 10");
    }
    this.maxWorkspaces = maxWorkspaces;
  }

  @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('OWNER') and @access.isCurrentUser(authentication, #ownerId)")
  public Workspace create(UUID ownerId, String name, String industry) {
    isolation.requireReadCommitted();
    CadenceUser owner =
        users
            .findLockedById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));
    if (!owner.isActive() || owner.getRole() != UserRole.OWNER) {
      throw new IllegalStateException("Only an owner can create a workspace");
    }
    // Count after locking the account. Archived tenants also consume its allowance.
    if (users.countWorkspaceMemberships(ownerId) >= maxWorkspaces) {
      throw new com.cadence.application.exceptions.WorkspaceQuotaExceededException();
    }
    Workspace workspace = workspaces.save(new Workspace(name, industry));
    owner.grantAccess(workspace);
    return workspace;
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Page<Workspace> findAll(Set<UUID> permittedWorkspaceIds, Pageable pageable) {
    return permittedWorkspaceIds.isEmpty()
        ? Page.empty(pageable)
        : workspaces.findAllByIdIn(permittedWorkspaceIds, pageable);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Workspace findById(UUID workspaceId, Set<UUID> permittedWorkspaceIds) {
    return workspaces
        .findByIdAndIdIn(workspaceId, permittedWorkspaceIds)
        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found: " + workspaceId));
  }

  @Transactional
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Workspace update(
      UUID workspaceId,
      Set<UUID> permittedWorkspaceIds,
      long expectedVersion,
      String name,
      String industry) {
    Workspace workspace = findById(workspaceId, permittedWorkspaceIds);
    requireVersion(workspace, expectedVersion);
    if (workspace.getStatus() == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException("Archived workspace cannot be updated");
    }
    workspace.rename(name);
    workspace.changeIndustry(industry);
    return workspaces.saveAndFlush(workspace);
  }

  @Transactional
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public Workspace archive(
      UUID workspaceId, Set<UUID> permittedWorkspaceIds, long expectedVersion) {
    Workspace workspace = findById(workspaceId, permittedWorkspaceIds);
    requireVersion(workspace, expectedVersion);
    workspace.archive();
    return workspaces.saveAndFlush(workspace);
  }

  private void requireVersion(Workspace workspace, long expectedVersion) {
    if (workspace.getLockVersion() != expectedVersion) {
      throw new IllegalStateException(
          "Workspace was modified; expected version "
              + expectedVersion
              + " but was "
              + workspace.getLockVersion());
    }
  }
}
