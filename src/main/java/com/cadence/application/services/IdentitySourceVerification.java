package com.cadence.application.services;

import com.cadence.application.exceptions.DocumentReviewUnavailableException;
import com.cadence.application.ports.EvidenceObjectStorage;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Internal orchestration. Capture and commit callbacks must authorize and lock their resources. */
@Service
public class IdentitySourceVerification {
  private final TransactionTemplate tx;
  private final EvidenceObjectStorage storage;
  private final Clock clock;
  private final com.cadence.application.ports.WebsiteMetadataExtractor extractor;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public IdentitySourceVerification(
      PlatformTransactionManager manager,
      EvidenceObjectStorage storage,
      Clock clock,
      com.cadence.application.ports.WebsiteMetadataExtractor extractor) {
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.storage = storage;
    this.clock = clock;
    this.extractor = extractor;
  }

  public <T> T execute(Supplier<List<EvidencePhysicalSource>> capture, Supplier<T> commit) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Identity verification requires nontransactional caller");
    if (!slots.tryAcquire()) throw new DocumentReviewUnavailableException();
    try {
      var deadline = clock.instant().plusSeconds(30);
      var sources = Objects.requireNonNull(tx.execute(s -> capture.get()));
      for (var source : sources) {
        if (!clock.instant().isBefore(deadline)) throw new DocumentReviewUnavailableException();
        try {
          source.verify(storage, extractor);
        } catch (RuntimeException failure) {
          throw new DocumentReviewUnavailableException();
        }
      }
      return tx.execute(
          s -> {
            var fresh = capture.get();
            if (!fresh.equals(sources) || !clock.instant().isBefore(deadline))
              throw new IllegalStateException("Identity source changed during verification");
            return commit.get();
          });
    } finally {
      slots.release();
    }
  }
}
