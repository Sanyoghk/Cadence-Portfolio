package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable, fenced extraction of already-scanned source bytes through the isolated renderer port.
 */
@Service
public class EvidenceArtifactWorker {
  private static final Duration LEASE = Duration.ofMinutes(2);
  private static final RetryPolicy RETRY =
      new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1));
  private final DurableJobRepository jobs;
  private final EvidenceOwnershipLocks ownership;
  private final EvidenceScanResultRepository scans;
  private final EvidenceArtifactRepository artifacts;
  private final EvidenceObjectStorage storage;
  private final EvidenceRenderer renderer;
  private final WebsiteMetadataExtractor decoder;
  private final QuarantineLifecycle lifecycle;
  private final Clock clock;
  private final Duration maxScanAge;
  private final TransactionTemplate transactions;

  public EvidenceArtifactWorker(
      DurableJobRepository jobs,
      EvidenceOwnershipLocks ownership,
      EvidenceScanResultRepository scans,
      EvidenceArtifactRepository artifacts,
      EvidenceObjectStorage storage,
      EvidenceRenderer renderer,
      WebsiteMetadataExtractor decoder,
      QuarantineLifecycle lifecycle,
      Clock clock,
      PlatformTransactionManager manager,
      @Value("${cadence.evidence.artifacts.max-scan-age:PT24H}") Duration maxScanAge) {
    if (maxScanAge == null
        || maxScanAge.compareTo(Duration.ofSeconds(1)) < 0
        || maxScanAge.compareTo(Duration.ofDays(7)) > 0)
      throw new IllegalArgumentException("Invalid evidence artifact scan-age configuration");
    this.jobs = jobs;
    this.ownership = ownership;
    this.scans = scans;
    this.artifacts = artifacts;
    this.storage = storage;
    this.renderer = renderer;
    this.decoder = decoder;
    this.lifecycle = lifecycle;
    this.clock = clock;
    this.maxScanAge = maxScanAge;
    transactions = new TransactionTemplate(manager);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  private record Attempt(
      UUID jobId,
      UUID evidenceId,
      UUID scanId,
      UUID workspaceId,
      UUID brandId,
      String owner,
      int number,
      long sourceVersion,
      String reference,
      String checksum,
      EvidenceArtifact.Kind kind) {}

  private record Output(
      String reference,
      String text,
      String checksum,
      String version,
      Integer width,
      Integer height,
      QuarantineLifecycle.Reservation reservation,
      long bytes) {}

  @Transactional(propagation = Propagation.NEVER)
  public boolean runOnce() {
    Attempt attempt = transactions.execute(status -> claim());
    if (attempt == null) return false;
    Output output;
    try {
      output = render(attempt);
    } catch (QuarantineReadFailure failure) {
      finish(
          attempt,
          null,
          "Artifact source read failed: " + failure.code(),
          failure.code() != QuarantineReadFailure.Code.UNAVAILABLE);
      return true;
    } catch (RuntimeException failure) {
      finish(attempt, null, "Evidence rendering unavailable", false);
      return true;
    }
    finish(attempt, output, "Evidence rendering unavailable", false);
    return true;
  }

  private Output render(Attempt attempt) {
    QuarantineLifecycle.Reservation reservation =
        attempt.kind() == EvidenceArtifact.Kind.SCREENSHOT
            ? lifecycle.reserve(
                attempt.workspaceId(),
                attempt.brandId(),
                EvidenceRenderer.MAX_SCREENSHOT_BYTES,
                "png")
            : null;
    byte[] source =
        storage.readQuarantined(attempt.reference(), EvidenceObjectStorage.MAX_QUARANTINE_BYTES);
    if (source == null
        || source.length == 0
        || source.length > EvidenceObjectStorage.MAX_QUARANTINE_BYTES)
      throw new QuarantineReadFailure(QuarantineReadFailure.Code.INVALID_CONTENT);
    source = source.clone();
    if (!attempt.checksum().equals(checksum(source)))
      throw new QuarantineReadFailure(QuarantineReadFailure.Code.CHECKSUM_MISMATCH);
    if (attempt.kind() == EvidenceArtifact.Kind.PDF_TEXT) {
      EvidenceRenderer.DocumentText result = renderer.extractPdf(source);
      if (result == null
          || !attempt.checksum().equals(checksum(source))
          || !EvidenceArtifact.validText(result.text()))
        throw new IllegalStateException("Invalid evidence renderer response");
      return new Output(
          null,
          result.text(),
          checksum(result.text().getBytes(StandardCharsets.UTF_8)),
          result.version(),
          null,
          null,
          null,
          0);
    }
    byte[] utf8 = decoder.decodeForRendering(source, "text/html");
    String inputChecksum = checksum(utf8);
    EvidenceRenderer.Screenshot result = renderer.screenshot(utf8);
    if (result == null || !inputChecksum.equals(checksum(utf8)))
      throw new IllegalStateException("Invalid evidence renderer response");
    byte[] png = result.bytes();
    validatePng(png, result.width(), result.height());
    lifecycle.requireWritable(reservation.id());
    String reference = storage.storeQuarantined(reservation.objectKey(), png.clone());
    if (!reservation.reference().equals(reference))
      throw new IllegalStateException("Invalid evidence artifact storage response");
    return new Output(
        reference,
        null,
        checksum(png),
        result.version(),
        result.width(),
        result.height(),
        reservation,
        png.length);
  }

  private Attempt claim() {
    Instant now = now();
    var candidates = jobs.findArtifactCandidates(now, PageRequest.of(0, 1));
    if (candidates.isEmpty()) return null;
    DurableJob job = candidates.getFirst();
    UUID id;
    try {
      if (!job.getPayloadReference().startsWith("evidence:")) throw new IllegalArgumentException();
      id = UUID.fromString(job.getPayloadReference().substring("evidence:".length()));
    } catch (IllegalArgumentException failure) {
      return failUnsupportedJob(job, now);
    }
    BrandEvidence item = ownership.findLocked(id).orElse(null);
    now = now();
    if (job.getStatus() == JobStatus.RUNNING) {
      if (job.getLeaseExpiresAt().isAfter(now)) return null;
      job.recoverExpiredLease(now);
    }
    if (job.getStatus() == JobStatus.DEAD) return null;
    if ((job.getStatus() != JobStatus.PENDING && job.getStatus() != JobStatus.FAILED)
        || job.getNextAttemptAt().isAfter(now)) return null;
    String owner = UUID.randomUUID().toString();
    job.claim(owner, now, LEASE);
    EvidenceArtifact.Kind kind = kind(item);
    EvidenceScanResult latest = latest(id);
    if (kind == null || !active(item) || !clean(item, latest, now)) {
      job.fail(
          owner,
          job.getAttempts(),
          now,
          "Source has no current valid CLEAN scan for extraction",
          RETRY,
          true);
      return null;
    }
    if (artifacts
        .findByEvidenceIdAndSourceChecksumAndSourceVersionAndKind(
            id, EvidenceArtifact.sourceChecksum(item), item.getLockVersion(), kind)
        .isPresent()) {
      job.succeed(owner, job.getAttempts(), now);
      return null;
    }
    return new Attempt(
        job.getId(),
        id,
        latest.getId(),
        item.getBrand().getWorkspace().getId(),
        item.getBrand().getId(),
        owner,
        job.getAttempts(),
        item.getLockVersion(),
        sourceReference(item),
        EvidenceArtifact.sourceChecksum(item),
        kind);
  }

  private Attempt failUnsupportedJob(DurableJob job, Instant now) {
    if (job.getStatus() == JobStatus.RUNNING) {
      if (job.getLeaseExpiresAt().isAfter(now)) return null;
      job.recoverExpiredLease(now);
    }
    if (job.getStatus() == JobStatus.DEAD) return null;
    String owner = UUID.randomUUID().toString();
    job.claim(owner, now, LEASE);
    job.fail(owner, job.getAttempts(), now, "Invalid extraction job source", RETRY, true);
    return null;
  }

  private void finish(Attempt attempt, Output output, String error, boolean permanent) {
    transactions.executeWithoutResult(
        status -> {
          DurableJob job = jobs.findLockedById(attempt.jobId()).orElseThrow();
          BrandEvidence item = ownership.findLocked(attempt.evidenceId()).orElseThrow();
          Instant now = now();
          if (job.getStatus() != JobStatus.RUNNING
              || !attempt.owner().equals(job.getLeaseOwner())
              || job.getAttempts() != attempt.number()
              || !job.getLeaseExpiresAt().isAfter(now)) return;
          EvidenceScanResult latest = latest(item.getId());
          if (!active(item)
              || item.getLockVersion() != attempt.sourceVersion()
              || !attempt.reference().equals(sourceReference(item))
              || !attempt.checksum().equals(EvidenceArtifact.sourceChecksum(item))
              || !clean(item, latest, now)
              || !attempt.scanId().equals(latest.getId())) {
            job.fail(
                attempt.owner(),
                attempt.number(),
                now,
                "Source or scan changed during extraction",
                RETRY,
                true);
            return;
          }
          if (output == null) {
            job.fail(attempt.owner(), attempt.number(), now, error, RETRY, permanent);
            return;
          }
          if (artifacts
              .findByEvidenceIdAndSourceChecksumAndSourceVersionAndKind(
                  item.getId(), attempt.checksum(), attempt.sourceVersion(), attempt.kind())
              .isPresent()) {
            job.succeed(attempt.owner(), attempt.number(), now);
            return;
          }
          EvidenceArtifact artifact =
              artifacts.saveAndFlush(
                  new EvidenceArtifact(
                      item,
                      latest,
                      job,
                      attempt.kind(),
                      output.reference(),
                      output.text(),
                      output.checksum(),
                      output.version(),
                      now,
                      output.width(),
                      output.height()));
          if (output.reservation() != null)
            lifecycle.referenceArtifact(
                output.reservation().id(), artifact.getId(), output.reference(), output.bytes());
          job.succeed(attempt.owner(), attempt.number(), now);
        });
  }

  private EvidenceScanResult latest(UUID evidenceId) {
    return scans.latestForEvidence(evidenceId, PageRequest.of(0, 1)).stream()
        .findFirst()
        .orElse(null);
  }

  private boolean clean(BrandEvidence item, EvidenceScanResult scan, Instant now) {
    return item != null
        && scan != null
        && scan.getVerdict() == ScanVerdict.CLEAN
        && scan.getSourceVersion() == item.getLockVersion()
        && scan.getChecksum().equals(EvidenceArtifact.sourceChecksum(item))
        && !scan.getScannedAt().isAfter(now)
        && !scan.getScannedAt().isBefore(now.minus(maxScanAge));
  }

  private boolean active(BrandEvidence item) {
    return item != null
        && item.getStatus() != EvidenceStatus.FAILED
        && item.getBrand().getStatus() == BrandStatus.ACTIVE
        && item.getBrand().getWorkspace().getStatus() == WorkspaceStatus.ACTIVE;
  }

  private EvidenceArtifact.Kind kind(BrandEvidence item) {
    if (item == null) return null;
    if (item.getType() == EvidenceType.URL && "text/html".equals(item.getFetchedMediaType()))
      return EvidenceArtifact.Kind.SCREENSHOT;
    if (item.getType() == EvidenceType.FILE && "application/pdf".equals(item.getMediaType()))
      return EvidenceArtifact.Kind.PDF_TEXT;
    return null;
  }

  private String sourceReference(BrandEvidence item) {
    return item.getType() == EvidenceType.URL
        ? item.getFetchedReference()
        : item.getSourceReference();
  }

  private void validatePng(byte[] png, int width, int height) {
    byte[] prefix = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
    if (png.length < 24
        || png.length > EvidenceRenderer.MAX_SCREENSHOT_BYTES
        || !Arrays.equals(Arrays.copyOf(png, 8), prefix)
        || !Arrays.equals(Arrays.copyOfRange(png, 12, 16), new byte[] {0x49, 0x48, 0x44, 0x52})
        || ByteBuffer.wrap(png, 16, 8).getInt() != width
        || ByteBuffer.wrap(png, 20, 4).getInt() != height)
      throw new IllegalStateException("Invalid quarantined PNG output");
  }

  private String checksum(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
