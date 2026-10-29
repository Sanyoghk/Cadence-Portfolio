package com.cadence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.CadenceUserRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.application.usecases.WorkspaceManagementUseCase;
import com.cadence.domain.CadenceUser;
import com.cadence.domain.UserRole;
import com.cadence.domain.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class WorkspaceManagementUseCaseTest {
  @Test
  void rejectsCreationAtTheAccountLimitBeforePersistingAnything() {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    CadenceUserRepository users = mock(CadenceUserRepository.class);
    UUID ownerId = UUID.randomUUID();
    when(users.findLockedById(ownerId))
        .thenReturn(Optional.of(new CadenceUser("owner", UserRole.OWNER, List.of())));
    when(users.countWorkspaceMemberships(ownerId)).thenReturn(3L);
    var useCase =
        new WorkspaceManagementUseCase(
            workspaces,
            users,
            mock(com.cadence.application.ports.EvidenceTransactionIsolation.class),
            3);
    assertThrows(
        com.cadence.application.exceptions.WorkspaceQuotaExceededException.class,
        () -> useCase.create(ownerId, "Another", "Creator"));
    org.mockito.Mockito.verifyNoInteractions(workspaces);
  }

  @Test
  void shouldCreateWorkspaceAndGrantOwnerAccess() {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    CadenceUserRepository users = mock(CadenceUserRepository.class);
    CadenceUser owner = new CadenceUser("creator-1", UserRole.OWNER, List.of());
    UUID ownerId = UUID.randomUUID();
    when(users.findLockedById(ownerId)).thenReturn(Optional.of(owner));
    when(workspaces.save(any(Workspace.class))).thenAnswer(call -> call.getArgument(0));
    WorkspaceManagementUseCase useCase =
        new WorkspaceManagementUseCase(
            workspaces,
            users,
            mock(com.cadence.application.ports.EvidenceTransactionIsolation.class),
            3);

    Workspace result = useCase.create(ownerId, "Studio", "Creator");

    assertEquals("Studio", result.getName());
    assertEquals(Set.of(result), owner.getWorkspaces());
    verify(workspaces).save(result);
  }

  @Test
  void shouldRejectUnknownOwner() {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    CadenceUserRepository users = mock(CadenceUserRepository.class);
    UUID ownerId = UUID.randomUUID();
    when(users.findLockedById(ownerId)).thenReturn(Optional.empty());

    WorkspaceManagementUseCase useCase =
        new WorkspaceManagementUseCase(
            workspaces,
            users,
            mock(com.cadence.application.ports.EvidenceTransactionIsolation.class),
            3);

    assertThrows(
        ResourceNotFoundException.class, () -> useCase.create(ownerId, "Studio", "Creator"));
  }

  @Test
  void shouldListOnlyPermittedWorkspaces() {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    CadenceUserRepository users = mock(CadenceUserRepository.class);
    UUID workspaceId = UUID.randomUUID();
    Workspace workspace = new Workspace("Studio", "Creator");
    PageRequest page = PageRequest.of(0, 20);
    when(workspaces.findAllByIdIn(Set.of(workspaceId), page))
        .thenReturn(new PageImpl<>(List.of(workspace), page, 1));

    WorkspaceManagementUseCase useCase =
        new WorkspaceManagementUseCase(
            workspaces,
            users,
            mock(com.cadence.application.ports.EvidenceTransactionIsolation.class),
            3);

    assertEquals(List.of(workspace), useCase.findAll(Set.of(workspaceId), page).getContent());
  }

  @Test
  void shouldUpdateArchiveAndRejectStaleWorkspace() {
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    CadenceUserRepository users = mock(CadenceUserRepository.class);
    UUID workspaceId = UUID.randomUUID();
    Set<UUID> permitted = Set.of(workspaceId);
    Workspace workspace = new Workspace("Studio", "Creator");
    when(workspaces.findByIdAndIdIn(workspaceId, permitted)).thenReturn(Optional.of(workspace));
    when(workspaces.saveAndFlush(workspace)).thenReturn(workspace);
    WorkspaceManagementUseCase useCase =
        new WorkspaceManagementUseCase(
            workspaces,
            users,
            mock(com.cadence.application.ports.EvidenceTransactionIsolation.class),
            3);

    Workspace updated = useCase.update(workspaceId, permitted, 0, "New Studio", "Retail");
    assertEquals("New Studio", updated.getName());
    assertEquals("Retail", updated.getIndustry());
    assertThrows(
        IllegalStateException.class,
        () -> useCase.update(workspaceId, permitted, 3, "Stale", "Retail"));
    assertEquals(
        com.cadence.domain.WorkspaceStatus.ARCHIVED,
        useCase.archive(workspaceId, permitted, 0).getStatus());
  }
}
