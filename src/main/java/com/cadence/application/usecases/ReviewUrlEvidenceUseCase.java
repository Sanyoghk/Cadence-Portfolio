package com.cadence.application.usecases;

import com.cadence.application.exceptions.DocumentReviewUnavailableException;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.results.UrlAdmissionDetails;
import com.cadence.application.results.UrlAdmissionSnapshot;
import com.cadence.application.results.UrlReviewPreview;
import com.cadence.application.services.UrlReviewTransactions;
import com.cadence.domain.UrlReviewToken;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class ReviewUrlEvidenceUseCase {
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);
  private final UrlReviewTransactions transactions;
  private final EvidenceObjectStorage storage;
  private final com.cadence.application.ports.WebsiteMetadataExtractor extractor;

  public ReviewUrlEvidenceUseCase(
      UrlReviewTransactions transactions,
      EvidenceObjectStorage storage,
      com.cadence.application.ports.WebsiteMetadataExtractor extractor) {
    this.transactions = transactions;
    this.storage = storage;
    this.extractor = extractor;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public UrlReviewPreview execute(UUID id, Set<UUID> scope, UUID actorId) {
    return verified(
        () -> transactions.capture(id, scope, actorId), s -> transactions.finish(s, scope));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public UrlAdmissionDetails admit(UUID id, Set<UUID> scope, UUID actorId, UrlReviewToken token) {
    return verified(
        () -> transactions.captureAdmission(id, scope, actorId, token),
        s -> transactions.admit(s, scope, token));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public UrlAdmissionSnapshot snapshot(UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    return verified(
        () -> transactions.captureSnapshot(id, admissionId, scope, actorId),
        s -> transactions.snapshot(s, admissionId, scope));
  }

  private <T> T verified(
      java.util.function.Supplier<UrlReviewTransactions.Snapshot> capture,
      java.util.function.Function<UrlReviewTransactions.Snapshot, T> finish) {
    if (!slots.tryAcquire()) throw new DocumentReviewUnavailableException();
    try {
      var snapshot = capture.get();
      try {
        var c = snapshot.input().content();
        var bytes =
            storage.readQuarantined(snapshot.input().reference(), Math.toIntExact(c.sourceBytes()));
        c.verifyBytes(bytes);
        if (!c.metadata().equals(extractor.extract(bytes, c.mediaType())))
          throw new IllegalStateException("URL extraction changed");
      } catch (RuntimeException failure) {
        throw new DocumentReviewUnavailableException();
      }
      return finish.apply(snapshot);
    } finally {
      slots.release();
    }
  }
}
