package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

@Service
public class RecommendationService {
  public record Result(RecommendationDetails details, boolean created) {}

  public static final RecommendationEngine.Attribution RULE =
      new RecommendationEngine.Attribution(
          "HEURISTIC", "pillar-shift-v1", "cadence-rules", null, null);
  private final RecommendationSources sources;
  private final RecommendationKeyRepository keys;
  private final RecommendationInsightRepository recommendations;
  private final RecommendationResolutionRepository resolutions;
  private final StrategyRepository strategies;
  private final IdentitySourceVerification verification;
  private final RecommendationEngine engine;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final Semaphore slots = new Semaphore(2);
  private final ExecutorService executor =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.MILLISECONDS,
          new SynchronousQueue<>(),
          r -> {
            var t = new Thread(r, "recommendation-rule");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public RecommendationService(
      RecommendationKeyRepository keys,
      RecommendationSources sources,
      RecommendationInsightRepository recommendations,
      RecommendationResolutionRepository resolutions,
      StrategyRepository strategies,
      IdentitySourceVerification verification,
      RecommendationEngine engine,
      ObjectMapper mapper,
      Clock clock,
      PlatformTransactionManager manager) {
    this.sources = sources;
    this.keys = keys;
    this.recommendations = recommendations;
    this.resolutions = resolutions;
    this.strategies = strategies;
    this.verification = verification;
    this.engine = engine;
    this.mapper = mapper;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    tx.setTimeout(5);
  }

  @jakarta.annotation.PreDestroy
  void close() {
    executor.shutdownNow();
  }

  public Result generate(
      UUID actor, UUID brand, Set<UUID> scope, String key, RecommendationSources.Request request) {
    String kd = key(key), digest = hash("recommendation-request-v1|" + brand + "|" + json(request));
    var admission = new AtomicReference<UUID>();
    var replay =
        tx.execute(
            t -> {
              var b = sources.authorize(actor, brand, scope);
              admission.set(admit(actor, b, "GENERATE", kd, digest));
              return recommendations
                  .findByActorIdAndWorkspaceIdAndKeyDigest(actor, b.getWorkspace().getId(), kd)
                  .map(
                      r -> {
                        same(r.getRequestDigest(), digest);
                        return new Result(details(r), false);
                      })
                  .orElse(null);
            });
    if (replay != null) return replay;
    if (!slots.tryAcquire()) throw new RecommendationUnavailableException();
    try {
      var deadline = clock.instant().plusSeconds(30);
      var held = new AtomicReference<RecommendationSources.Capture>();
      verification.execute(
          () -> capture(actor, brand, scope, request, held, null),
          () -> {
            if (recommendations.countByBrandId(brand) >= 1000)
              throw new IllegalStateException("Recommendation retained quota reached");
            return true;
          });
      var frozen = held.get().command();
      if (!RULE.equals(engine.attribution())) throw new RecommendationUnavailableException();
      RecommendationProposal output;
      Future<RecommendationProposal> f;
      try {
        f =
            executor.submit(
                () -> {
                  if (TransactionSynchronizationManager.isActualTransactionActive())
                    throw new IllegalStateException("Rule I/O inside transaction");
                  return engine.recommend(RecommendationInput.from(frozen));
                });
      } catch (RejectedExecutionException failure) {
        throw new RecommendationUnavailableException();
      }
      try {
        output = f.get(3, TimeUnit.SECONDS);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        f.cancel(true);
        throw new RecommendationUnavailableException();
      } catch (ExecutionException | TimeoutException failure) {
        f.cancel(true);
        throw new RecommendationUnavailableException();
      }
      frozen.validate(output);
      if (!RULE.equals(engine.attribution())) throw new RecommendationUnavailableException();
      return verification.execute(
          () -> capture(actor, brand, scope, request, held, frozen),
          () -> {
            if (!clock.instant().isBefore(deadline))
              throw new IllegalStateException("Recommendation deadline exceeded");
            var c = held.get();
            var old =
                recommendations.findByActorIdAndWorkspaceIdAndKeyDigest(
                    actor, c.brand().getWorkspace().getId(), kd);
            if (old.isPresent()) {
              same(old.get().getRequestDigest(), digest);
              return new Result(details(old.get()), false);
            }
            if (recommendations.countByBrandId(brand) >= 1000)
              throw new IllegalStateException("Recommendation retained quota reached");
            String snapshot = json(frozen);
            var links =
                frozen.sources().stream()
                    .map(
                        s ->
                            new RecommendationMetricLink(
                                s.observationId(), s.attributionId(), s.valueChecksum()))
                    .toList();
            var r =
                recommendations.saveAndFlush(
                    new RecommendationInsight(
                        admission.get(),
                        c.brand(),
                        c.strategy(),
                        actor,
                        kd,
                        digest,
                        frozen.strategyVersion(),
                        frozen.strategyChecksum(),
                        snapshot,
                        output,
                        clock.instant(),
                        request.query(),
                        links));
            return new Result(details(r), true);
          });
    } finally {
      slots.release();
    }
  }

  private List<EvidencePhysicalSource> capture(
      UUID actor,
      UUID brand,
      Set<UUID> scope,
      RecommendationSources.Request request,
      AtomicReference<RecommendationSources.Capture> held,
      RecommendationCommand frozen) {
    var c = sources.capture(actor, brand, scope, request);
    var expected = frozen != null ? frozen : held.get() == null ? null : held.get().command();
    if (expected != null && !expected.equals(c.command()))
      throw new IllegalStateException("Captured recommendation source changed");
    held.set(c);
    return c.physical();
  }

  public Result resolve(
      UUID actor,
      UUID id,
      Set<UUID> scope,
      String key,
      long expectedVersion,
      RecommendationResolution.Action action) {
    if (expectedVersion != 0 || action == null)
      throw new IllegalStateException("Stale recommendation version");
    String kd = key(key),
        digest = hash("recommendation-resolution-v1|" + id + "|" + action + "|" + expectedVersion);
    var admission = new AtomicReference<UUID>();
    var replay =
        tx.execute(
            t -> {
              var r = scoped(actor, id, scope);
              admission.set(admit(actor, r.getBrand(), action.name(), kd, digest));
              var old =
                  resolutions.findByActorIdAndWorkspaceIdAndActionAndKeyDigest(
                      actor, r.getWorkspaceId(), action, kd);
              if (old.isPresent()) {
                same(old.get().getRequestDigest(), digest);
                return new Result(details(r), false);
              }
              if (resolutions.findByRecommendationId(id).isPresent())
                throw new IllegalStateException("Recommendation already resolved");
              return null;
            });
    if (replay != null) return replay;
    if (!slots.tryAcquire()) throw new RecommendationUnavailableException();
    try {
      var original =
          tx.execute(
              t -> {
                var r = scoped(actor, id, scope);
                return command(r);
              });
      var request =
          new RecommendationSources.Request(
              original.strategyId(),
              original.strategyVersion(),
              original.query().fromDate(),
              original.query().untilDate(),
              original.query().timezone(),
              original.query().platform());
      var held = new AtomicReference<RecommendationSources.Capture>();
      return verification.execute(
          () -> capture(actor, original.brandId(), scope, request, held, original),
          () -> {
            var r = scoped(actor, id, scope);
            var old =
                resolutions.findByActorIdAndWorkspaceIdAndActionAndKeyDigest(
                    actor, r.getWorkspaceId(), action, kd);
            if (old.isPresent()) {
              same(old.get().getRequestDigest(), digest);
              return new Result(details(r), false);
            }
            if (resolutions.findByRecommendationId(id).isPresent())
              throw new IllegalStateException("Recommendation already resolved");
            Strategy draft = null;
            if (action == RecommendationResolution.Action.APPLY) {
              var source = held.get().strategy();
              if (source.getVersion() == Integer.MAX_VALUE)
                throw new IllegalStateException("Strategy version limit reached");
              draft = source.createDraftVersion(source.getVersion() + 1);
              draft.replacePlaybook(
                  original.expected().apply(original.content(), original.playbook()),
                  original.playbook());
              draft.recordHumanEdit(actor, clock.instant());
              strategies.saveAndFlush(draft);
            }
            resolutions.saveAndFlush(
                new RecommendationResolution(
                    admission.get(), r, actor, action, kd, digest, draft, clock.instant()));
            return new Result(details(r), true);
          });
    } catch (IllegalStateException changed) {
      // A concurrent same-key apply can create the new latest DRAFT during preflight.
      // Only an existing authorized identical receipt converts this into a replay.
      var replayAfterRace =
          tx.execute(
              t -> {
                var r = scoped(actor, id, scope);
                return resolutions
                    .findByActorIdAndWorkspaceIdAndActionAndKeyDigest(
                        actor, r.getWorkspaceId(), action, kd)
                    .map(
                        v -> {
                          same(v.getRequestDigest(), digest);
                          return new Result(details(r), false);
                        })
                    .orElse(null);
              });
      if (replayAfterRace != null) return replayAfterRace;
      throw changed;
    } finally {
      slots.release();
    }
  }

  public RecommendationDetails read(UUID actor, UUID id, Set<UUID> scope) {
    return tx.execute(t -> details(scoped(actor, id, scope)));
  }

  /** Internal historical projection inside the caller's authorized parent-locked transaction. */
  RecommendationDetails reportHistory(UUID actor, UUID id, Set<UUID> scope) {
    if (!TransactionSynchronizationManager.isActualTransactionActive())
      throw new IllegalStateException("Report capture requires transaction");
    return details(scoped(actor, id, scope));
  }

  public Page<RecommendationSummary> list(
      UUID actor, UUID brand, Set<UUID> scope, int page, int size) {
    if (page < 0 || page > 1000 || size < 1 || size > 50)
      throw new IllegalArgumentException("Invalid recommendation pagination");
    return tx.execute(
        t -> {
          sources.authorize(actor, brand, scope);
          return recommendations
              .findByBrandId(
                  brand,
                  PageRequest.of(
                      page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id"))))
              .map(
                  r -> {
                    var action =
                        resolutions
                            .findByRecommendationId(r.getId())
                            .map(
                                v -> v.getAction().name().equals("APPLY") ? "APPLIED" : "DISMISSED")
                            .orElse("REVIEWABLE");
                    return new RecommendationSummary(
                        r.getId(),
                        r.getRequestId(),
                        r.getBrand().getId(),
                        r.getStrategy().getId(),
                        action,
                        new RecommendationEngine.Attribution(
                            r.getOrigin(),
                            r.getRuleVersion(),
                            r.getProvider(),
                            r.getModelVersion(),
                            r.getPromptVersion()),
                        r.getConfidence(),
                        r.getCapturedAt());
                  });
        });
  }

  private RecommendationInsight scoped(UUID actor, UUID id, Set<UUID> scope) {
    var b = recommendations.findBrandIdIn(id, scope).orElseThrow(RecommendationSources::missing);
    sources.authorize(actor, b, scope);
    return recommendations.findById(id).orElseThrow(RecommendationSources::missing);
  }

  private RecommendationCommand command(RecommendationInsight r) {
    if (!hash(r.getSnapshot()).equals(r.getSnapshotChecksum()))
      throw new IllegalStateException("Corrupt recommendation snapshot");
    try {
      var c = mapper.readValue(r.getSnapshot(), RecommendationCommand.class);
      if (!c.brandId().equals(r.getBrand().getId())
          || !c.strategyId().equals(r.getStrategy().getId())
          || c.strategyVersion() != r.getSourceVersion()
          || !c.strategyChecksum().equals(r.getSourceChecksum())
          || !BrandAnalysisAttempt.checksum(c.playbook().canonical(c.content()))
              .equals(r.getSourceChecksum())
          || !c.expected()
              .citations()
              .equals(
                  r.getMetrics().stream().map(RecommendationMetricLink::getObservationId).toList()))
        throw new IllegalStateException("Corrupt recommendation source binding");
      c.validate(c.expected());
      return c;
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalStateException("Corrupt recommendation snapshot");
    }
  }

  private RecommendationDetails details(RecommendationInsight r) {
    var c = command(r);
    var savedResolution = resolutions.findByRecommendationId(r.getId());
    savedResolution
        .filter(v -> v.getAction() == RecommendationResolution.Action.APPLY)
        .ifPresent(
            v -> {
              var expectedChecksum =
                  BrandAnalysisAttempt.checksum(
                      c.playbook().canonical(c.expected().apply(c.content(), c.playbook())));
              if (!expectedChecksum.equals(v.getDraftChecksum()))
                throw new IllegalStateException("Corrupt recommendation application receipt");
            });
    var resolution =
        savedResolution
            .map(
                v ->
                    new RecommendationDetails.Resolution(
                        v.getRequestId(),
                        v.getId(),
                        v.getAction().name(),
                        v.getActorId(),
                        v.getResolvedAt(),
                        v.getDraft() == null ? null : v.getDraft().getId(),
                        v.getDraftChecksum()))
            .orElse(null);
    return new RecommendationDetails(
        r.getWindowFrom(),
        r.getWindowUntil(),
        r.getRulesVersion(),
        r.getRequestId(),
        r.getId(),
        r.getBrand().getId(),
        r.getWorkspaceId(),
        r.getActorId(),
        r.getLockVersion(),
        resolution == null
            ? "REVIEWABLE"
            : resolution.action().equals("APPLY") ? "APPLIED" : "DISMISSED",
        new RecommendationEngine.Attribution(
            r.getOrigin(),
            r.getRuleVersion(),
            r.getProvider(),
            r.getModelVersion(),
            r.getPromptVersion()),
        r.getConfidence(),
        null,
        null,
        "HYPOTHESIS",
        r.getExplanation(),
        List.of(
            "HUMAN_INPUT_NOT_PROVIDER_VERIFIED",
            "UNCALIBRATED_HEURISTIC",
            "NO_CAUSAL_LIFT",
            "EXPOSURE_AGE_UNKNOWN",
            "CURRENT_ELIGIBILITY_REQUIRES_RECHECK"),
        c,
        r.getSnapshotChecksum(),
        r.getCapturedAt(),
        resolution);
  }

  private String json(Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      throw new IllegalArgumentException("Invalid recommendation input");
    }
  }

  private UUID admit(UUID actor, Brand brand, String operation, String key, String digest) {
    var old =
        keys.findByActorIdAndWorkspaceIdAndOperationAndKeyDigest(
            actor, brand.getWorkspace().getId(), operation, key);
    if (old.isPresent()) {
      same(old.get().getRequestDigest(), digest);
      return old.get().getId();
    }
    if (keys.countByBrandId(brand.getId()) >= 10000)
      throw new IllegalStateException("Recommendation key quota reached");
    return keys.saveAndFlush(
            new RecommendationKey(
                actor, brand.getWorkspace().getId(), brand.getId(), operation, key, digest))
        .getId();
  }

  static String hash(String value) {
    return MetricRow.sha(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  static String key(String value) {
    if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("Invalid idempotency key");
    return hash(value);
  }

  private void same(String actual, String expected) {
    if (!actual.equals(expected)) throw new IllegalStateException("Idempotency payload conflict");
  }
}
