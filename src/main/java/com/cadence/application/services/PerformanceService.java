package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.PerformanceReader;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PerformanceService {
  private final AiGenerationSources parents;
  private final PerformanceReader reader;
  private final PerformanceCalculator calculator;
  private final MetricObservationRepository observations;
  private final MetricAttributionRepository attributions;
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;
  private final Clock clock;
  private final TransactionTemplate tx;
  private final java.util.concurrent.Semaphore slots = new java.util.concurrent.Semaphore(2);

  public PerformanceService(
      AiGenerationSources parents,
      PerformanceReader reader,
      PerformanceCalculator calculator,
      MetricObservationRepository observations,
      MetricAttributionRepository attributions,
      ContentPostRepository posts,
      PostRevisionRepository revisions,
      Clock clock,
      PlatformTransactionManager manager) {
    this.parents = parents;
    this.reader = reader;
    this.calculator = calculator;
    this.observations = observations;
    this.attributions = attributions;
    this.posts = posts;
    this.revisions = revisions;
    this.clock = clock;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    tx.setTimeout(5);
  }

  private Brand authorize(UUID actor, UUID brand, Set<UUID> scope) {
    var b = parents.authorize(actor, brand, scope);
    if (b.getStatus() != BrandStatus.ACTIVE
        || b.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Performance parent archived");
    return b;
  }

  public PerformanceReport read(UUID actor, UUID brand, Set<UUID> scope, PerformanceQuery q) {
    if (q == null) throw new IllegalArgumentException("Performance query required");
    if (!slots.tryAcquire()) throw new PerformanceUnavailableException();
    try {
      return tx.execute(
          s -> {
            var b = authorize(actor, brand, scope);
            var now = clock.instant();
            var rows =
                q.windows().isEmpty()
                    ? List.<PerformanceSample>of()
                    : reader.read(brand, b.getWorkspace().getId(), q);
            return calculator.calculate(brand, b.getWorkspace().getId(), q, now, rows);
          });
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionTimedOutException failure) {
      throw new PerformanceUnavailableException();
    } finally {
      slots.release();
    }
  }

  private MetricObservation scoped(UUID actor, Set<UUID> scope, UUID id) {
    var t =
        observations
            .target(id)
            .filter(v -> scope.contains(v.getWorkspaceId()))
            .orElseThrow(PerformanceService::missing);
    authorize(actor, t.getBrandId(), scope);
    posts.findLocked(t.getPostId(), t.getBrandId()).orElseThrow(PerformanceService::missing);
    return observations.findById(id).orElseThrow(PerformanceService::missing);
  }

  public MetricAttributionDetails attribute(
      UUID actor,
      Set<UUID> scope,
      UUID observationId,
      UUID revision,
      long version,
      String pillar,
      int expectedAttributionVersion) {
    if (expectedAttributionVersion < 0 || expectedAttributionVersion > 100)
      throw new IllegalArgumentException("Invalid attribution version");
    return tx.execute(
        s -> {
          var o = scoped(actor, scope, observationId);
          var r =
              revisions
                  .findByIdAndPostId(revision, o.getPost().getId())
                  .orElseThrow(PerformanceService::missing);
          var proposed = new MetricAttribution(o, r, version, pillar, actor, clock.instant());
          var old =
              attributions
                  .findTopByObservationIdOrderByAttributionNumberDesc(observationId)
                  .orElse(null);
          if (old != null) {
            if (old.getRevision().getId().equals(revision)
                && old.getRevisionVersion() == version
                && Objects.equals(old.getPillarId(), proposed.getPillarId())
                && (expectedAttributionVersion == old.getAttributionNumber()
                    || expectedAttributionVersion == old.getAttributionNumber() - 1))
              return MetricAttributionDetails.from(old);
          }
          if (expectedAttributionVersion != (old == null ? 0 : old.getAttributionNumber()))
            throw new IllegalStateException("Stale attribution version");
          var current =
              observations
                  .findTopByPostIdAndObservedAtOrderByRevisionNumberDesc(
                      o.getPost().getId(), o.getObservedAt())
                  .orElseThrow();
          if (!current.getId().equals(o.getId()))
            throw new IllegalStateException("Obsolete observation attribution denied");
          if (attributions.countByObservationPostBrandId(o.getPost().getBrand().getId()) >= 100000)
            throw new IllegalStateException("Retained attribution quota reached");
          return MetricAttributionDetails.from(
              attributions.saveAndFlush(
                  old == null
                      ? proposed
                      : new MetricAttribution(o, r, version, pillar, actor, clock.instant(), old)));
        });
  }

  public MetricAttributionDetails attribution(
      UUID actor, Set<UUID> scope, UUID observationId, Integer number) {
    if (number != null && (number < 1 || number > 100))
      throw new IllegalArgumentException("Invalid attribution history version");
    return tx.execute(
        s -> {
          scoped(actor, scope, observationId);
          return MetricAttributionDetails.from(
              (number == null
                      ? attributions.findTopByObservationIdOrderByAttributionNumberDesc(
                          observationId)
                      : attributions.findByObservationIdAndAttributionNumber(observationId, number))
                  .orElseThrow(PerformanceService::missing));
        });
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Performance resource not found");
  }
}
