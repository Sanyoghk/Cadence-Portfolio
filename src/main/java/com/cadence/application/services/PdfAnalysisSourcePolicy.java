package com.cadence.application.services;

import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Internal metadata guard; caller owns Account/parent/Evidence locks. No external I/O here. */
@Service
public class PdfAnalysisSourcePolicy {
  private final EvidenceArtifactRepository artifacts;
  private final EvidenceScanResultRepository scans;
  private final QuarantineObjectRepository objects;
  private final Clock clock;
  private final Duration maxAge;

  public PdfAnalysisSourcePolicy(
      EvidenceArtifactRepository artifacts,
      EvidenceScanResultRepository scans,
      QuarantineObjectRepository objects,
      Clock clock,
      @Value("${cadence.evidence.artifacts.max-scan-age:PT24H}") Duration maxAge) {
    if (maxAge == null
        || maxAge.compareTo(Duration.ofSeconds(1)) < 0
        || maxAge.compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("Invalid PDF scan age");
    this.artifacts = artifacts;
    this.scans = scans;
    this.objects = objects;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  public void validate(EvidenceAdmission a, BrandEvidence e, Brand brand) {
    capture(a, e, brand, brand.getLockVersion());
  }

  public EvidencePhysicalSource capture(
      EvidenceAdmission a, BrandEvidence e, Brand brand, long receiptBrandVersion) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("PDF source validation requires transaction");
    var artifact =
        artifacts
            .findByEvidenceIdAndSourceChecksumAndSourceVersionAndKind(
                e.getId(), e.getChecksum(), e.getLockVersion(), EvidenceArtifact.Kind.PDF_TEXT)
            .orElseThrow(() -> new IllegalStateException("PDF analysis artifact unavailable"));
    var latest =
        scans.latestForEvidence(e.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
    String ref = e.getSourceReference();
    if (latest.isEmpty() || ref == null || !ref.startsWith("quarantine:"))
      throw new IllegalStateException("PDF analysis source unavailable");
    var q =
        objects
            .findLockedForEvidence(
                ref.substring(11), e.getId(), brand.getId(), brand.getWorkspace().getId())
            .orElseThrow(() -> new IllegalStateException("PDF analysis storage unavailable"));
    var c = PdfReviewContent.from(e, artifact, latest.getFirst(), q, clock.instant(), maxAge);
    a.requireCurrentPdf(
        c,
        e.getLockVersion(),
        receiptBrandVersion,
        brand.getWorkspace().getLockVersion(),
        clock.instant());
    return new EvidencePhysicalSource(a.getId(), q.getId(), q.getLockVersion(), ref, c, null);
  }
}
