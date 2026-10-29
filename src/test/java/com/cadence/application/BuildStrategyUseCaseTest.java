package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.results.BuiltStrategy;
import com.cadence.application.services.StrategyBuildTransactions;
import com.cadence.application.usecases.BuildStrategyUseCase;
import com.cadence.domain.ApprovalStatus;
import com.cadence.support.StrategyFixtures;
import java.util.*;
import org.junit.jupiter.api.Test;

class BuildStrategyUseCaseTest {
  private final StrategyBuildTransactions transactions = mock(StrategyBuildTransactions.class);
  private final StrategyGenerationService generator = mock(StrategyGenerationService.class);
  private final com.cadence.application.services.GenerationBudgets budgets =
      mock(com.cadence.application.services.GenerationBudgets.class);
  private final BuildStrategyUseCase useCase =
      new BuildStrategyUseCase(transactions, generator, budgets);
  private final UUID brand = UUID.randomUUID();
  private final Set<UUID> scope = Set.of(UUID.randomUUID());
  private final StrategyBuildTransactions.Snapshot snapshot =
      new StrategyBuildTransactions.Snapshot(
          brand,
          StrategyFixtures.command(),
          new com.cadence.domain.ApprovedIdentitySource(
              UUID.randomUUID(),
              1,
              1,
              StrategyFixtures.identityContent().checksum(),
              UUID.randomUUID(),
              java.time.Instant.now(),
              null,
              UUID.randomUUID()),
          scope);

  @Test
  void publishesOnlyCompletedDraftAfterValidatedPortCall() {
    var output = StrategyFixtures.output();
    var expected = new BuiltStrategy(UUID.randomUUID(), brand, 1, ApprovalStatus.DRAFT, output);
    when(transactions.snapshot(brand, scope)).thenReturn(snapshot);
    when(generator.generate(snapshot.command())).thenReturn(output);
    when(transactions.complete(snapshot, scope, output)).thenReturn(expected);
    assertThat(useCase.execute(brand, scope)).isSameAs(expected);
    var order = inOrder(transactions, generator, budgets);
    order.verify(transactions).snapshot(brand, scope);
    order.verify(transactions).preflight(snapshot, scope);
    order
        .verify(budgets)
        .dispatchSync(
            eq(snapshot.command().generationId()),
            eq(brand),
            eq(scope),
            eq("STRATEGY"),
            anyString(),
            eq(snapshot.command().deadline()));
    order.verify(transactions).preflight(snapshot, scope);
    order.verify(generator).generate(snapshot.command());
    order.verify(budgets).syntheticCompleted(snapshot.command().generationId());
    order.verify(transactions).complete(snapshot, scope, output);
  }

  @Test
  void invalidBrandNeverReachesProvider() {
    when(transactions.snapshot(brand, scope))
        .thenThrow(new IllegalStateException("Missing signature"));
    assertThatThrownBy(() -> useCase.execute(brand, scope))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(generator);
  }

  @Test
  void providerExceptionIsSafeAndDoesNotPersistDraft() {
    when(transactions.snapshot(brand, scope)).thenReturn(snapshot);
    when(generator.generate(any()))
        .thenThrow(new IllegalArgumentException("api_key=private-secret issuer.internal"));
    assertThatThrownBy(() -> useCase.execute(brand, scope))
        .isInstanceOf(StrategyGenerationUnavailableException.class)
        .hasMessage("Strategy generation is unavailable")
        .hasNoCause();
    verify(transactions, never()).complete(any(), any(), any());
  }

  @Test
  void nullOutputDoesNotPersistDraft() {
    when(transactions.snapshot(brand, scope)).thenReturn(snapshot);
    assertThatThrownBy(() -> useCase.execute(brand, scope))
        .isInstanceOf(StrategyGenerationUnavailableException.class);
    verify(transactions, never()).complete(any(), any(), any());
  }
}
