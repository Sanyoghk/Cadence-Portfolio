package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Account -> Workspace -> Brand -> sorted Post -> import -> Job; parsing outside transactions. */
@Service
public class MetricImportService {
  public record Ticket(UUID id, String worker, int attempt) {}

  public record ObservationPage(
      List<MetricObservationDetails> items, int page, int size, long totalElements) {}

  private final MetricImportRepository imports;
  private final MetricObservationRepository observations;
  private final ContentPostRepository posts;
  private final DurableJobRepository jobs;
  private final AiGenerationSources parents;
  private final MetricImportParser parser;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final String worker = "metrics-" + UUID.randomUUID();
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(1);

  public MetricImportService(
      MetricImportRepository imports,
      MetricObservationRepository observations,
      ContentPostRepository posts,
      DurableJobRepository jobs,
      AiGenerationSources parents,
      MetricImportParser parser,
      Clock clock,
      PlatformTransactionManager manager) {
    this.imports = imports;
    this.observations = observations;
    this.posts = posts;
    this.jobs = jobs;
    this.parents = parents;
    this.parser = parser;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  private Brand authorize(UUID actor, UUID brand, Set<UUID> scope) {
    var b = parents.authorize(actor, brand, scope);
    if (b.getStatus() != BrandStatus.ACTIVE
        || b.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Metrics parent archived");
    return b;
  }

  public MetricImportDetails submit(
      UUID actor, UUID brand, Set<UUID> scope, String key, String origin, byte[] raw) {
    if (key == null
        || !key.matches("[A-Za-z0-9_-]{16,128}")
        || raw == null
        || raw.length < 1
        || raw.length > 131072
        || origin == null
        || !Set.of("CSV", "MANUAL_JSON").contains(origin))
      throw new IllegalArgumentException("Invalid metric import request");
    var bytes = raw.clone();
    var digest = MetricImport.digest(origin, bytes);
    var prior =
        tx.execute(
            s -> {
              authorize(actor, brand, scope);
              return replay(actor, brand, key, digest);
            });
    if (prior != null) return prior;
    var rows = parser.parse(origin, bytes);
    return tx.execute(
        s -> {
          var b = authorize(actor, brand, scope);
          var existing = replay(actor, brand, key, digest);
          if (existing != null) return existing;
          validateRows(b, rows, clock.instant());
          if (imports.countByBrandId(brand) >= 1000)
            throw new IllegalStateException("Retained metric import quota reached");
          var now = clock.instant();
          var j =
              jobs.saveAndFlush(
                  new DurableJob(
                      "METRICS_IMPORT",
                      "metrics:pending",
                      "metrics:" + actor + ":" + brand + ":" + key,
                      3,
                      now));
          var e = imports.save(new MetricImport(b, j, actor, key, origin, bytes, rows.size(), now));
          j.bindMetrics(e.getId());
          imports.flush();
          jobs.flush();
          return MetricImportDetails.from(e);
        });
  }

  private MetricImportDetails replay(UUID actor, UUID brand, String key, String digest) {
    return imports
        .findByBrandIdAndActorIdAndClientKey(brand, actor, key)
        .map(
            e -> {
              if (!e.getRequestDigest().equals(digest))
                throw new IllegalStateException("Metrics key payload conflict");
              return MetricImportDetails.from(e);
            })
        .orElse(null);
  }

  private Map<UUID, ContentPost> validateRows(Brand b, List<MetricRow> rows, Instant now) {
    var selected = new LinkedHashMap<UUID, ContentPost>();
    rows.stream()
        .map(MetricRow::postId)
        .distinct()
        .sorted()
        .forEach(
            id ->
                selected.put(
                    id, posts.findLocked(id, b.getId()).orElseThrow(MetricImportService::missing)));
    for (var r : rows) {
      var p = selected.get(r.postId());
      if (p.isLegacy() || p.getPlatform() != r.platform() || r.observedAt().isAfter(now))
        throw new IllegalArgumentException("Invalid metrics Post, platform or source time");
    }
    return selected;
  }

  private MetricImportRepository.Target target(UUID id) {
    return imports.target(id).orElseThrow(MetricImportService::missing);
  }

  private MetricImport authorized(UUID actor, Set<UUID> scope, UUID id) {
    var t = target(id);
    if (!scope.contains(t.getWorkspaceId())) throw missing();
    authorize(actor, t.getBrandId(), scope);
    return imports.findById(id).orElseThrow(MetricImportService::missing);
  }

  public MetricImportDetails status(UUID actor, Set<UUID> scope, UUID id, boolean byJob) {
    return tx.execute(
        s -> {
          var actual = byJob ? imports.scopedJobId(id, scope).orElse(null) : id;
          return actual == null ? null : MetricImportDetails.from(authorized(actor, scope, actual));
        });
  }

  public ObservationPage read(
      UUID actor,
      Set<UUID> scope,
      UUID post,
      Instant from,
      Instant until,
      boolean history,
      int page,
      int size) {
    if (from == null
        || until == null
        || from.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
        || until.isAfter(Instant.parse("2200-01-01T00:00:00Z"))
        || from.getNano() % 1000 != 0
        || until.getNano() % 1000 != 0
        || !from.isBefore(until)
        || Duration.between(from, until).compareTo(Duration.ofDays(366)) > 0
        || page < 0
        || page > 10000
        || size < 1
        || size > 100) throw new IllegalArgumentException("Invalid metrics query bounds");
    return tx.execute(
        s -> {
          var b = posts.findBrandIdIn(post, scope).orElseThrow(MetricImportService::missing);
          authorize(actor, b, scope);
          posts.findLocked(post, b).orElseThrow(MetricImportService::missing);
          var result =
              observations.observations(post, from, until, history, PageRequest.of(page, size));
          return new ObservationPage(
              result.stream().map(MetricObservationDetails::from).toList(),
              page,
              size,
              result.getTotalElements());
        });
  }

  private void lock(UUID id) {
    var t = target(id);
    parents.lockParents(t.getActorId(), t.getBrandId(), Set.of(t.getWorkspaceId()));
  }

  public Ticket claim(UUID id) {
    return tx.execute(
        s -> {
          lock(id);
          var e = imports.locked(id).orElseThrow();
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

  private void requireTicket(MetricImport e, Ticket t) {
    var j = e.getJob();
    var now = clock.instant();
    if (j.getStatus() != JobStatus.RUNNING
        || j.getAttempts() != t.attempt()
        || !Objects.equals(j.getLeaseOwner(), t.worker())
        || j.getLeaseExpiresAt() == null
        || !j.getLeaseExpiresAt().isAfter(now)
        || !now.isBefore(e.getDeadline()))
      throw new IllegalStateException("Metrics lease or deadline expired");
  }

  public void execute(Ticket t) {
    if (t == null) return;
    try {
      var e =
          tx.execute(
              s -> {
                var target = target(t.id());
                var input =
                    authorized(target.getActorId(), Set.of(target.getWorkspaceId()), t.id());
                requireTicket(input, t);
                return new Raw(input.getOrigin(), input.raw(), input.getRawChecksum());
              });
      if (!MetricRow.sha(e.bytes()).equals(e.checksum()))
        throw new IllegalStateException("Metrics raw checksum mismatch");
      var rows = parser.parse(e.origin(), e.bytes());
      tx.executeWithoutResult(
          s -> {
            var target = target(t.id());
            var b =
                authorize(
                    target.getActorId(), target.getBrandId(), Set.of(target.getWorkspaceId()));
            var selected = validateRows(b, rows, clock.instant());
            var batch = imports.locked(t.id()).orElseThrow();
            jobs.findLockedById(batch.getJob().getId()).orElseThrow();
            requireTicket(batch, t);
            if (rows.size() != batch.getRowCount() || !e.checksum().equals(batch.getRawChecksum()))
              throw new IllegalStateException("Metrics snapshot changed");
            // Ingestion audit belongs to this atomic publication transaction. Lease/deadline checks
            // still use fresh wall time before and after publication; reported source times stay
            // raw.
            var ingestedAt = imports.transactionTime();
            var ids = new ArrayList<UUID>();
            for (var row : rows) {
              var prior =
                  observations
                      .findTopByPostIdAndObservedAtOrderByRevisionNumberDesc(
                          row.postId(), row.observedAt())
                      .orElse(null);
              if (row.correctionOf() != null
                  && (prior == null || !prior.getId().equals(row.correctionOf())))
                throw new IllegalStateException("Metrics correction conflict");
              if (prior != null && prior.getValueChecksum().equals(row.digest())) {
                ids.add(prior.getId());
                continue;
              }
              if (prior == null
                  ? row.correctionOf() != null
                  : !prior.getId().equals(row.correctionOf()))
                throw new IllegalStateException("Metrics correction conflict");
              if (observations.countByPostBrandId(b.getId()) >= 100000)
                throw new IllegalStateException("Retained metrics quota reached");
              ids.add(
                  observations
                      .saveAndFlush(
                          new MetricObservation(
                              selected.get(row.postId()), batch, row, prior, ingestedAt))
                      .getId());
            }
            requireTicket(batch, t);
            var completedAt = clock.instant();
            batch.publish(ids, t.worker(), t.attempt(), completedAt);
            imports.flush();
            // One atomic publication has one completion timestamp; wall-clock samples can regress.
            // A separate fresh check still fences expiry after the SQL publication write.
            requireTicket(batch, t);
            batch.getJob().succeed(t.worker(), t.attempt(), completedAt);
            jobs.flush();
          });
    } catch (RuntimeException failure) {
      WorkerFailureDiagnostics.rejected(
          WorkerFailureDiagnostics.Kind.METRIC_IMPORT, t.id(), t.attempt(), failure);
      fail(
          t,
          failure instanceof TransientDataAccessException
              || failure
                  instanceof com.cadence.application.exceptions.MetricImportUnavailableException);
    }
  }

  private record Raw(String origin, byte[] bytes, String checksum) {}

  private void fail(Ticket t, boolean retryable) {
    tx.executeWithoutResult(
        s -> {
          lock(t.id());
          var e = imports.locked(t.id()).orElseThrow();
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
                !now.isBefore(e.getDeadline())
                    ? "METRICS_DEADLINE_EXCEEDED"
                    : retryable ? "METRICS_RETRYABLE" : "METRICS_INPUT_UNAVAILABLE",
                new RetryPolicy(Duration.ofSeconds(2), Duration.ofSeconds(10)),
                !retryable || !now.isBefore(e.getDeadline()));
        });
  }

  public void runOnce() {
    if (!slots.tryAcquire()) return;
    try {
      var candidates = tx.execute(s -> imports.candidates(clock.instant(), PageRequest.of(0, 10)));
      for (var id : Objects.requireNonNull(candidates)) {
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

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Metrics resource not found");
  }
}
