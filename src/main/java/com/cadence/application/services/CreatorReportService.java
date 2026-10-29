package com.cadence.application.services;

import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
public class CreatorReportService {
  public record Ticket(UUID reportId, String worker, int attempt) {}

  public record Download(UUID id, String checksum, byte[] bytes) {}

  private final CreatorReportRepository reports;
  private final DurableJobRepository jobs;
  private final CreatorReportSources sources;
  private final CreatorReportRenderer renderer;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final String worker = "report-" + UUID.randomUUID();
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(1);
  private final java.util.concurrent.Semaphore captureSlots = new java.util.concurrent.Semaphore(2);

  public CreatorReportService(
      CreatorReportRepository reports,
      DurableJobRepository jobs,
      CreatorReportSources sources,
      CreatorReportRenderer renderer,
      ObjectMapper mapper,
      Clock clock,
      PlatformTransactionManager manager) {
    this.reports = reports;
    this.jobs = jobs;
    this.sources = sources;
    this.renderer = renderer;
    this.mapper = mapper;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    tx.setTimeout(5);
  }

  public CreatorReportDetails submit(
      UUID actor, UUID brand, Set<UUID> scope, String key, CreatorReportRequest request) {
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}") || request == null)
      throw new IllegalArgumentException("Invalid report key or request");
    if (!captureSlots.tryAcquire())
      throw new com.cadence.application.exceptions.CreatorReportUnavailableException();
    try {
      return tx.execute(
          t -> {
            var b = sources.authorize(actor, brand, scope);
            var kd = CreatorReport.hash(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var digest = request.digest(brand);
            var old =
                reports.findByActorIdAndWorkspaceIdAndKeyDigest(
                    actor, b.getWorkspace().getId(), kd);
            if (old.isPresent()) {
              if (!old.get().getRequestDigest().equals(digest))
                throw new IllegalStateException("Report key payload conflict");
              return CreatorReportDetails.from(old.get(), clock.instant());
            }
            if (reports.countByBrandId(brand) >= 100)
              throw new IllegalStateException("Report retained quota reached");
            var source =
                sources.source(
                    brand, request.strategyId(), request.expectedStrategyVersion(), null);
            var now = clock.instant();
            var capture = sources.capture(actor, b, source, scope, request, now);
            var job =
                jobs.saveAndFlush(
                    new DurableJob(
                        "CREATOR_REPORT",
                        "report:pending",
                        "report:" + actor + ":" + b.getWorkspace().getId() + ":" + kd,
                        3,
                        now));
            var report =
                reports.save(
                    new CreatorReport(
                        b,
                        source,
                        job,
                        actor,
                        kd,
                        digest,
                        json(capture),
                        capture.strategy().checksum(),
                        now));
            job.bindReport(report.getId());
            reports.flush();
            jobs.flush();
            return CreatorReportDetails.from(report, now);
          });
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionTimedOutException e) {
      throw new com.cadence.application.exceptions.CreatorReportUnavailableException(e);
    } finally {
      captureSlots.release();
    }
  }

  private CreatorReport authorized(UUID actor, Set<UUID> scope, UUID id) {
    var target =
        reports
            .target(id)
            .filter(v -> scope != null && scope.contains(v.getWorkspaceId()))
            .orElseThrow(CreatorReportSources::missing);
    sources.authorize(actor, target.getBrandId(), scope);
    return reports.findById(id).orElseThrow(CreatorReportSources::missing);
  }

  public CreatorReportDetails status(UUID actor, Set<UUID> scope, UUID id, boolean byJob) {
    return tx.execute(
        t -> {
          var reportId = byJob ? reports.scopedJobId(id, scope).orElse(null) : id;
          if (reportId == null) return null;
          return CreatorReportDetails.from(authorized(actor, scope, reportId), clock.instant());
        });
  }

  private CreatorReportSnapshot current(CreatorReport r) {
    sources.source(
        r.getBrand().getId(), r.getStrategy().getId(), r.getSourceVersion(), r.getSourceChecksum());
    if (!CreatorReport.hash(r.getSnapshot().getBytes(java.nio.charset.StandardCharsets.UTF_8))
        .equals(r.getSnapshotChecksum()))
      throw new IllegalStateException("Corrupt report snapshot");
    try {
      var s = mapper.readValue(r.getSnapshot(), CreatorReportSnapshot.class);
      if (!s.brandId().equals(r.getBrand().getId())
          || !s.workspaceId().equals(r.getWorkspaceId())
          || !s.strategy().id().equals(r.getStrategy().getId())
          || s.strategy().version() != r.getSourceVersion()
          || !s.strategy().checksum().equals(r.getSourceChecksum())
          || !s.capturedAt().equals(r.getCapturedAt())
          || !s.request().digest(s.brandId()).equals(r.getRequestDigest()))
        throw new IllegalStateException("Corrupt report source binding");
      return s;
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Corrupt report snapshot");
    }
  }

  public CreatorReportSnapshot result(UUID actor, Set<UUID> scope, UUID id) {
    return tx.execute(
        t -> {
          var r = authorized(actor, scope, id);
          r.requireResult(clock.instant());
          return current(r);
        });
  }

  public Download download(UUID actor, Set<UUID> scope, UUID id) {
    return tx.execute(
        t -> {
          var r = authorized(actor, scope, id);
          current(r);
          return new Download(r.getId(), r.getArtifactChecksum(), r.bytes(clock.instant()));
        });
  }

  private void lock(UUID id) {
    var target = reports.target(id).orElseThrow(CreatorReportSources::missing);
    sources.lock(target.getActorId(), target.getBrandId(), Set.of(target.getWorkspaceId()));
  }

  public Ticket claim(UUID id) {
    return tx.execute(
        t -> {
          lock(id);
          var r = reports.locked(id).orElseThrow();
          var j = jobs.findLockedById(r.getJob().getId()).orElseThrow();
          var now = clock.instant();
          if (j.getStatus() == JobStatus.RUNNING && !j.getLeaseExpiresAt().isAfter(now))
            j.recoverExpiredLease(now);
          if (!Set.of(JobStatus.PENDING, JobStatus.FAILED).contains(j.getStatus())
              || j.getNextAttemptAt().isAfter(now)) return null;
          j.claim(worker, now, Duration.ofSeconds(60));
          return new Ticket(id, worker, j.getAttempts());
        });
  }

  public void execute(Ticket ticket) {
    if (ticket == null) return;
    boolean rendering = false;
    try {
      var snapshot =
          tx.execute(
              t -> {
                var target =
                    reports.target(ticket.reportId()).orElseThrow(CreatorReportSources::missing);
                var r =
                    authorized(
                        target.getActorId(), Set.of(target.getWorkspaceId()), ticket.reportId());
                r.requireLease(ticket.worker(), ticket.attempt(), clock.instant());
                return current(r);
              });
      if (TransactionSynchronizationManager.isActualTransactionActive())
        throw new IllegalStateException("Report render inside transaction");
      rendering = true;
      var bytes = renderer.render(ticket.reportId(), snapshot);
      rendering = false;
      tx.executeWithoutResult(
          t -> {
            var target =
                reports.target(ticket.reportId()).orElseThrow(CreatorReportSources::missing);
            var authorizedReport =
                authorized(target.getActorId(), Set.of(target.getWorkspaceId()), ticket.reportId());
            current(authorizedReport);
            var r = reports.locked(ticket.reportId()).orElseThrow();
            var j = jobs.findLockedById(r.getJob().getId()).orElseThrow();
            var completedAt = clock.instant();
            r.publish(bytes, ticket.worker(), ticket.attempt(), completedAt);
            reports.flush();
            r.requireLease(ticket.worker(), ticket.attempt(), clock.instant());
            j.succeed(ticket.worker(), ticket.attempt(), completedAt);
            jobs.flush();
          });
    } catch (RuntimeException failure) {
      WorkerFailureDiagnostics.rejected(
          WorkerFailureDiagnostics.Kind.CREATOR_REPORT,
          ticket.reportId(),
          ticket.attempt(),
          failure);
      boolean invalidOutput = failure instanceof CreatorReportOutputException;
      fail(
          ticket,
          invalidOutput
              ? "REPORT_OUTPUT_INVALID"
              : rendering ? "REPORT_RENDER_FAILED" : "REPORT_SOURCE_UNAVAILABLE",
          invalidOutput || !rendering);
    }
  }

  private void fail(Ticket ticket, String code, boolean permanent) {
    tx.executeWithoutResult(
        t -> {
          lock(ticket.reportId());
          var r = reports.locked(ticket.reportId()).orElseThrow();
          var j = jobs.findLockedById(r.getJob().getId()).orElseThrow();
          var now = clock.instant();
          if (j.getStatus() == JobStatus.RUNNING
              && j.getAttempts() == ticket.attempt()
              && Objects.equals(j.getLeaseOwner(), ticket.worker())
              && j.getLeaseExpiresAt().isAfter(now))
            j.fail(
                ticket.worker(),
                ticket.attempt(),
                now,
                now.isBefore(r.getDeadline()) ? code : "REPORT_DEADLINE_EXCEEDED",
                new RetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10)),
                permanent || !now.isBefore(r.getDeadline()));
        });
  }

  public void runOnce() {
    if (!slots.tryAcquire()) return;
    try {
      var ids = tx.execute(t -> reports.candidates(clock.instant(), PageRequest.of(0, 10)));
      for (var id : Objects.requireNonNull(ids)) {
        var ticket = claim(id);
        if (ticket != null) {
          execute(ticket);
          break;
        }
      }
    } finally {
      slots.release();
    }
  }

  private String json(Object o) {
    try {
      return mapper.writeValueAsString(o);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid report input");
    }
  }
}
