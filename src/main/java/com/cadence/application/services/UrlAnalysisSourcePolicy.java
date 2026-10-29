package com.cadence.application.services;

import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class UrlAnalysisSourcePolicy {
  private final UrlTextSnapshotRepository snapshots;
  private final EvidenceScanResultRepository scans;
  private final QuarantineObjectRepository objects;
  private final DurableJobRepository jobs;
  private final Clock clock;
  private final Duration maxAge;

  public UrlAnalysisSourcePolicy(
      UrlTextSnapshotRepository snapshots,
      EvidenceScanResultRepository scans,
      QuarantineObjectRepository objects,
      DurableJobRepository jobs,
      Clock clock,
      @Value("${cadence.evidence.artifacts.max-scan-age:PT24H}") Duration maxAge) {
    if (maxAge == null
        || maxAge.compareTo(Duration.ofSeconds(1)) < 0
        || maxAge.compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("Invalid URL scan age");
    this.snapshots = snapshots;
    this.scans = scans;
    this.objects = objects;
    this.jobs = jobs;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  public EvidencePhysicalSource capture(
      EvidenceAdmission a, BrandEvidence e, Brand brand, long receiptBrandVersion) {
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive())
      throw new IllegalStateException("URL validation requires transaction");
    UrlTextSnapshot.requireSource(e);
    var snapshot =
        snapshots
            .findByEvidenceIdAndSourceVersion(e.getId(), e.getLockVersion())
            .orElseThrow(() -> new IllegalStateException("URL snapshot unavailable"));
    var job =
        jobs.findById(snapshot.getFetchJobId())
            .orElseThrow(() -> new IllegalStateException("URL fetch unavailable"));
    if (job.getStatus() != JobStatus.SUCCEEDED || job.getAttempts() != snapshot.getFetchAttempt())
      throw new IllegalStateException("URL fetch changed");
    var latest =
        scans.latestForEvidence(e.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
    String ref = e.getFetchedReference();
    if (latest.isEmpty() || ref == null || !ref.startsWith("quarantine:"))
      throw new IllegalStateException("URL source unavailable");
    var q =
        objects
            .findLockedForEvidence(
                ref.substring(11), e.getId(), brand.getId(), brand.getWorkspace().getId())
            .orElseThrow(() -> new IllegalStateException("URL storage unavailable"));
    var c = UrlReviewContent.from(e, snapshot, latest.getFirst(), q, clock.instant(), maxAge);
    a.requireCurrentUrl(
        c,
        e.getLockVersion(),
        receiptBrandVersion,
        brand.getWorkspace().getLockVersion(),
        clock.instant());
    return new EvidencePhysicalSource(a.getId(), q.getId(), q.getLockVersion(), ref, null, c);
  }
}
