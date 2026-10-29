package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.BrandAnalysisCommand;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AnalyzeEvidenceCapacityTest {
  @Test
  void twoBlockedModelsRejectThirdWithoutCreatingAttemptAndReleaseSlotsAfterFailure()
      throws Exception {
    var tx = mock(BrandAnalysisTransactions.class);
    var ingest = mock(BrandIngestService.class);
    var reviews = mock(ReviewPdfEvidenceUseCase.class);
    var useCase =
        new AnalyzeEvidenceUseCase(
            tx,
            ingest,
            reviews,
            mock(ReviewUrlEvidenceUseCase.class),
            Clock.systemUTC(),
            mock(GenerationBudgets.class));
    var actor = UUID.randomUUID();
    var brand = UUID.randomUUID();
    var scope = Set.of(UUID.randomUUID());
    var snapshot =
        new BrandAnalysisTransactions.Snapshot(
            brand, new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator"), actor);
    when(tx.startEvidence(brand, scope, actor)).thenReturn(snapshot);
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(ingest.analyze(any()))
        .thenAnswer(
            i -> {
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              throw new BrandAnalysisUnavailableException();
            });
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first = pool.submit(() -> useCase.execute(brand, scope, actor));
      var second = pool.submit(() -> useCase.execute(brand, scope, actor));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> useCase.execute(brand, scope, actor))
            .isInstanceOf(BrandAnalysisUnavailableException.class);
        verify(tx, times(2)).startEvidence(brand, scope, actor);
      } finally {
        release.countDown();
      }
      for (var f : List.of(first, second))
        assertThatThrownBy(() -> f.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(BrandAnalysisUnavailableException.class);
    }
    verify(tx, times(2)).fail(snapshot, scope);
    when(tx.startEvidence(brand, scope, actor)).thenThrow(new IllegalStateException("stale"));
    assertThatThrownBy(() -> useCase.execute(brand, scope, actor))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("stale");
    verify(tx, times(3)).startEvidence(brand, scope, actor);
    verifyNoInteractions(reviews);
  }
}
