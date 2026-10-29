package com.cadence.application.usecases;

import com.cadence.application.exceptions.DocumentReviewUnavailableException;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.results.PdfAdmissionDetails;
import com.cadence.application.results.PdfAdmissionSnapshot;
import com.cadence.application.results.PdfReviewPreview;
import com.cadence.application.services.PdfReviewTransactions;
import com.cadence.domain.PdfReviewToken;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class ReviewPdfEvidenceUseCase {
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);
  private final PdfReviewTransactions transactions;
  private final EvidenceObjectStorage storage;

  public ReviewPdfEvidenceUseCase(
      PdfReviewTransactions transactions, EvidenceObjectStorage storage) {
    this.transactions = transactions;
    this.storage = storage;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public PdfReviewPreview execute(UUID id, Set<UUID> scope, UUID actorId) {
    return verified(
        () -> transactions.capture(id, scope, actorId), s -> transactions.finish(s, scope));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public PdfAdmissionDetails admit(UUID id, Set<UUID> scope, UUID actorId, PdfReviewToken token) {
    return verified(
        () -> transactions.captureAdmission(id, scope, actorId, token),
        s -> transactions.admit(s, scope, token));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public PdfAdmissionSnapshot snapshot(UUID id, UUID admissionId, Set<UUID> scope, UUID actorId) {
    return verified(
        () -> transactions.captureSnapshot(id, admissionId, scope, actorId),
        s -> transactions.snapshot(s, admissionId, scope));
  }

  private <T> T verified(
      java.util.function.Supplier<PdfReviewTransactions.Snapshot> capture,
      java.util.function.Function<PdfReviewTransactions.Snapshot, T> finish) {
    if (!slots.tryAcquire()) throw new DocumentReviewUnavailableException();
    try {
      var snapshot = capture.get();
      try {
        var c = snapshot.input().content();
        c.verifyBytes(
            storage.readQuarantined(
                snapshot.input().reference(), Math.toIntExact(c.sourceBytes())));
      } catch (RuntimeException failure) {
        throw new DocumentReviewUnavailableException();
      }
      return finish.apply(snapshot);
    } finally {
      slots.release();
    }
  }
}
