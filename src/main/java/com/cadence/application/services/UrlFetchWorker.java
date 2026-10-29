package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UrlFetchWorker {
  private static final Duration LEASE = Duration.ofMinutes(2);
  private static final RetryPolicy RETRY =
      new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1));
  private final DurableJobRepository jobs;
  private final EvidenceOwnershipLocks ownership;
  private final UrlEvidenceFetcher fetcher;
  private final EvidenceObjectStorage storage;
  private final WebsiteMetadataExtractor extractor;
  private final DurableJobQueue queue;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final QuarantineLifecycle lifecycle;
  private final UrlTextSnapshotRepository snapshots;
  private final BrandEvidenceRepository evidence;

  public UrlFetchWorker(
      DurableJobRepository jobs,
      EvidenceOwnershipLocks ownership,
      UrlEvidenceFetcher fetcher,
      EvidenceObjectStorage storage,
      WebsiteMetadataExtractor extractor,
      DurableJobQueue queue,
      PlatformTransactionManager manager,
      Clock clock,
      QuarantineLifecycle lifecycle,
      UrlTextSnapshotRepository snapshots,
      BrandEvidenceRepository evidence) {
    this.jobs = jobs;
    this.ownership = ownership;
    this.fetcher = fetcher;
    this.storage = storage;
    this.extractor = extractor;
    this.queue = queue;
    this.transactions = new TransactionTemplate(manager);
    this.transactions.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.clock = clock;
    this.lifecycle = lifecycle;
    this.snapshots = snapshots;
    this.evidence = evidence;
  }

  private record Attempt(
      UUID jobId,
      UUID evidenceId,
      UUID workspaceId,
      UUID brandId,
      String owner,
      int number,
      String source) {}

  /** One bounded work item; network and storage never hold a database transaction open. */
  @Transactional(propagation = Propagation.NEVER)
  public boolean runOnce() {
    Attempt attempt = transactions.execute(status -> claim());
    if (attempt == null) return false;
    UrlEvidenceFetcher.FetchedUrl result;
    String reference;
    String checksum;
    WebsiteMetadata metadata;
    QuarantineLifecycle.Reservation reservation;
    try {
      reservation =
          lifecycle.reserve(attempt.workspaceId(), attempt.brandId(), 2 * 1024 * 1024, "html");
      result = fetcher.fetch(attempt.source());
      metadata = extractor.extract(result.body(), result.mediaType());
      checksum = checksum(result.body());
      String objectKey = reservation.objectKey();
      lifecycle.requireWritable(reservation.id());
      reference = storage.storeQuarantined(objectKey, result.body());
      if (!("quarantine:" + objectKey).equals(reference))
        throw new IllegalStateException("Evidence storage returned a mismatched reference");
    } catch (WebsiteExtractionFailure failure) {
      finishFailure(attempt, "Website metadata could not be extracted", true);
      return true;
    } catch (UrlFetchFailure failure) {
      finishFailure(attempt, failure.getMessage(), !failure.retryable());
      return true;
    } catch (RuntimeException failure) {
      finishFailure(attempt, "Evidence fetch or storage unavailable", false);
      return true;
    }
    transactions.executeWithoutResult(
        status -> {
          DurableJob job = jobs.findLockedById(attempt.jobId()).orElseThrow();
          BrandEvidence item = ownership.findLocked(attempt.evidenceId()).orElseThrow();
          Instant now = clock.instant();
          if (!owns(job, attempt, now)) return;
          if (item.getStatus() == EvidenceStatus.FAILED) {
            job.fail(attempt.owner(), attempt.number(), now, "Source is unavailable", RETRY, true);
            return;
          }
          if (item.getStatus() == EvidenceStatus.PENDING) {
            if (!active(item)) {
              fail(job, item, attempt, now, "Source is no longer active", true);
              return;
            }
            item.recordFetched(reference, checksum, result.mediaType(), now);
            item.recordWebsiteMetadata(metadata, now);
            lifecycle.reference(reservation.id(), item.getId(), reference, result.body().length);
            evidence.flush();
            snapshots.saveAndFlush(new UrlTextSnapshot(item, job, now));
            queue.enqueue(
                "EVIDENCE_MALWARE_SCAN",
                "evidence:" + item.getId(),
                "evidence-scan:" + item.getId() + ":" + checksum,
                3,
                now);
          }
          job.succeed(attempt.owner(), attempt.number(), now);
        });
    return true;
  }

  private Attempt claim() {
    Instant now = clock.instant();
    var candidates = jobs.findUrlFetchCandidates(now, PageRequest.of(0, 1));
    if (candidates.isEmpty()) return null;
    DurableJob job = candidates.getFirst();
    UUID id = UUID.fromString(job.getPayloadReference().substring("evidence:".length()));
    BrandEvidence item = ownership.findLocked(id).orElseThrow();
    now = clock.instant();
    if (job.getStatus() == JobStatus.RUNNING) {
      if (job.getLeaseExpiresAt().isAfter(now)) return null;
      job.recoverExpiredLease(now);
    }
    if (job.getStatus() == JobStatus.DEAD) {
      if (item.getStatus() == EvidenceStatus.PENDING) item.failUrlFetch();
      return null;
    }
    if ((job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.FAILED)
        || job.getNextAttemptAt().isAfter(now)) return null;
    String owner = UUID.randomUUID().toString();
    job.claim(owner, now, LEASE);
    if (item.getStatus() == EvidenceStatus.FETCHED || item.getStatus() == EvidenceStatus.READY) {
      job.succeed(owner, job.getAttempts(), now);
      return null;
    }
    if (item.getStatus() == EvidenceStatus.FAILED || !active(item)) {
      job.fail(owner, job.getAttempts(), now, "Source is unavailable", RETRY, true);
      if (item.getStatus() == EvidenceStatus.PENDING) item.failUrlFetch();
      return null;
    }
    return new Attempt(
        job.getId(),
        id,
        item.getBrand().getWorkspace().getId(),
        item.getBrand().getId(),
        owner,
        job.getAttempts(),
        item.getSourceReference());
  }

  private void finishFailure(Attempt attempt, String safeError, boolean permanent) {
    transactions.executeWithoutResult(
        status -> {
          DurableJob job = jobs.findLockedById(attempt.jobId()).orElseThrow();
          BrandEvidence item = ownership.findLocked(attempt.evidenceId()).orElseThrow();
          Instant now = clock.instant();
          if (!owns(job, attempt, now)) return;
          fail(job, item, attempt, now, safeError, permanent);
        });
  }

  private void fail(
      DurableJob job,
      BrandEvidence item,
      Attempt attempt,
      Instant now,
      String error,
      boolean permanent) {
    job.fail(attempt.owner(), attempt.number(), now, error, RETRY, permanent);
    if (job.getStatus() == JobStatus.DEAD && item.getStatus() == EvidenceStatus.PENDING)
      item.failUrlFetch();
  }

  private boolean owns(DurableJob job, Attempt attempt, Instant now) {
    return job.getStatus() == JobStatus.RUNNING
        && attempt.owner().equals(job.getLeaseOwner())
        && job.getAttempts() == attempt.number()
        && job.getLeaseExpiresAt().isAfter(now);
  }

  private boolean active(BrandEvidence item) {
    return item.getBrand().getStatus() == BrandStatus.ACTIVE
        && item.getBrand().getWorkspace().getStatus() == WorkspaceStatus.ACTIVE;
  }

  private String checksum(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
