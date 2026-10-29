package com.cadence.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.application.usecases.BrandManagementUseCase;
import com.cadence.domain.Brand;
import com.cadence.domain.BrandStatus;
import com.cadence.domain.Workspace;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrandManagementUseCaseTest {

  @Test
  void shouldCreateBrandForWorkspace() {
    BrandRepository brands = mock(BrandRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    UUID workspaceId = UUID.randomUUID();
    Workspace workspace = new Workspace("Brin Studio", "Luxury food");
    when(workspaces.findLockedByIdAndIdIn(workspaceId, Set.of(workspaceId)))
        .thenReturn(Optional.of(workspace));
    when(brands.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
    BrandManagementUseCase useCase = new BrandManagementUseCase(brands, workspaces);

    Brand result =
        useCase.create(
            workspaceId,
            Set.of(workspaceId),
            " Brin de Caviar ",
            " https://brindecaviar.com ",
            " @brindecaviar ",
            " ");

    assertEquals("Brin de Caviar", result.getName());
    assertEquals("https://brindecaviar.com", result.getWebsiteUrl());
    assertEquals("@brindecaviar", result.getInstagramHandle());
    assertNull(result.getTiktokHandle());
    assertEquals(workspace, result.getWorkspace());
    verify(brands).save(result);
  }

  @Test
  void shouldFailWhenWorkspaceDoesNotExist() {
    BrandRepository brands = mock(BrandRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    UUID workspaceId = UUID.randomUUID();
    when(workspaces.findLockedByIdAndIdIn(workspaceId, Set.of(workspaceId)))
        .thenReturn(Optional.empty());
    BrandManagementUseCase useCase = new BrandManagementUseCase(brands, workspaces);

    assertThrows(
        ResourceNotFoundException.class,
        () -> useCase.create(workspaceId, Set.of(workspaceId), "Brin", null, null, null));
  }

  @Test
  void shouldUpdateAndArchiveBrandWithinPermittedWorkspace() {
    BrandRepository brands = mock(BrandRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    UUID workspaceId = UUID.randomUUID();
    UUID brandId = UUID.randomUUID();
    Set<UUID> permitted = Set.of(workspaceId);
    Brand brand = new Brand("Brand", new Workspace("Studio", "Creator"));
    when(brands.findWorkspaceIdIn(brandId, permitted)).thenReturn(Optional.of(workspaceId));
    when(workspaces.findLockedByIdAndIdIn(workspaceId, permitted))
        .thenReturn(Optional.of(brand.getWorkspace()));
    when(brands.findLockedWithWorkspaceIn(brandId, permitted)).thenReturn(Optional.of(brand));
    when(brands.saveAndFlush(brand)).thenReturn(brand);
    BrandManagementUseCase useCase = new BrandManagementUseCase(brands, workspaces);

    Brand updated =
        useCase.update(brandId, permitted, 0, "New Brand", " https://brand.test ", " ", "@b");
    assertEquals("New Brand", updated.getName());
    assertEquals("https://brand.test", updated.getWebsiteUrl());
    assertNull(updated.getInstagramHandle());
    assertEquals(BrandStatus.ARCHIVED, useCase.archive(brandId, permitted, 0).getStatus());
  }

  @Test
  void shouldRejectBrandOutsidePermittedWorkspaces() {
    BrandRepository brands = mock(BrandRepository.class);
    WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    UUID brandId = UUID.randomUUID();
    Set<UUID> permitted = Set.of(UUID.randomUUID());
    when(brands.findByIdAndWorkspaceIdIn(brandId, permitted)).thenReturn(Optional.empty());
    BrandManagementUseCase useCase = new BrandManagementUseCase(brands, workspaces);

    assertThrows(ResourceNotFoundException.class, () -> useCase.findById(brandId, permitted));
  }
}
