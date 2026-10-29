package com.cadence.application.services;

import com.cadence.application.exceptions.MediaUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.application.results.MediaDetails;
import java.time.Clock;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** All private object I/O outside transactions, exact recapture before returning/publishing. */
@Service
public class MediaVerification {
  private final TransactionTemplate tx;
  private final PrivateMediaStorage storage;
  private final DemoMediaSource source;
  private final Clock clock;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public MediaVerification(
      PlatformTransactionManager manager,
      PrivateMediaStorage storage,
      DemoMediaSource source,
      Clock clock) {
    this.storage = storage;
    this.source = source;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public <T> T execute(Supplier<List<MediaDetails>> capture, Supplier<T> commit) {
    return execute(capture, bytes -> commit.get());
  }

  public <T> T execute(Supplier<List<MediaDetails>> capture, Function<List<byte[]>, T> commit) {
    if (TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Media verification requires nontransactional caller");
    boolean acquired = false;
    try {
      var deadline = clock.instant().plusSeconds(30);
      var captured = Objects.requireNonNull(tx.execute(s -> capture.get()));
      var content = new ArrayList<byte[]>();
      if (!captured.isEmpty()) {
        if (!slots.tryAcquire()) throw new MediaUnavailableException();
        acquired = true;
        source.requireEnabled();
      }
      for (var asset : captured) {
        if (!clock.instant().isBefore(deadline)) throw new MediaUnavailableException();
        try {
          byte[] bytes = storage.readMedia(asset.key(), asset.byteSize()).clone();
          asset.key().verify(bytes);
          asset.fixture().verify(bytes);
          content.add(bytes);
        } catch (RuntimeException failure) {
          throw new MediaUnavailableException();
        }
      }
      return tx.execute(
          s -> {
            var fresh = capture.get();
            if (!fresh.equals(captured) || !clock.instant().isBefore(deadline))
              throw new IllegalStateException("Media or access changed during verification");
            return commit.apply(content);
          });
    } finally {
      if (acquired) slots.release();
    }
  }
}
