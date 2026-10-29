package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.ports.PerformanceReader;
import com.cadence.application.repositories.StrategyRepository;
import com.cadence.application.results.*;
import com.cadence.application.results.CreatorReportSnapshot.*;
import com.cadence.domain.*;
import java.time.*;
import java.time.zone.ZoneRulesProvider;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class CreatorReportSources {
  private final AiGenerationSources parents;
  private final StrategyRepository strategies;
  private final PerformanceReader reader;
  private final PerformanceCalculator calculator;
  private final RecommendationService recommendations;

  public CreatorReportSources(
      AiGenerationSources parents,
      StrategyRepository strategies,
      PerformanceReader reader,
      PerformanceCalculator calculator,
      RecommendationService recommendations) {
    this.parents = parents;
    this.strategies = strategies;
    this.reader = reader;
    this.calculator = calculator;
    this.recommendations = recommendations;
  }

  public Brand authorize(UUID actor, UUID brand, Set<UUID> scope) {
    var b = parents.authorize(actor, brand, scope);
    if (b.getStatus() != BrandStatus.ACTIVE
        || b.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Report parent archived");
    return b;
  }

  public void lock(UUID actor, UUID brand, Set<UUID> scope) {
    parents.lockParents(actor, brand, scope);
  }

  public static String checksum(Strategy s) {
    return BrandAnalysisAttempt.checksum(s.getPlaybook().canonical(StrategyContent.from(s)));
  }

  public Strategy source(UUID brand, UUID id, long version, String checksum) {
    var s =
        strategies.findLockedByIdAndBrandId(id, brand).orElseThrow(CreatorReportSources::missing);
    if (s.getContentFormatVersion() != 2
        || s.getApprovedAt() == null
        || s.getApprovedBy() == null
        || (s.getStatus() != ApprovalStatus.APPROVED && s.getStatus() != ApprovalStatus.LOCKED)
        || s.getLockVersion() != version
        || (checksum != null && !checksum(s).equals(checksum)))
      throw new IllegalStateException("Report Strategy source changed");
    s.validateContent();
    return s;
  }

  public CreatorReportSnapshot capture(
      UUID actor,
      Brand brand,
      Strategy s,
      Set<UUID> scope,
      CreatorReportRequest request,
      Instant now) {
    var rows = reader.read(brand.getId(), brand.getWorkspace().getId(), request.query(0));
    if (rows.size() > 1000) throw new IllegalStateException("Report candidate cap exceeded");
    rows = rows.stream().filter(v -> v.strategyId().equals(s.getId())).toList();
    var first =
        calculator.calculate(
            brand.getId(), brand.getWorkspace().getId(), request.query(0), now, rows);
    if (first.totalGroups() > 1000) throw new IllegalStateException("Report group cap exceeded");
    var groups = new ArrayList<Group>();
    groups.addAll(first.items().stream().map(Group::from).toList());
    for (int p = 1; p * 50 < first.totalGroups(); p++)
      groups.addAll(
          calculator
              .calculate(brand.getId(), brand.getWorkspace().getId(), request.query(p), now, rows)
              .items()
              .stream()
              .map(Group::from)
              .toList());
    var observations = groups.stream().flatMap(g -> g.sources().stream()).toList();
    var advice = new ArrayList<Advice>();
    for (var id : request.recommendationIds()) {
      var r = recommendations.reportHistory(actor, id, scope);
      var c = r.capture();
      if (!r.brandId().equals(brand.getId()) || !c.strategyId().equals(s.getId())) throw missing();
      if (c.query().platform() != request.platform()
          || !c.query().from().equals(request.query(0).from())
          || !c.query().until().equals(request.query(0).until())
          || !c.query().timezone().equals(request.timezone())
          || !c.strategyChecksum().equals(checksum(s)))
        throw new IllegalStateException("Recommendation window or source mismatch");
      var support = c.sources().stream().map(Observation::from).toList();
      advice.add(
          new Advice(
              r.id(),
              r.snapshotChecksum(),
              r.capturedAt(),
              new Source(
                  c.strategyId(), c.strategyRevision(), c.strategyVersion(), c.strategyChecksum()),
              r.attribution(),
              r.confidence(),
              r.claimType(),
              r.explanation(),
              r.uncertainty(),
              c.expected().fromPillar(),
              c.expected().toPillar(),
              c.expected().points(),
              observations.containsAll(support)
                  ? "MATCHES_CAPTURED_METRICS"
                  : "HISTORICAL_DIFFERS_FROM_CAPTURED_METRICS",
              support));
    }
    var caveats = new ArrayList<>(first.caveats());
    caveats.add("IMMUTABLE_HISTORICAL_SNAPSHOT_NOT_CURRENT_ANALYTICS");
    caveats.add("NO_CURRENT_RECOMMENDATION_ELIGIBILITY_OR_CAUSAL_CLAIM");
    if (first.selectedRows() == 0) caveats.add("NO_DATA");
    return new CreatorReportSnapshot(
        "creator-report-v1",
        brand.getId(),
        brand.getWorkspace().getId(),
        new Source(s.getId(), s.getVersion(), s.getLockVersion(), checksum(s)),
        request,
        now,
        ZoneRulesProvider.getVersions(request.timezone()).lastKey(),
        first.formulaVersion(),
        first.candidateRows(),
        first.selectedRows(),
        first.duplicateSnapshots(),
        first.incompatibleWindows(),
        first.skippedLocalDays(),
        caveats,
        groups,
        advice);
  }

  static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Report resource not found");
  }
}
