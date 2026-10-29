package com.cadence.application.usecases;

import com.cadence.application.exceptions.StrategyGenerationUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.application.results.BuiltStrategy;
import com.cadence.application.services.StrategyBuildTransactions;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class BuildStrategyUseCase {
  private final com.cadence.application.services.GenerationBudgets budgets;
  private final StrategyBuildTransactions transactions;
  private final StrategyGenerationService generator;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public BuildStrategyUseCase(
      StrategyBuildTransactions transactions,
      StrategyGenerationService generator,
      com.cadence.application.services.GenerationBudgets budgets) {
    this.budgets = budgets;
    this.transactions = transactions;
    this.generator = generator;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public BuiltStrategy execute(UUID brandId, Set<UUID> scope) {
    if (!slots.tryAcquire()) throw new StrategyGenerationUnavailableException();
    try {
      var snapshot = transactions.snapshot(brandId, scope);
      transactions.preflight(snapshot, scope);
      try {
        budgets.dispatchSync(
            snapshot.command().generationId(),
            brandId,
            scope,
            "STRATEGY",
            com.cadence.domain.BrandAnalysisAttempt.checksum(
                snapshot.command().toString() + "\n" + snapshot.source().toString()),
            snapshot.command().deadline());
      } catch (com.cadence.application.exceptions.BrandAnalysisUnavailableException unavailable) {
        throw new StrategyGenerationUnavailableException();
      }
      transactions.preflight(snapshot, scope);
      StrategyGenerationResult result;
      try {
        result = generator.generate(snapshot.command());
        if (result == null) throw new StrategyGenerationUnavailableException();
        result.requireCompletePlaybook();
        budgets.syntheticCompleted(snapshot.command().generationId());
      } catch (RuntimeException failure) {
        throw new StrategyGenerationUnavailableException();
      }
      return transactions.complete(snapshot, scope, result);
    } finally {
      slots.release();
    }
  }
}
