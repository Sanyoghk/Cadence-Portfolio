package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.core.env.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal accounting boundary. No HTTP settlement or caller-selected actor/cost. */
@Service
public class GenerationBudgets {
  private final GenerationBudgetMutexRepository mutex;
  private final GenerationReservationRepository reservations;
  private final AiGenerationSources sources;
  private final Environment environment;
  private final GenerationBudgetLedger ledger;
  private final Clock clock;
  private final TransactionTemplate tx;

  public GenerationBudgets(
      GenerationBudgetMutexRepository mutex,
      GenerationReservationRepository reservations,
      AiGenerationSources sources,
      Environment environment,
      GenerationBudgetLedger ledger,
      Clock clock,
      PlatformTransactionManager manager) {
    this.mutex = mutex;
    this.reservations = reservations;
    this.sources = sources;
    this.environment = environment;
    this.ledger = ledger;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    tx.setTimeout(10);
  }

  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }

  private void lock() {
    mutex.lock().orElseThrow(BrandAnalysisUnavailableException::new);
  }

  private long limit(String name, long maximum) {
    String raw = environment.getProperty("cadence.ai-budget." + name);
    try {
      long value = Long.parseLong(raw);
      if (value < 1 || value > maximum) throw new IllegalArgumentException();
      return value;
    } catch (RuntimeException e) {
      throw new BrandAnalysisUnavailableException();
    }
  }

  private void available(String kind) {
    String adapter =
        kind.equals("STRATEGY")
            ? "cadence.strategy-generation.adapter"
            : kind.equals("CONTENT")
                ? "cadence.content-generation.adapter"
                : "cadence.brand-analysis.adapter";
    if (environment.acceptsProfiles(Profiles.of("prod"))
        || !environment.acceptsProfiles(Profiles.of("local", "test"))
        || !"synthetic".equals(environment.getProperty("cadence.ai-budget.mode"))
        || !"fake".equals(environment.getProperty(adapter)))
      throw new BrandAnalysisUnavailableException();
  }

  // Parameters choose values only; column predicates are internal constants.
  private void check(UUID actor, UUID workspace, boolean dispatch) {
    Instant start = now().truncatedTo(ChronoUnit.DAYS);
    for (var dimension : GenerationBudgetLedger.Scope.values()) {
      String scope = dimension.name().toLowerCase(Locale.ROOT);
      var totals = ledger.totals(dimension, "SYNTHETIC", actor, workspace, start);
      BigDecimal spend = totals.spend();
      long upper = dispatch ? 0 : limit("upper", 1_000_000_000L);
      if ((!dispatch && totals.retained() >= limit(scope + "-retained", 1_000_000))
          || spend
                  .add(BigDecimal.valueOf(upper))
                  .compareTo(BigDecimal.valueOf(limit(scope + "-spend", 1_000_000_000_000L)))
              > 0
          || totals.overruns() > 0
          || (!dispatch && totals.queued() >= limit(scope + "-queued", 10000))
          || (dispatch && totals.running() >= limit(scope + "-running", 1000)))
        throw new GenerationBudgetExceededException();
    }
  }

  private String policySnapshot() {
    var text = new StringBuilder("upper=").append(limit("upper", 1_000_000_000L));
    for (String scope : List.of("global", "account", "workspace")) {
      for (String metric : List.of("spend", "queued", "running", "retained")) {
        long max =
            switch (metric) {
              case "spend" -> 1_000_000_000_000L;
              case "queued" -> 10000;
              case "running" -> 1000;
              default -> 1_000_000;
            };
        text.append(';')
            .append(scope)
            .append('-')
            .append(metric)
            .append('=')
            .append(limit(scope + "-" + metric, max));
      }
    }
    return text.toString();
  }

  /** Called in submission transaction AFTER parents/operation/job. */
  public void reserve(AiGenerationOperation operation) {
    reserve(
        operation.getId(),
        operation.getId(),
        operation.getActorId(),
        operation.getWorkspaceId(),
        operation.getBrandId(),
        operation.getKind().name(),
        operation.getRequestDigest(),
        operation.getDeadline());
  }

  private GenerationReservation reserve(
      UUID request,
      UUID operation,
      UUID actor,
      UUID workspace,
      UUID brand,
      String kind,
      String digest,
      Instant deadline) {
    available(kind);
    lock();
    var existing = reservations.findByRequestId(request);
    if (existing.isPresent()) {
      var r = existing.get();
      if (!r.getActorId().equals(actor)
          || !r.getWorkspaceId().equals(workspace)
          || !r.getBrandId().equals(brand)
          || !Objects.equals(r.getOperationId(), operation)
          || !r.getRequestDigest().equals(digest)
          || !r.getKind().equals(kind)
          || !r.getDeadline().equals(deadline))
        throw new IllegalStateException("Reservation binding conflict");
      return r;
    }
    if (!now().isBefore(deadline)) throw new IllegalStateException("Generation deadline expired");
    check(actor, workspace, false);
    var start = now().truncatedTo(ChronoUnit.DAYS);
    String policy = environment.getProperty("cadence.ai-budget.policy-version");
    if (policy == null || !policy.matches("[A-Za-z0-9._:-]{1,64}"))
      throw new BrandAnalysisUnavailableException();
    return reservations.saveAndFlush(
        new GenerationReservation(
            request,
            operation,
            actor,
            workspace,
            brand,
            kind,
            digest,
            policy,
            "synthetic-fixed-v1",
            policySnapshot(),
            start,
            start.plus(1, ChronoUnit.DAYS),
            deadline,
            limit("upper", 1_000_000_000L)));
  }

  /** Same transaction as durable attempt dispatch witness. */
  public Instant dispatch(UUID request, int attempt) {
    lock();
    var r =
        reservations.findByRequestId(request).orElseThrow(BrandAnalysisUnavailableException::new);
    if (!r.getNamespace().equals("SYNTHETIC")) throw new BrandAnalysisUnavailableException();
    available(r.getKind());
    check(r.getActorId(), r.getWorkspaceId(), true);
    r.dispatch(attempt, now());
    reservations.flush();
    return r.getDispatchedAt();
  }

  /** No execution occurred; unknown reservations are deliberately retained. */
  public void releaseUndispatched(UUID request) {
    lock();
    reservations
        .findByRequestId(request)
        .filter(r -> r.getState() == GenerationReservation.State.RESERVED)
        .ifPresent(GenerationReservation::release);
    reservations.flush();
  }

  /**
   * Trusted synthetic adapter completion, persisted even when later content publication is
   * rejected.
   */
  public void syntheticCompleted(UUID request) {
    tx.executeWithoutResult(
        s -> {
          lock();
          var r =
              reservations
                  .findByRequestId(request)
                  .orElseThrow(BrandAnalysisUnavailableException::new);
          settleLocked(
              r,
              new GenerationUsage(
                  GenerationUsage.Origin.SYNTHETIC,
                  "SYNTHETIC_CREDIT",
                  0,
                  1,
                  BrandAnalysisAttempt.checksum(
                      "synthetic-fixed-v1\n" + request + "\n" + r.getAttemptNumber())));
        });
  }

  /** Internal trusted reconciliation port; never changes operation/content status. */
  public void reconcile(UUID request, GenerationUsage evidence) {
    tx.executeWithoutResult(
        s -> {
          lock();
          settleLocked(
              reservations
                  .findByRequestId(request)
                  .orElseThrow(BrandAnalysisUnavailableException::new),
              evidence);
        });
  }

  private void settleLocked(GenerationReservation r, GenerationUsage evidence) {
    r.settle(evidence, now());
    reservations.flush();
  }

  /** Legacy calls have unique server request IDs, not implicit client idempotency. */
  public void dispatchSync(
      UUID request, UUID brand, Set<UUID> scope, String kind, String digest, Instant deadline) {
    var authentication =
        org.springframework.security.core.context.SecurityContextHolder.getContext()
            .getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal()
            instanceof com.cadence.security.CadencePrincipal principal))
      throw new org.springframework.security.access.AccessDeniedException("Generation denied");
    tx.executeWithoutResult(
        s -> {
          var b = sources.authorize(principal.userId(), brand, scope);
          Instant originalDeadline = deadline;
          if (!kind.equals("STRATEGY")) {
            originalDeadline =
                ledger
                    .analysisDeadline(
                        request,
                        brand,
                        digest,
                        b.getLockVersion(),
                        b.getWorkspace().getLockVersion())
                    .orElseThrow(() -> new IllegalStateException("Generation source changed"));
          }

          reserve(
              request,
              null,
              principal.userId(),
              b.getWorkspace().getId(),
              brand,
              kind,
              digest,
              originalDeadline);
          dispatch(request, 1);
        });
  }
}
