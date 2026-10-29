package com.cadence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.services.*;
import com.cadence.application.usecases.AnalyzeBrandUseCase;
import com.cadence.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnalyzeBrandUseCaseTest {
  private final Set<UUID> permitted = Set.of(UUID.randomUUID());
  private final UUID id = UUID.randomUUID();
  private final BrandRepository brands = mock(BrandRepository.class);
  private final BrandIngestService ingest = mock(BrandIngestService.class);
  private final BrandAnalysisTransactions transactions = mock(BrandAnalysisTransactions.class);
  private final AnalyzeBrandUseCase useCase =
      new AnalyzeBrandUseCase(
          brands,
          ingest,
          transactions,
          mock(com.cadence.application.services.GenerationBudgets.class));
  private final BrandAnalysisTransactions.Snapshot snapshot =
      new BrandAnalysisTransactions.Snapshot(
          id, new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator"));

  private void prepare() {
    when(brands.findByIdAndWorkspaceIdIn(id, permitted))
        .thenReturn(Optional.of(new Brand("Brand", new Workspace("Studio", "Creator"))));
    when(transactions.start(id, permitted)).thenReturn(snapshot);
  }

  @Test
  void publishesOnlyTheTransactionalCompletion() {
    prepare();
    var result =
        new BrandAnalysisResult(
            new BrandAnalysisResult.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
            List.of("Inter"),
            List.of("clear"),
            List.of("spam"),
            "Caption",
            List.of(),
            "provider",
            "model-v1",
            "prompt-v1");
    when(ingest.analyze(snapshot.command())).thenReturn(result);
    var signature = result.toSignature();
    when(transactions.complete(snapshot, permitted, result))
        .thenReturn(new BrandAnalysisTransactions.Completion(true, signature));
    assertSame(signature, useCase.execute(id, permitted));
    var order = inOrder(transactions, ingest);
    order.verify(transactions).start(id, permitted);
    order.verify(ingest).analyze(snapshot.command());
    order.verify(transactions).complete(snapshot, permitted, result);
    verify(brands, never()).save(any());
  }

  @Test
  void refusesUnknownBrandBeforeAdapterOrLedger() {
    when(brands.findByIdAndWorkspaceIdIn(id, permitted)).thenReturn(Optional.empty());
    assertThrows(ResourceNotFoundException.class, () -> useCase.execute(id, permitted));
    verifyNoInteractions(ingest, transactions);
  }

  @Test
  void recordsFailureAndDoesNotPublish() {
    prepare();
    when(ingest.analyze(any())).thenThrow(new BrandAnalysisUnavailableException());
    assertThrows(BrandAnalysisUnavailableException.class, () -> useCase.execute(id, permitted));
    verify(transactions).fail(snapshot, permitted);
    verify(transactions, never()).complete(any(), any(), any());
  }

  @Test
  void rejectsSupersededResultAfterTheReceiptTransactionCommits() {
    prepare();
    when(transactions.complete(eq(snapshot), eq(permitted), any()))
        .thenReturn(new BrandAnalysisTransactions.Completion(false, null));
    assertThrows(IllegalStateException.class, () -> useCase.execute(id, permitted));
    verify(brands, never()).save(any());
  }
}
