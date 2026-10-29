package com.cadence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class UseCaseAuthorizationTest {
  private final BrandRepository brands = mock(BrandRepository.class);
  private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
  private final BrandEvidenceRepository evidence = mock(BrandEvidenceRepository.class);
  private final BrandIngestService ingest = mock(BrandIngestService.class);
  private final DurableJobQueue jobs = mock(DurableJobQueue.class);
  private final EvidenceObjectStorage storage = mock(EvidenceObjectStorage.class);
  private final EvidenceMalwareScanner scanner = mock(EvidenceMalwareScanner.class);
  private final UUID target = UUID.randomUUID();
  private final Set<UUID> permitted = Set.of(UUID.randomUUID());
  private final PageRequest page = PageRequest.of(0, 20);

  private RegisterBrandEvidenceUseCase registration() {
    return new RegisterBrandEvidenceUseCase(
        brands,
        evidence,
        mock(QuarantineWriteIntentRepository.class),
        jobs,
        storage,
        scanner,
        mock(org.springframework.transaction.PlatformTransactionManager.class),
        java.time.Clock.systemUTC(),
        mock(QuarantineLifecycle.class),
        mock(com.cadence.application.services.EvidenceRegistrationQuota.class));
  }

  @Test
  void directCallsCannotReadOrMutateAnotherWorkspace() {
    var management = new BrandManagementUseCase(brands, workspaces);
    assertThrows(
        ResourceNotFoundException.class,
        () -> management.create(target, permitted, "Brand", null, null, null));
    assertThrows(
        ResourceNotFoundException.class, () -> management.findAll(target, permitted, page));
    assertThrows(
        ResourceNotFoundException.class, () -> registration().findAll(target, permitted, page));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            new AnalyzeBrandUseCase(
                    brands,
                    ingest,
                    mock(com.cadence.application.services.BrandAnalysisTransactions.class),
                    mock(com.cadence.application.services.GenerationBudgets.class))
                .execute(target, permitted));
    assertThrows(
        ResourceNotFoundException.class,
        () -> registration().register(target, permitted, EvidenceType.TEXT, "Text"));
    assertThrows(
        ResourceNotFoundException.class,
        () ->
            registration()
                .registerFile(target, permitted, "file.pdf", "application/pdf", new byte[1]));
    verify(brands, never()).findById(any());
    verify(brands, never()).save(any());
    verify(brands, never()).findAllByWorkspaceId(any(), any());
    verifyNoInteractions(evidence, ingest, jobs, storage, scanner);
  }

  @Test
  void scopedListsRemainAvailableToMembers() {
    Workspace workspace = new Workspace("Studio", "Creator");
    Brand brand = new Brand("Brand", workspace);
    when(workspaces.findByIdAndIdIn(target, permitted)).thenReturn(Optional.of(workspace));
    when(brands.findByIdAndWorkspaceIdIn(target, permitted)).thenReturn(Optional.of(brand));
    when(brands.findAllByWorkspaceId(target, page)).thenReturn(Page.empty());
    when(evidence.findAllByBrandId(target, page)).thenReturn(Page.empty());
    assertTrue(
        new BrandManagementUseCase(brands, workspaces).findAll(target, permitted, page).isEmpty());
    assertTrue(registration().findAll(target, permitted, page).isEmpty());
    verify(evidence).findAllByBrandId(target, page);
  }

  @Test
  void archivedWorkspaceRejectsAnalysisUpdatesAndIngestBeforeSideEffects() {
    Workspace workspace = new Workspace("Studio", "Creator");
    Brand brand = new Brand("Brand", workspace);
    workspace.archive();
    when(brands.findByIdAndWorkspaceIdIn(target, permitted)).thenReturn(Optional.of(brand));
    when(workspaces.findByIdAndIdIn(target, permitted)).thenReturn(Optional.of(workspace));
    when(workspaces.findLockedByIdAndIdIn(target, permitted)).thenReturn(Optional.of(workspace));
    when(brands.findWorkspaceIdIn(target, permitted)).thenReturn(Optional.of(target));
    when(brands.findLockedWithWorkspaceIn(target, permitted)).thenReturn(Optional.of(brand));
    var management = new BrandManagementUseCase(brands, workspaces);
    assertThrows(
        IllegalStateException.class,
        () -> management.create(target, permitted, "Brand", null, null, null));
    assertThrows(
        IllegalStateException.class,
        () -> management.update(target, permitted, 0, "Changed", null, null, null));
    assertThrows(
        IllegalStateException.class,
        () ->
            new AnalyzeBrandUseCase(
                    brands,
                    ingest,
                    mock(com.cadence.application.services.BrandAnalysisTransactions.class),
                    mock(com.cadence.application.services.GenerationBudgets.class))
                .execute(target, permitted));
    assertThrows(
        IllegalStateException.class,
        () -> registration().register(target, permitted, EvidenceType.TEXT, "Text"));
    assertThrows(
        IllegalStateException.class,
        () ->
            registration()
                .registerFile(target, permitted, "file.pdf", "application/pdf", new byte[1]));
    assertEquals("Brand", brand.getName());
    verifyNoInteractions(evidence, ingest, jobs, storage, scanner);
  }

  @Test
  void archivedBrandRejectsAnalysis() {
    Brand brand = new Brand("Brand", new Workspace("Studio", "Creator"));
    brand.archive();
    when(brands.findByIdAndWorkspaceIdIn(target, permitted)).thenReturn(Optional.of(brand));
    assertThrows(
        IllegalStateException.class,
        () ->
            new AnalyzeBrandUseCase(
                    brands,
                    ingest,
                    mock(com.cadence.application.services.BrandAnalysisTransactions.class),
                    mock(com.cadence.application.services.GenerationBudgets.class))
                .execute(target, permitted));
    verifyNoInteractions(ingest);
  }
}
