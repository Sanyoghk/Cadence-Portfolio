package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.CsvExportDetails;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal orchestration; HTTP authorization is additionally enforced by CsvExportUseCase. */
@Service
public class CsvExportService {
  public record Ticket(UUID exportId, String worker, int attempt) {}

  public record Download(UUID id, String checksum, byte[] bytes) {}

  private final CsvExportRepository exports;
  private final DurableJobRepository jobs;
  private final CsvExportSources sources;
  private final MediaVerification verification;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final CsvExportRenderer renderer;
  private final String worker = "csv-" + UUID.randomUUID();
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(1);

  public CsvExportService(
      CsvExportRepository exports,
      DurableJobRepository jobs,
      CsvExportSources sources,
      MediaVerification verification,
      PlatformTransactionManager manager,
      Clock clock,
      CsvExportRenderer renderer) {
    this.renderer = renderer;
    this.exports = exports;
    this.jobs = jobs;
    this.sources = sources;
    this.verification = verification;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public CsvExportDetails submit(
      UUID actor, UUID brand, Set<UUID> scope, String key, CsvExportRequest request) {
    if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}") || request == null)
      throw new IllegalArgumentException("Invalid export idempotency key or request");
    var prior =
        tx.execute(
            s -> {
              sources.authorize(actor, brand, scope);
              return replay(brand, actor, key, request);
            });
    if (prior != null) return prior;
    var captured = new AtomicReference<Instant>();
    return verification.execute(
        () -> {
          var b = sources.authorize(actor, brand, scope);
          if (captured.get() == null) captured.set(clock.instant());
          return sources.capture(actor, b, request, captured.get()).media();
        },
        () -> {
          var b = sources.authorize(actor, brand, scope);
          var existing = replay(brand, actor, key, request);
          if (existing != null) return existing;
          if (exports.countByBrandId(brand) >= 100)
            throw new IllegalStateException("Retained export quota reached");
          var c = sources.capture(actor, b, request, captured.get());
          var job =
              jobs.saveAndFlush(
                  new DurableJob(
                      "CSV_EXPORT",
                      "csv-export:pending",
                      "csv:" + actor + ":" + brand + ":" + key,
                      3,
                      clock.instant()));
          var batch =
              exports.save(
                  new CsvExportBatch(
                      b,
                      job,
                      actor,
                      key,
                      request,
                      captured.get(),
                      c.items(),
                      CsvDocument.checksum(c.rows())));
          job.bindExport(batch.getId());
          exports.flush();
          jobs.flush();
          return CsvExportDetails.from(batch);
        });
  }

  private CsvExportDetails replay(UUID brand, UUID actor, String key, CsvExportRequest request) {
    return exports
        .findByBrandIdAndActorIdAndClientKey(brand, actor, key)
        .map(
            e -> {
              if (!e.getRequestDigest().equals(request.digest()))
                throw new IllegalStateException("Export key payload conflict");
              return CsvExportDetails.from(e);
            })
        .orElse(null);
  }

  public CsvExportDetails status(UUID actor, Set<UUID> scope, UUID id, boolean byJob) {
    return tx.execute(
        s -> {
          var exportId = byJob ? exports.scopedJobId(id, scope).orElse(null) : id;
          if (exportId == null) return null;
          var e = authorized(exportId, actor, scope);
          return CsvExportDetails.from(e);
        });
  }

  private CsvExportBatch authorized(UUID id, UUID actor, Set<UUID> scope) {
    if (scope == null || scope.isEmpty()) throw missing();
    var target =
        exports
            .target(id)
            .filter(t -> scope.contains(t.getWorkspaceId()))
            .orElseThrow(CsvExportService::missing);
    sources.authorize(actor, target.getBrandId(), scope);
    return exports.findById(id).orElseThrow(CsvExportService::missing);
  }

  private CsvExportBatch workerInput(UUID id) {
    var target = exports.target(id).orElseThrow(CsvExportService::missing);
    return authorized(id, target.getActorId(), Set.of(target.getWorkspaceId()));
  }

  public Download download(UUID actor, Set<UUID> scope, UUID id) {
    return verification.execute(
        () -> {
          var e = authorized(id, actor, scope);
          e.bytes(clock.instant());
          return sources.current(e, actor, scope).media();
        },
        () -> {
          var e = authorized(id, actor, scope);
          sources.current(e, actor, scope);
          return new Download(e.getId(), e.getArtifactChecksum(), e.bytes(clock.instant()));
        });
  }

  /** Candidate scans do not hold Job locks before the shared parent lock order. */
  public Ticket claim(UUID id) {
    return tx.execute(
        s -> {
          sourcesLock(id);
          var e = exports.locked(id).orElseThrow();
          var j = jobs.findLockedById(e.getJob().getId()).orElseThrow();
          var now = clock.instant();
          if (j.getStatus() == JobStatus.RUNNING && !j.getLeaseExpiresAt().isAfter(now))
            j.recoverExpiredLease(now);
          if (!Set.of(JobStatus.PENDING, JobStatus.FAILED).contains(j.getStatus())
              || j.getNextAttemptAt().isAfter(now)) return null;
          j.claim(worker, now, Duration.ofSeconds(60));
          return new Ticket(id, worker, j.getAttempts());
        });
  }

  // Lock without granting access: even revoked actors need durable terminal recovery.
  private void sourcesLock(UUID id) {
    // The lock primitive deliberately does not authorize; every consumer uses current() afterwards.
    var target = exports.target(id).orElseThrow(CsvExportService::missing);
    sources.lock(target.getActorId(), target.getBrandId(), Set.of(target.getWorkspaceId()));
  }

  public void execute(Ticket ticket) {
    if (ticket == null) return;
    try {
      var rows = new AtomicReference<List<List<String>>>();
      verification.execute(
          () -> {
            var e = workerInput(ticket.exportId());
            var scope = Set.of(e.getBrand().getWorkspace().getId());
            var c = sources.current(e, e.getActorId(), scope);
            requireTicket(e, ticket);
            rows.set(c.rows());
            return c.media();
          },
          () -> null);
      // No transaction/lock held during CPU rendering; bytes only depend on immutable captured
      // input.
      byte[] bytes = renderer.render(ticket.exportId(), rows.get());
      verification.execute(
          () -> {
            var e = workerInput(ticket.exportId());
            var c = sources.current(e, e.getActorId(), Set.of(e.getBrand().getWorkspace().getId()));
            requireTicket(e, ticket);
            if (!c.rows().equals(rows.get()))
              throw new IllegalStateException("Export input changed");
            return c.media();
          },
          () -> {
            var e = exports.locked(ticket.exportId()).orElseThrow();
            var j = jobs.findLockedById(e.getJob().getId()).orElseThrow();
            requireTicket(e, ticket);
            var now = clock.instant();
            e.publish(bytes, ticket.worker(), ticket.attempt(), now);
            exports.flush();
            requireTicket(e, ticket);
            j.succeed(ticket.worker(), ticket.attempt(), now);
            jobs.flush();
            return null;
          });
    } catch (RuntimeException failure) {
      WorkerFailureDiagnostics.rejected(
          WorkerFailureDiagnostics.Kind.CSV_EXPORT, ticket.exportId(), ticket.attempt(), failure);
      String code =
          failure instanceof MediaUnavailableException
              ? "CSV_INPUT_UNAVAILABLE"
              : "CSV_SOURCE_UNAVAILABLE";
      fail(ticket, code, !(failure instanceof MediaUnavailableException));
    }
  }

  private void requireTicket(CsvExportBatch e, Ticket t) {
    var j = e.getJob();
    var now = clock.instant();
    if (j.getStatus() != JobStatus.RUNNING
        || j.getAttempts() != t.attempt()
        || !Objects.equals(j.getLeaseOwner(), t.worker())
        || j.getLeaseExpiresAt() == null
        || !j.getLeaseExpiresAt().isAfter(now)
        || !now.isBefore(e.getDeadline()))
      throw new IllegalStateException("Export lease or deadline expired");
  }

  private void fail(Ticket t, String code, boolean permanent) {
    tx.executeWithoutResult(
        s -> {
          sourcesLock(t.exportId());
          var e = exports.locked(t.exportId()).orElseThrow();
          var j = jobs.findLockedById(e.getJob().getId()).orElseThrow();
          var now = clock.instant();
          if (j.getStatus() == JobStatus.RUNNING
              && j.getAttempts() == t.attempt()
              && Objects.equals(j.getLeaseOwner(), t.worker())
              && j.getLeaseExpiresAt().isAfter(now))
            j.fail(
                t.worker(),
                t.attempt(),
                now,
                now.isBefore(e.getDeadline()) ? code : "CSV_DEADLINE_EXCEEDED",
                new RetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10)),
                permanent || !now.isBefore(e.getDeadline()));
        });
  }

  public void runOnce() {
    if (!slots.tryAcquire()) return;
    try {
      var ids = tx.execute(s -> exports.candidates(clock.instant(), PageRequest.of(0, 10)));
      for (var id : Objects.requireNonNull(ids)) {
        var t = claim(id);
        if (t != null) {
          execute(t);
          break;
        }
      }
    } finally {
      slots.release();
    }
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Export Job not found");
  }
}
