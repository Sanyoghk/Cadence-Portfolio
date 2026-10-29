package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.services.BrandAnalysisTransactions;
import com.cadence.application.services.BrandIngestService;
import com.cadence.domain.Brand;
import com.cadence.domain.BrandSignature;
import com.cadence.domain.BrandStatus;
import com.cadence.domain.WorkspaceStatus;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("denyAll()")
public class AnalyzeBrandUseCase {
  private final com.cadence.application.services.GenerationBudgets budgets;

  private final BrandRepository brandRepository;
  private final BrandIngestService brandIngestService;
  private final BrandAnalysisTransactions transactions;

  public AnalyzeBrandUseCase(
      BrandRepository brandRepository,
      BrandIngestService brandIngestService,
      BrandAnalysisTransactions transactions,
      com.cadence.application.services.GenerationBudgets budgets) {
    this.budgets = budgets;
    this.brandRepository = brandRepository;
    this.brandIngestService = brandIngestService;
    this.transactions = transactions;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #permittedWorkspaceIds)")
  public BrandSignature execute(UUID brandId, Set<UUID> permittedWorkspaceIds) {
    Brand brand =
        brandRepository
            .findByIdAndWorkspaceIdIn(brandId, permittedWorkspaceIds)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found: " + brandId));

    if (brand.getStatus() == BrandStatus.ARCHIVED
        || brand.getWorkspace().getStatus() == WorkspaceStatus.ARCHIVED) {
      throw new IllegalStateException("Cannot analyze an archived brand or workspace");
    }
    var snapshot = transactions.start(brandId, permittedWorkspaceIds);
    com.cadence.application.ports.BrandAnalysisResult result;
    try {
      budgets.dispatchSync(
          snapshot.command().analysisId(),
          brandId,
          permittedWorkspaceIds,
          "DECLARED",
          snapshot.command().sourceChecksum(),
          java.time.Instant.now().plusSeconds(60));
      result = brandIngestService.analyze(snapshot.command());
      budgets.syntheticCompleted(snapshot.command().analysisId());
    } catch (RuntimeException failure) {
      transactions.fail(snapshot, permittedWorkspaceIds);
      throw failure;
    }
    BrandAnalysisTransactions.Completion completion;
    try {
      completion = transactions.complete(snapshot, permittedWorkspaceIds, result);
    } catch (RuntimeException failure) {
      // Publication and its receipt rolled back together; release the RUNNING attempt separately.
      transactions.fail(snapshot, permittedWorkspaceIds);
      throw failure;
    }
    if (!completion.published()) throw new IllegalStateException("Brand analysis was superseded");
    return completion.signature();
  }
}
