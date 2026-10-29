package com.cadence.application.services;

import com.cadence.application.ports.EvidenceObjectLifecycleStorage;
import com.cadence.application.repositories.QuarantineObjectRepository;
import com.cadence.domain.QuarantineObject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QuarantineCleanupWorker {
  private static final Logger LOG = LoggerFactory.getLogger(QuarantineCleanupWorker.class);

  private record Claim(UUID id, String reference, long attempt) {}

  private final QuarantineObjectRepository objects;
  private final ObjectProvider<EvidenceObjectLifecycleStorage> storage;
  private final QuarantineLifecyclePolicy policy;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public QuarantineCleanupWorker(
      QuarantineObjectRepository objects,
      ObjectProvider<EvidenceObjectLifecycleStorage> storage,
      QuarantineLifecyclePolicy policy,
      Clock clock,
      PlatformTransactionManager manager) {
    this.objects = objects;
    this.storage = storage;
    this.policy = policy;
    this.clock = clock;
    transactions = new TransactionTemplate(manager);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** One bounded batch. No provider I/O is performed inside a database transaction. */
  @Transactional(propagation = Propagation.NEVER)
  public int runOnce() {
    EvidenceObjectLifecycleStorage adapter = storage.getIfAvailable();
    if (adapter == null) return 0;
    var candidates = objects.cleanupCandidates(now(), PageRequest.of(0, policy.cleanupBatch()));
    int claimed = 0;
    for (UUID id : candidates) {
      Claim claim = transactions.execute(status -> claim(id));
      if (claim == null) continue;
      claimed++;
      try {
        adapter.deleteQuarantined(claim.reference());
      } catch (RuntimeException failure) {
        // The provider's raw message may contain credentials, bucket names or private endpoints.
        LOG.warn("Quarantine cleanup deferred for reservation {}", claim.id());
        continue;
      }
      transactions.executeWithoutResult(status -> confirm(claim));
    }
    return claimed;
  }

  private Claim claim(UUID id) {
    QuarantineObject object = objects.findLockedById(id).orElseThrow();
    Instant now = now();
    if (object.getState() == QuarantineObject.State.REFERENCED
        || now.isBefore(object.getNextCleanupAt())) return null;
    if (referenced(object)) return null;
    object.beginDeletion(now, policy.writeDeadline());
    return new Claim(object.getId(), object.reference(), object.getCleanupAttempts());
  }

  private void confirm(Claim claim) {
    QuarantineObject object = objects.findLockedById(claim.id()).orElseThrow();
    if (object.getCleanupAttempts() != claim.attempt() || referenced(object)) return;
    object.deletionConfirmed(now(), policy.deletionDrain(), policy.tombstoneSweep());
  }

  private boolean referenced(QuarantineObject object) {
    return objects.evidenceReferences(object.reference()) != 0
        || objects.artifactReferences(object.reference()) != 0
        || objects.registeredIntents(object.getObjectKey()) != 0
        || objects.activeJobs(object.reference(), object.getObjectKey()) != 0;
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
