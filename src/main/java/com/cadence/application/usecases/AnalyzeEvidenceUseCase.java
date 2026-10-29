package com.cadence.application.usecases;

import com.cadence.application.ports.BrandAnalysisResult;
import com.cadence.application.services.*;
import com.cadence.domain.BrandSignature;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
public class AnalyzeEvidenceUseCase {
  private final com.cadence.application.services.GenerationBudgets budgets;

  public record Outcome(UUID analysisId, BrandSignature signature, BrandAnalysisResult result) {}

  private final BrandAnalysisTransactions transactions;
  private final BrandIngestService ingest;
  private final ReviewPdfEvidenceUseCase pdfReviews;
  private final ReviewUrlEvidenceUseCase urlReviews;
  private final java.time.Clock clock;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public AnalyzeEvidenceUseCase(
      BrandAnalysisTransactions transactions,
      BrandIngestService ingest,
      ReviewPdfEvidenceUseCase pdfReviews,
      ReviewUrlEvidenceUseCase urlReviews,
      java.time.Clock clock,
      GenerationBudgets budgets) {
    this.budgets = budgets;
    this.transactions = transactions;
    this.ingest = ingest;
    this.pdfReviews = pdfReviews;
    this.urlReviews = urlReviews;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actorId)")
  public Outcome execute(UUID brandId, Set<UUID> scope, UUID actorId) {
    if (!slots.tryAcquire())
      throw new com.cadence.application.exceptions.BrandAnalysisUnavailableException();
    try {
      var snapshot = transactions.startEvidence(brandId, scope, actorId);
      BrandAnalysisResult result;
      BrandAnalysisTransactions.Completion completion;
      try {
        verifyPhysicalBatch(snapshot, scope, actorId);
        transactions.preflight(snapshot, scope);
        budgets.dispatchSync(
            snapshot.command().analysisId(),
            brandId,
            scope,
            "EVIDENCE",
            snapshot.command().sourceChecksum(),
            clock.instant().plusSeconds(60));
        transactions.preflight(snapshot, scope);
        result = ingest.analyze(snapshot.command());
        budgets.syntheticCompleted(snapshot.command().analysisId());
        if (!snapshot.pdfAdmissions().isEmpty()
            || snapshot.command().sources().stream()
                .anyMatch(com.cadence.application.ports.AnalysisTextSource::structured))
          transactions.preflight(snapshot, scope);
        verifyPhysicalBatch(snapshot, scope, actorId);
        completion = transactions.complete(snapshot, scope, result);
      } catch (RuntimeException failure) {
        transactions.fail(snapshot, scope);
        throw failure;
      }
      if (!completion.published())
        throw new IllegalStateException("Evidence analysis was superseded");
      return new Outcome(snapshot.command().analysisId(), completion.signature(), result);
    } finally {
      slots.release();
    }
  }

  private void verifyPhysicalBatch(
      BrandAnalysisTransactions.Snapshot snapshot, Set<UUID> scope, UUID actorId) {

    var deadline = clock.instant().plusSeconds(30);
    for (var selected : snapshot.command().sources()) {
      if (selected.structured() && !selected.social()) {
        var verified =
            urlReviews.snapshot(selected.evidenceId(), selected.admissionId(), scope, actorId);
        var a = verified.admission();
        if (!a.id().equals(selected.admissionId())
            || !a.evidenceId().equals(selected.evidenceId())
            || a.sourceVersion() != selected.sourceVersion()
            || a.brandVersion() != snapshot.command().brandVersion()
            || a.workspaceVersion() != snapshot.command().workspaceVersion()
            || !a.contentChecksum().equals(selected.contentChecksum())
            || !verified.text().equals(selected.text())
            || !clock.instant().isBefore(deadline))
          throw new IllegalStateException("URL physical batch changed");
        continue;
      }
      if (!snapshot.pdfAdmissions().contains(selected.admissionId())) continue;
      var verified =
          pdfReviews.snapshot(selected.evidenceId(), selected.admissionId(), scope, actorId);
      var a = verified.admission();
      if (!a.id().equals(selected.admissionId())
          || !a.evidenceId().equals(selected.evidenceId())
          || a.sourceVersion() != selected.sourceVersion()
          || a.brandVersion() != snapshot.command().brandVersion()
          || a.workspaceVersion() != snapshot.command().workspaceVersion()
          || !a.contentChecksum().equals(selected.contentChecksum())
          || !verified.text().equals(selected.text())
          || !clock.instant().isBefore(deadline))
        throw new IllegalStateException("PDF analysis physical batch changed");
    }
  }
}
