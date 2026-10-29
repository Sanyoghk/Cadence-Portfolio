package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Internal durable coordinator. HTTP calls enter through secured AiGenerationUseCase. */
@Service
public class AiGenerationService {
  public record View(
      UUID jobId,
      UUID operationId,
      String kind,
      String status,
      Instant submittedAt,
      Instant deadline,
      Instant completedAt,
      UUID resultId,
      String resultUrl,
      int attempts,
      String errorCode) {
    public boolean terminal() {
      return !Set.of("QUEUED", "RUNNING", "RETRY_WAIT").contains(status);
    }
  }

  public record Ticket(UUID operationId, String worker, int attempt) {}

  private static final class InvalidOutput extends RuntimeException {}

  private static final class AlreadyDispatched extends RuntimeException {}

  private final AiGenerationOperationRepository operations;
  private final AiGenerationAttemptRepository attempts;
  private final DurableJobRepository jobs;
  private final AiGenerationSources sources;
  private final IdentitySourceVerification verification;
  private final ObjectMapper json;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final BrandAnalysisAttemptRepository analyses;
  private final BrandAnalysisSourceRepository links;
  private final BrandAnalysisCitationRepository citations;
  private final EvidenceAdmissionRepository admissions;
  private final BrandRepository brands;
  private final StrategyRepository strategies;
  private final BrandIngestService analyzer;
  private final StrategyGenerationService generator;
  private final Environment environment;
  private final ContentGenerationService contentGenerator;
  private final ContentPublication contentPublication;
  private final ContentPostRepository contentPosts;
  private final int retainedCap;
  private final GenerationBudgets budgets;
  private final java.util.concurrent.Semaphore capacity = new java.util.concurrent.Semaphore(2);
  // No backlog and no replacement threads for a provider that ignores interruption.
  private final java.util.concurrent.ThreadPoolExecutor calls =
      new java.util.concurrent.ThreadPoolExecutor(
          2,
          2,
          0,
          java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.SynchronousQueue<>(),
          Thread.ofPlatform().daemon().name("ai-generation-call-", 0).factory(),
          new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

  @jakarta.annotation.PreDestroy
  void stopCalls() {
    calls.shutdownNow();
  }

  @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;

  public AiGenerationService(
      AiGenerationOperationRepository operations,
      AiGenerationAttemptRepository attempts,
      DurableJobRepository jobs,
      AiGenerationSources sources,
      IdentitySourceVerification verification,
      ObjectMapper json,
      Clock clock,
      PlatformTransactionManager manager,
      BrandAnalysisAttemptRepository analyses,
      BrandAnalysisSourceRepository links,
      BrandAnalysisCitationRepository citations,
      EvidenceAdmissionRepository admissions,
      BrandRepository brands,
      StrategyRepository strategies,
      BrandIngestService analyzer,
      StrategyGenerationService generator,
      ContentGenerationService contentGenerator,
      ContentPublication contentPublication,
      ContentPostRepository contentPosts,
      Environment environment,
      AiGenerationTelemetry telemetry,
      GenerationBudgets budgets,
      @Value("${cadence.ai-jobs.max-retained-per-workspace:1000}") int retainedCap) {
    if (retainedCap < 1 || retainedCap > 10000)
      throw new IllegalArgumentException("Invalid AI history cap");
    this.retainedCap = retainedCap;
    this.operations = operations;
    this.attempts = attempts;
    this.jobs = jobs;
    this.sources = sources;
    this.verification = verification;
    this.json = json;
    this.clock = clock;
    this.analyses = analyses;
    this.links = links;
    this.citations = citations;
    this.admissions = admissions;
    this.brands = brands;
    this.strategies = strategies;
    this.analyzer = analyzer;
    this.generator = generator;
    this.environment = environment;
    this.contentGenerator = contentGenerator;
    this.contentPublication = contentPublication;
    this.contentPosts = contentPosts;
    this.telemetry = telemetry;
    this.budgets = budgets;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    tx.setTimeout(10);
  }

  private Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  private void capability(AiGenerationOperation.Kind kind) {
    String property =
        kind == AiGenerationOperation.Kind.STRATEGY
            ? "cadence.strategy-generation.adapter"
            : kind == AiGenerationOperation.Kind.CONTENT
                ? "cadence.content-generation.adapter"
                : "cadence.brand-analysis.adapter";
    if (environment.acceptsProfiles(Profiles.of("prod"))
        || !environment.acceptsProfiles(Profiles.of("local", "test"))
        || !"fake".equals(environment.getProperty(property, "disabled")))
      throw new BrandAnalysisUnavailableException();
  }

  public View submit(
      UUID actor,
      Set<UUID> scope,
      UUID brandId,
      AiGenerationOperation.Kind kind,
      long expected,
      String key) {
    if (kind == null
        || kind == AiGenerationOperation.Kind.CONTENT
        || expected < 0
        || key == null
        || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("Invalid generation request");
    String keyHash = BrandAnalysisAttempt.checksum(key),
        requestHash =
            BrandAnalysisAttempt.checksum(
                "ai-request-v1\n" + brandId + "\n" + kind + "\n" + expected);
    return tx.execute(
        s -> {
          var brand = sources.authorize(actor, brandId, scope);
          var existing =
              operations.findByActorIdAndWorkspaceIdAndKindAndKeyDigest(
                  actor, brand.getWorkspace().getId(), kind, keyHash);
          if (existing.isPresent()) {
            if (!existing.get().getRequestDigest().equals(requestHash))
              throw new IllegalStateException("Idempotency request conflict");
            return view(existing.get());
          }
          capability(kind);
          if (brand.getLockVersion() != expected)
            throw new IllegalStateException("Stale Brand version");
          if (operations.countByWorkspaceId(brand.getWorkspace().getId()) >= retainedCap)
            throw new EvidenceQuotaExceededException();
          var now = now();
          var capture = sources.capture(actor, brandId, scope, kind, now, null);
          var job =
              jobs.saveAndFlush(
                  new DurableJob(
                      "AI_GENERATION", "generation:pending", "ai:" + UUID.randomUUID(), 3, now));
          var operation =
              operations.saveAndFlush(
                  new AiGenerationOperation(
                      actor,
                      brand.getWorkspace().getId(),
                      brandId,
                      kind,
                      keyHash,
                      requestHash,
                      encode(AiGenerationSnapshot.of(capture.input())),
                      now,
                      job,
                      capture.admissions()));
          job.bindGeneration(operation.getId());
          jobs.flush();
          budgets.reserve(operation);
          return view(operation);
        });
  }

  public View submitContent(
      UUID actor,
      Set<UUID> scope,
      UUID brandId,
      long expected,
      String key,
      ContentGenerationRequest request) {
    return tx.execute(s -> submitContentLocked(actor, scope, brandId, expected, key, request));
  }

  private View submitContentLocked(
      UUID actor,
      Set<UUID> scope,
      UUID brandId,
      long expected,
      String key,
      ContentGenerationRequest request) {
    if (expected < 0 || key == null || !key.matches("[A-Za-z0-9._:-]{1,128}") || request == null)
      throw new IllegalArgumentException("Invalid Content request");
    var keyHash = BrandAnalysisAttempt.checksum(key);
    var requestHash =
        BrandAnalysisAttempt.checksum(
            "content-request-v1\n"
                + brandId
                + "\n"
                + (request.postId() == null ? Long.toString(expected) : "regeneration")
                + "\n"
                + encode(request));
    var brand = sources.authorize(actor, brandId, scope);
    var existing =
        operations.findByActorIdAndWorkspaceIdAndKindAndKeyDigest(
            actor, brand.getWorkspace().getId(), AiGenerationOperation.Kind.CONTENT, keyHash);
    if (existing.isPresent()) {
      if (!existing.get().getRequestDigest().equals(requestHash))
        throw new IllegalStateException("Idempotency request conflict");
      return view(existing.get());
    }
    capability(AiGenerationOperation.Kind.CONTENT);
    if (brand.getLockVersion() != expected) throw new IllegalStateException("Stale Brand version");
    if (operations.countByWorkspaceId(brand.getWorkspace().getId()) >= retainedCap)
      throw new EvidenceQuotaExceededException();
    var now = now();
    var captured = sources.captureContent(actor, brandId, scope, request, now, null);
    boundedEncode(captured.input().content().command());
    var job =
        jobs.saveAndFlush(
            new DurableJob(
                "AI_GENERATION", "generation:pending", "ai:" + UUID.randomUUID(), 3, now));
    var o =
        operations.saveAndFlush(
            new AiGenerationOperation(
                actor,
                brand.getWorkspace().getId(),
                brandId,
                AiGenerationOperation.Kind.CONTENT,
                keyHash,
                requestHash,
                boundedEncode(AiGenerationSnapshot.of(captured.input())),
                now,
                job,
                Set.of()));
    job.bindGeneration(o.getId());
    jobs.flush();
    budgets.reserve(o);
    return view(o);
  }

  public View regenerate(
      UUID actor,
      Set<UUID> scope,
      UUID postId,
      UUID revisionId,
      long expected,
      String slot,
      ContentGenerationRequest.Field field,
      String key) {
    return tx.execute(
        s -> {
          var brandId =
              contentPosts
                  .findBrandIdIn(postId, scope)
                  .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
          var brand = sources.authorize(actor, brandId, scope);
          var p = contentPosts.findByIdAndBrandWorkspaceIdIn(postId, scope).orElseThrow();
          var request =
              new ContentGenerationRequest(
                  p.getStrategy().getId(),
                  p.getSourceLockVersion(),
                  List.of(slot),
                  postId,
                  revisionId,
                  expected,
                  field);
          return submitContentLocked(actor, scope, brandId, brand.getLockVersion(), key, request);
        });
  }

  public record ContentResult(
      UUID id,
      UUID operationId,
      int attempt,
      String outputChecksum,
      ContentGenerationResult output,
      List<ContentResultItem> items,
      ContentResultSource source) {}

  public record ContentResultSource(
      UUID strategyId,
      int strategyRevision,
      long strategyLockVersion,
      String strategyChecksum,
      UUID approvedBy,
      Instant approvedAt,
      ApprovedIdentitySource identity,
      ContentGenerationRequest.Field field) {}

  public record ContentResultItem(String slotId, UUID postId, UUID revisionId) {}

  public ContentResult contentResult(UUID actor, Set<UUID> scope, UUID id) {
    return tx.execute(
        s -> {
          var o =
              operations
                  .findByResultId(id)
                  .orElseThrow(() -> new ResourceNotFoundException("Content result not found"));
          authorizeRead(actor, scope, o);
          if (o.getKind() != AiGenerationOperation.Kind.CONTENT
              || o.getStatus() != AiGenerationOperation.Status.SUCCEEDED)
            throw new ResourceNotFoundException("Content result not found");
          var receipt = contentPublication.revisions(id);
          var snapshot = decode(o.getSnapshot(), AiGenerationSnapshot.class);
          var capture = snapshot.content();
          return new ContentResult(
              id,
              o.getId(),
              o.getJob().getAttempts(),
              BrandAnalysisAttempt.checksum(o.getResultJson()),
              decode(o.getResultJson(), ContentGenerationResult.class),
              receipt.stream()
                  .map(
                      r ->
                          new ContentResultItem(
                              r.getGenerationSlotId(), r.getPost().getId(), r.getId()))
                  .toList(),
              new ContentResultSource(
                  capture.strategyId(),
                  capture.strategyRevision(),
                  capture.strategyLockVersion(),
                  capture.strategyChecksum(),
                  capture.strategyApprovedBy(),
                  capture.strategyApprovedAt(),
                  snapshot.identity(),
                  capture.request().field()));
        });
  }

  private String boundedEncode(Object value) {
    var encoded = encode(value);
    if (encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
      throw new IllegalArgumentException("Content payload exceeds limit");
    return encoded;
  }

  public View status(UUID actor, Set<UUID> scope, UUID jobId) {
    return tx.execute(
        s -> {
          var o = operations.findByJobId(jobId).orElse(null);
          if (o == null) return null;
          authorizeRead(actor, scope, o);
          return view(o);
        });
  }

  public BrandAnalysisResult result(UUID actor, Set<UUID> scope, UUID id) {
    return tx.execute(
        s -> {
          var o =
              operations
                  .findByResultId(id)
                  .orElseThrow(() -> new ResourceNotFoundException("Analysis result not found"));
          authorizeRead(actor, scope, o);
          if (Set.of(AiGenerationOperation.Kind.STRATEGY, AiGenerationOperation.Kind.CONTENT)
                  .contains(o.getKind())
              || o.getStatus() != AiGenerationOperation.Status.SUCCEEDED)
            throw new ResourceNotFoundException("Analysis result not found");
          return decode(o.getResultJson(), BrandAnalysisResult.class);
        });
  }

  private void authorizeRead(UUID actor, Set<UUID> scope, AiGenerationOperation o) {
    if (!scope.contains(o.getWorkspaceId())) throw new ResourceNotFoundException("Job not found");
    sources.authorize(actor, o.getBrandId(), Set.of(o.getWorkspaceId()));
  }

  private View view(AiGenerationOperation o) {
    return new View(
        o.getJob().getId(),
        o.getId(),
        o.getKind().name(),
        o.getStatus().name(),
        o.getSubmittedAt(),
        o.getDeadline(),
        o.getCompletedAt(),
        o.getResultId(),
        o.getResultId() == null
            ? null
            : (o.getKind() == AiGenerationOperation.Kind.STRATEGY
                    ? "/api/v1/strategies/"
                    : o.getKind() == AiGenerationOperation.Kind.CONTENT
                        ? "/api/v1/content-results/"
                        : "/api/v1/analysis-results/")
                + o.getResultId(),
        o.getJob().getAttempts(),
        errorCode(o));
  }

  private String errorCode(AiGenerationOperation o) {
    if (o.getStatus() == AiGenerationOperation.Status.SUCCEEDED
        || o.getStatus() == AiGenerationOperation.Status.RUNNING
        || o.getStatus() == AiGenerationOperation.Status.QUEUED) return null;
    if (o.getStatus() == AiGenerationOperation.Status.EXPIRED) return "DEADLINE_EXPIRED";
    return attempts
        .findByOperationIdAndAttemptNumber(o.getId(), o.getJob().getAttempts())
        .filter(
            a ->
                a.getOutcome() != null
                    && (a.getOutcome().name().equals(o.getStatus().name())
                        || (a.getOutcome() == AiGenerationAttempt.Outcome.RETRY_SAFE
                            && (o.getStatus() == AiGenerationOperation.Status.RETRY_WAIT
                                || o.getStatus() == AiGenerationOperation.Status.FAILED))))
        .map(AiGenerationAttempt::getReason)
        .map(Enum::name)
        .orElse(
            switch (o.getStatus()) {
              case EXPIRED -> "DEADLINE_EXPIRED";
              case SUPERSEDED -> "SOURCE_OR_ACCESS_CHANGED";
              case OUTCOME_UNKNOWN -> "PROVIDER_OUTCOME_UNKNOWN";
              default -> "GENERATION_FAILED";
            });
  }

  private String encode(Object o) {
    try {
      return json.writeValueAsString(o);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid generation snapshot");
    }
  }

  private <T> T decode(String s, Class<T> type) {
    try {
      if (s == null || s.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536)
        throw new IllegalArgumentException();
      return json.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid generation snapshot");
    }
  }

  private AiGenerationSources.Capture capture(AiGenerationOperation o) {
    if (!BrandAnalysisAttempt.checksum(o.getSnapshot()).equals(o.getSnapshotChecksum()))
      throw new IllegalStateException("Corrupt generation snapshot");
    var c =
        sources.capture(
            o.getActorId(),
            o.getBrandId(),
            Set.of(o.getWorkspaceId()),
            o.getKind(),
            o.getSubmittedAt(),
            decode(o.getSnapshot(), AiGenerationSnapshot.class).input());
    if (!c.admissions().equals(o.getAdmissions()))
      throw new IllegalStateException("Generation source links changed");
    return c;
  }

  public List<UUID> candidates() {
    return tx.execute(s -> operations.candidates(now(), PageRequest.of(0, 100)));
  }

  public Ticket claim(UUID id) {
    try {
      return tx.execute(
          s -> {
            var initial = operations.findById(id).orElse(null);
            if (initial == null || initial.terminal()) return null;
            // Parent locks precede operation/job locks, including recovery with revoked ownership.
            sources.authorize(
                initial.getActorId(), initial.getBrandId(), Set.of(initial.getWorkspaceId()));
            em.refresh(initial);
            em.refresh(initial.getJob());
            var o = operations.locked(id).orElseThrow();
            var job = jobs.findLockedById(o.getJob().getId()).orElseThrow();
            var now = now();
            if (o.terminal()) return null;
            if (job.getStatus() == JobStatus.RUNNING) {
              if (now.isBefore(job.getLeaseExpiresAt())) return null;
              var old =
                  attempts.findByOperationIdAndAttemptNumber(id, job.getAttempts()).orElseThrow();
              if (old.getDispatchedAt() != null) {
                terminate(o, AiGenerationOperation.Status.OUTCOME_UNKNOWN);
                return null;
              }
              finishAttempt(
                  initial,
                  old,
                  AiGenerationAttempt.Outcome.RETRY_SAFE,
                  AiGenerationAttempt.Reason.LEASE_EXPIRED_BEFORE_DISPATCH);
              attempts.flush();
              job.recoverExpiredLease(now);
              if (job.getStatus() == JobStatus.DEAD) {
                o.finish(AiGenerationOperation.Status.FAILED, now);
                budgets.releaseUndispatched(o.getId());
                return null;
              }
              o.retry();
            }
            if (!now.isBefore(o.getDeadline())) {
              terminate(o, AiGenerationOperation.Status.EXPIRED);
              return null;
            }
            if (job.getNextAttemptAt().isAfter(now)) return null;
            capture(o);
            capability(o.getKind());
            if (calls.getActiveCount() >= 2 || capacity.availablePermits() == 0) return null;
            String worker = UUID.randomUUID().toString();
            Duration lease = Duration.between(now, o.getDeadline());
            if (lease.compareTo(Duration.ofSeconds(20)) > 0) lease = Duration.ofSeconds(20);
            job.claim(worker, now, lease);
            o.running();
            jobs.flush();
            operations.flush();
            attempts.saveAndFlush(
                new AiGenerationAttempt(
                    id, job.getAttempts(), worker, now, job.getLeaseExpiresAt()));
            return new Ticket(id, worker, job.getAttempts());
          });
    } catch (BrandAnalysisUnavailableException e) {
      cleanup(id, null, AiGenerationOperation.Status.FAILED);
      return null;
    } catch (org.springframework.security.access.AccessDeniedException
        | IllegalStateException
        | ResourceNotFoundException e) {
      cleanup(id, null, AiGenerationOperation.Status.SUPERSEDED);
      return null;
    }
  }

  private AiGenerationOperation current(Ticket ticket) {
    var original = operations.findById(ticket.operationId()).orElseThrow();
    capture(original);
    em.refresh(original);
    em.refresh(original.getJob());
    var o = operations.locked(ticket.operationId()).orElseThrow();
    var job = jobs.findLockedById(o.getJob().getId()).orElseThrow();
    if (o.getStatus() != AiGenerationOperation.Status.RUNNING
        || job.getStatus() != JobStatus.RUNNING
        || job.getAttempts() != ticket.attempt()
        || !ticket.worker().equals(job.getLeaseOwner())
        || !now().isBefore(job.getLeaseExpiresAt())
        || !now().isBefore(o.getDeadline()))
      throw new IllegalStateException("Stale generation lease");
    return o;
  }

  private List<EvidencePhysicalSource> physical(Ticket ticket) {
    var o = current(ticket);
    return capture(o).physical();
  }

  public void execute(Ticket ticket) {
    if (ticket == null) return;
    if (calls.getActiveCount() >= 2 || !capacity.tryAcquire()) return;
    try {
      var input = verification.execute(() -> physical(ticket), () -> dispatch(ticket));
      Object result;
      try {
        long remaining =
            tx.execute(
                s -> {
                  var o = current(ticket);
                  return Math.min(
                      20000L, Duration.between(now(), o.getJob().getLeaseExpiresAt()).toMillis());
                });
        if (remaining <= 0) throw new IllegalStateException("Expired call");
        var call =
            calls.submit(
                () ->
                    input.content() != null
                        ? contentGenerator.generate(input.content().command())
                        : input.strategy() != null
                            ? generator.generate(input.strategy())
                            : analyzer.analyze(input.analysis()));
        try {
          result = call.get(remaining, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          call.cancel(true);
          throw new IllegalStateException("Interrupted call");
        } catch (java.util.concurrent.ExecutionException e) {
          if (e.getCause() instanceof IllegalArgumentException) throw new InvalidOutput();
          if (e.getCause() instanceof BrandAnalysisUnavailableException unavailable
              && unavailable.isInvalidOutput()) throw new InvalidOutput();
          throw new IllegalStateException("Unavailable call");
        } catch (java.util.concurrent.TimeoutException e) {
          call.cancel(true);
          throw new IllegalStateException("Unavailable call");
        }
        if (result == null) throw new IllegalStateException();
        if (input.content() != null) {
          try {
            if (!(result instanceof ContentGenerationResult content))
              throw new IllegalArgumentException();
            content.validateFor(input.content().command());
            for (var item : content.items())
              input
                  .content()
                  .request()
                  .merge(input.content().command().baseline(), item.content())
                  .validateFor(item.channel());
            if (!content.warnings().contains(ContentGenerationResult.Warning.DEMONSTRATION_ONLY))
              throw new IllegalArgumentException();
            boundedEncode(content);
          } catch (RuntimeException invalid) {
            throw new InvalidOutput();
          }
        }
        if (result instanceof StrategyGenerationResult strategyResult) {
          try {
            strategyResult.requireCompletePlaybook();
          } catch (RuntimeException invalid) {
            throw new InvalidOutput();
          }
        }
      } catch (RuntimeException failure) {
        cleanup(
            ticket.operationId(),
            ticket,
            AiGenerationOperation.Status.OUTCOME_UNKNOWN,
            failure instanceof InvalidOutput
                ? AiGenerationAttempt.Reason.INVALID_OUTPUT
                : AiGenerationAttempt.Reason.PROVIDER_OUTCOME_UNKNOWN);
        return;
      }
      budgets.syntheticCompleted(ticket.operationId());
      try {
        verification.execute(
            () -> physical(ticket),
            () -> {
              publish(ticket, result);
              return Boolean.TRUE;
            });
      } catch (RuntimeException failure) {
        cleanup(
            ticket.operationId(),
            ticket,
            AiGenerationOperation.Status.SUPERSEDED,
            failure instanceof org.springframework.dao.DataAccessException
                ? AiGenerationAttempt.Reason.PUBLICATION_REJECTED
                : failure instanceof DocumentReviewUnavailableException
                    ? AiGenerationAttempt.Reason.INPUT_UNAVAILABLE
                    : failure instanceof IllegalArgumentException
                        ? AiGenerationAttempt.Reason.INVALID_OUTPUT
                        : AiGenerationAttempt.Reason.SOURCE_OR_ACCESS_CHANGED);
      }
    } catch (AlreadyDispatched duplicate) {
      // The current attempt already owns a call; duplicate delivery must not cancel it.
      return;
    } catch (GenerationBudgetExceededException failure) {
      cleanup(
          ticket.operationId(),
          ticket,
          AiGenerationOperation.Status.FAILED,
          AiGenerationAttempt.Reason.BUDGET_EXCEEDED);
    } catch (BrandAnalysisUnavailableException failure) {
      cleanup(ticket.operationId(), ticket, AiGenerationOperation.Status.FAILED);
    } catch (DocumentReviewUnavailableException failure) {
      retryBeforeDispatch(ticket);
    } catch (RuntimeException failure) {
      cleanup(ticket.operationId(), ticket, AiGenerationOperation.Status.SUPERSEDED);
    } finally {
      capacity.release();
    }
  }

  private AiGenerationSources.Input dispatch(Ticket ticket) {
    var o = current(ticket);
    capability(o.getKind());
    var c = capture(o);
    var attempt =
        attempts.findByOperationIdAndAttemptNumber(o.getId(), ticket.attempt()).orElseThrow();
    if (attempt.getDispatchedAt() != null) throw new AlreadyDispatched();
    UUID analysisId = null;
    var input = c.input();
    if (input.analysis() != null) {
      var cmd = input.analysis();
      var running =
          analyses.findByBrandIdAndStatus(o.getBrandId(), BrandAnalysisAttempt.Status.RUNNING);
      if (running.isPresent()) {
        if (now().isBefore(running.get().getDeadline()))
          throw new IllegalStateException("Analysis already running");
        running.get().finish(BrandAnalysisAttempt.Status.FAILED, now());
        analyses.flush();
      }
      var receipt =
          analyses.saveAndFlush(
              o.getKind() == AiGenerationOperation.Kind.DECLARED
                  ? new BrandAnalysisAttempt(
                      o.getBrandId(),
                      cmd.brandVersion(),
                      cmd.workspaceVersion(),
                      cmd.sourceChecksum(),
                      now())
                  : new BrandAnalysisAttempt(
                      o.getBrandId(),
                      cmd.brandVersion(),
                      cmd.workspaceVersion(),
                      cmd.sourceChecksum(),
                      now(),
                      o.getActorId(),
                      cmd.brandName(),
                      cmd.industry()));
      analysisId = receipt.getId();
      int i = 0;
      for (var source : cmd.sources())
        links.saveAndFlush(
            new BrandAnalysisSource(
                analysisId, admissions.findById(source.admissionId()).orElseThrow(), i++));
      input =
          new AiGenerationSources.Input(
              new BrandAnalysisCommand(
                  analysisId,
                  cmd.brandVersion(),
                  cmd.workspaceVersion(),
                  cmd.brandName(),
                  cmd.industry(),
                  cmd.sources()),
              null,
              null);
    }
    var dispatchedAt = budgets.dispatch(o.getId(), ticket.attempt());
    attempt.dispatch(dispatchedAt, analysisId);
    attempts.flush();
    return input;
  }

  private void publish(Ticket ticket, Object output) {
    var o = current(ticket);
    var captured = capture(o);
    var a = attempts.findByOperationIdAndAttemptNumber(o.getId(), ticket.attempt()).orElseThrow();
    if (a.getDispatchedAt() == null) throw new IllegalStateException("Missing dispatch");
    UUID resultId;
    if (o.getKind() == AiGenerationOperation.Kind.CONTENT) {
      resultId =
          contentPublication.publish(
              o,
              ticket.attempt(),
              captured.input().content(),
              (ContentGenerationResult) output,
              boundedEncode(output),
              now());
    } else if (o.getKind() == AiGenerationOperation.Kind.STRATEGY) {
      var r = (StrategyGenerationResult) output;
      r.requireCompletePlaybook();
      var cmd = captured.input().strategy();
      int version =
          strategies
              .findTopByBrandIdOrderByVersionDesc(o.getBrandId())
              .map(Strategy::getVersion)
              .orElse(0);
      if (version == Integer.MAX_VALUE) throw new IllegalStateException("Revision limit");
      var strategy =
          new Strategy(
              captured.brand(),
              version + 1,
              new PublishingCadence(r.postsPerWeek(), r.timezone(), r.bestTimes()),
              r.pillars().stream()
                  .map(p -> new ContentPillar(p.name(), p.weight(), p.description()))
                  .toList());
      var provenance =
          new StrategyGenerationProvenance(
              cmd.generationId(),
              cmd.brandVersion(),
              cmd.workspaceVersion(),
              cmd.startedAt(),
              r.provider(),
              r.modelVersion(),
              r.promptVersion(),
              r.warnings().stream()
                  .distinct()
                  .map(Enum::name)
                  .collect(java.util.stream.Collectors.joining(",")));
      strategy.replacePlaybook(r.content(), r.playbook());
      provenance.attachOutput(r.content(), r.playbook());
      provenance.attachIdentity(captured.input().identity());
      strategy.attachGeneration(provenance);
      resultId = strategies.saveAndFlush(strategy).getId();
    } else {
      var r = (BrandAnalysisResult) output;
      captured.input().analysis().validateCitations(r.citations());
      if (o.getKind() == AiGenerationOperation.Kind.EVIDENCE
          && (r.warnings().contains(BrandAnalysisResult.Warning.DECLARED_FIELDS_ONLY)
              || !r.warnings().contains(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED)))
        throw new IllegalArgumentException("Invalid evidence attribution");
      var receipt = analyses.findById(a.getAnalysisId()).orElseThrow();
      if (receipt.getStatus() != BrandAnalysisAttempt.Status.RUNNING
          || !now().isBefore(receipt.getDeadline()))
        throw new IllegalStateException("Invalid receipt");
      int pos = 0;
      for (var q : r.citations())
        citations.saveAndFlush(
            new BrandAnalysisCitation(
                receipt.getId(), pos++, q.admissionId(), q.quote(), q.fieldPath()));
      var brand = captured.brand();
      long prior = brand.getLockVersion();
      brand.updateSignature(r.toSignature());
      brands.saveAndFlush(brand);
      if (brand.getLockVersion() == prior)
        em.lock(brand, jakarta.persistence.LockModeType.PESSIMISTIC_FORCE_INCREMENT);
      receipt.succeed(
          now(),
          r.provider(),
          r.modelVersion(),
          r.promptVersion(),
          r.warnings().stream()
              .distinct()
              .map(Enum::name)
              .collect(java.util.stream.Collectors.joining(",")),
          r.contentChecksum());
      analyses.flush();
      resultId = receipt.getId();
    }
    finishAttempt(
        o, a, AiGenerationAttempt.Outcome.SUCCEEDED, AiGenerationAttempt.Reason.COMPLETED);
    attempts.flush();
    o.getJob().succeed(ticket.worker(), ticket.attempt(), now());
    o.succeed(resultId, encode(output), now());
    operations.flush();
  }

  private void terminate(AiGenerationOperation o, AiGenerationOperation.Status reason) {
    terminate(o, reason, null);
  }

  private void terminate(
      AiGenerationOperation o,
      AiGenerationOperation.Status reason,
      AiGenerationAttempt.Reason code) {
    if (o.terminal()) return;
    var a = attempts.findByOperationIdAndAttemptNumber(o.getId(), o.getJob().getAttempts());
    a.filter(at -> at.getFinishedAt() == null)
        .ifPresent(
            at ->
                finishAttempt(
                    o,
                    at,
                    AiGenerationAttempt.Outcome.valueOf(reason.name()),
                    code != null
                        ? code
                        : switch (reason) {
                          case EXPIRED -> AiGenerationAttempt.Reason.DEADLINE_EXPIRED;
                          case SUPERSEDED -> AiGenerationAttempt.Reason.SOURCE_OR_ACCESS_CHANGED;
                          case OUTCOME_UNKNOWN ->
                              AiGenerationAttempt.Reason.PROVIDER_OUTCOME_UNKNOWN;
                          default -> AiGenerationAttempt.Reason.CAPABILITY_UNAVAILABLE;
                        }));
    attempts.flush();
    if (a.isPresent() && a.get().getAnalysisId() != null) {
      var receipt = analyses.findById(a.get().getAnalysisId()).orElseThrow();
      if (receipt.getStatus() == BrandAnalysisAttempt.Status.RUNNING)
        receipt.finish(
            reason == AiGenerationOperation.Status.SUPERSEDED
                ? BrandAnalysisAttempt.Status.SUPERSEDED
                : BrandAnalysisAttempt.Status.FAILED,
            now());
    }
    if (o.getJob().getStatus() != JobStatus.DEAD) o.getJob().terminateAi(now());
    o.finish(reason, now());
    budgets.releaseUndispatched(o.getId());
  }

  private void cleanup(UUID id, Ticket ticket, AiGenerationOperation.Status reason) {
    cleanup(id, ticket, reason, null);
  }

  private void cleanup(
      UUID id,
      Ticket ticket,
      AiGenerationOperation.Status reason,
      AiGenerationAttempt.Reason code) {
    tx.executeWithoutResult(
        s -> {
          var original = operations.findById(id).orElse(null);
          if (original == null) return;
          sources.lockParents(
              original.getActorId(), original.getBrandId(), Set.of(original.getWorkspaceId()));
          em.refresh(original);
          em.refresh(original.getJob());
          var o = operations.locked(id).orElse(null);
          if (o == null || o.terminal()) return;
          var j = jobs.findLockedById(o.getJob().getId()).orElseThrow();
          if (ticket != null
              && (ticket.attempt() != j.getAttempts()
                  || !ticket.worker().equals(j.getLeaseOwner()))) return;
          terminate(o, reason, code);
        });
  }

  private void retryBeforeDispatch(Ticket ticket) {
    tx.executeWithoutResult(
        s -> {
          var original = operations.findById(ticket.operationId()).orElseThrow();
          sources.lockParents(
              original.getActorId(), original.getBrandId(), Set.of(original.getWorkspaceId()));
          em.refresh(original);
          em.refresh(original.getJob());
          var o = operations.locked(ticket.operationId()).orElseThrow();
          var j = jobs.findLockedById(o.getJob().getId()).orElseThrow();
          if (o.terminal()
              || j.getAttempts() != ticket.attempt()
              || !ticket.worker().equals(j.getLeaseOwner())) return;
          var a =
              attempts.findByOperationIdAndAttemptNumber(o.getId(), ticket.attempt()).orElseThrow();
          if (a.getDispatchedAt() != null) {
            // This executor failed before dispatch; a concurrent delivery owns the call.
            return;
          }
          if (!now().isBefore(j.getLeaseExpiresAt())) return;
          finishAttempt(
              o,
              a,
              AiGenerationAttempt.Outcome.RETRY_SAFE,
              AiGenerationAttempt.Reason.INPUT_UNAVAILABLE);
          attempts.flush();
          j.fail(
              ticket.worker(),
              ticket.attempt(),
              now(),
              "Generation input unavailable",
              new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(4)),
              false);
          if (j.getStatus() == JobStatus.DEAD) {
            o.finish(AiGenerationOperation.Status.FAILED, now());
            budgets.releaseUndispatched(o.getId());
          } else o.retry();
        });
  }

  private final AiGenerationTelemetry telemetry;

  private void finishAttempt(
      AiGenerationOperation o,
      AiGenerationAttempt a,
      AiGenerationAttempt.Outcome outcome,
      AiGenerationAttempt.Reason reason) {
    a.finish(outcome, reason, now());
    UUID job = o.getJob().getId(), operation = o.getId();
    int number = a.getAttemptNumber();
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            telemetry.completed(job, operation, number, outcome, reason);
          }
        });
  }

  public void runOnce() {
    for (var id : candidates()) {
      var ticket = claim(id);
      if (ticket != null) {
        execute(ticket);
        return;
      }
    }
  }
}
