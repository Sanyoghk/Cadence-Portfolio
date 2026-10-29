package com.cadence.application.services;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.PerformanceReport.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RecommendationSources {
  public record Request(
      UUID strategyId,
      long expectedStrategyVersion,
      LocalDate fromDate,
      LocalDate untilDate,
      String timezone,
      PlaybookContent.Channel platform) {
    public Request {
      if (strategyId == null || expectedStrategyVersion < 0 || platform == null)
        throw new IllegalArgumentException("Invalid recommendation request");
      new PerformanceQuery(
          fromDate,
          untilDate,
          timezone,
          PerformanceQuery.Dimension.PILLAR,
          platform,
          0,
          50,
          PerformanceQuery.Bucket.PERIOD);
    }

    public PerformanceQuery query() {
      return new PerformanceQuery(
          fromDate,
          untilDate,
          timezone,
          PerformanceQuery.Dimension.PILLAR,
          platform,
          0,
          50,
          PerformanceQuery.Bucket.PERIOD);
    }
  }

  public record Capture(
      Brand brand,
      Strategy strategy,
      RecommendationCommand command,
      List<EvidencePhysicalSource> physical) {}

  private final AiGenerationSources parents;
  private final StrategyRepository strategies;
  private final PerformanceReader reader;
  private final PerformanceCalculator calculator;
  private final BrandIdentityRepository identities;
  private final EvidenceIdentitySourcePolicy identityPolicy;
  private final Clock clock;

  public RecommendationSources(
      AiGenerationSources parents,
      StrategyRepository strategies,
      PerformanceReader reader,
      PerformanceCalculator calculator,
      BrandIdentityRepository identities,
      EvidenceIdentitySourcePolicy identityPolicy,
      Clock clock) {
    this.parents = parents;
    this.strategies = strategies;
    this.reader = reader;
    this.calculator = calculator;
    this.identities = identities;
    this.identityPolicy = identityPolicy;
    this.clock = clock;
  }

  public Brand authorize(UUID actor, UUID brand, Set<UUID> scope) {
    var b = parents.authorize(actor, brand, scope);
    if (b.getStatus() != BrandStatus.ACTIVE
        || b.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Recommendation parent archived");
    return b;
  }

  public Capture capture(UUID actor, UUID brand, Set<UUID> scope, Request request) {
    var b = authorize(actor, brand, scope);
    var s =
        strategies
            .findLockedByIdAndBrandId(request.strategyId(), brand)
            .orElseThrow(RecommendationSources::missing);
    if (!s.isReadyContentSource()
        || s.getLockVersion() != request.expectedStrategyVersion()
        || !strategies
            .findTopByBrandIdOrderByVersionDesc(brand)
            .orElseThrow()
            .getId()
            .equals(s.getId()))
      throw new IllegalStateException("Current approved Strategy required");
    s.validateContent();
    var physical = new ArrayList<EvidencePhysicalSource>();
    ApprovedIdentitySource identity = null;
    var origin = s;
    for (int i = 0;
        origin.getGeneration() == null || origin.getGeneration().getIdentitySource() == null;
        i++) {
      if (origin.getRevisedFromId() == null) break;
      if (i >= 1000) throw new IllegalStateException("Invalid source lineage");
      var prior = strategies.findById(origin.getRevisedFromId()).orElseThrow();
      if (!prior.getBrand().getId().equals(brand) || prior.getVersion() >= origin.getVersion())
        throw new IllegalStateException("Invalid source lineage");
      origin = prior;
    }
    if (origin.getGeneration() != null && origin.getGeneration().getIdentitySource() == null)
      throw new IllegalStateException("Verified Strategy Identity lineage required");
    if (origin.getGeneration() != null && origin.getGeneration().getIdentitySource() != null) {
      identity = origin.getGeneration().getIdentitySource();
      var id = identities.findLockedByIdAndBrandId(identity.identityId(), brand).orElseThrow();
      if (id.getStatus() != ApprovalStatus.APPROVED
          || id.getVersion() != identity.version()
          || id.getLockVersion() != identity.lockVersion()
          || !id.getContent().checksum().equals(identity.checksum())
          || !Objects.equals(id.getApprovedAt(), identity.approvedAt())
          || !Objects.equals(id.getApprovedBy(), identity.approvedBy())
          || !identities
              .findTopByBrandIdOrderByVersionDesc(brand)
              .orElseThrow()
              .getId()
              .equals(id.getId()))
        throw new IllegalStateException("Strategy Identity source changed");
      physical.addAll(identityPolicy.validateForApproval(id, b));
    }
    var q = request.query();
    var report =
        calculator.calculate(
            brand,
            b.getWorkspace().getId(),
            q,
            clock.instant(),
            reader.read(brand, b.getWorkspace().getId(), q));
    var groups =
        report.items().stream()
            .filter(
                g ->
                    s.getId().equals(g.pillarStrategyId())
                        && g.pillarId() != null
                        && g.rates().getFirst().comparisonEligible())
            .sorted(
                Comparator.comparing((PerformanceGroup g) -> g.rates().getFirst().percent())
                    .thenComparing(PerformanceGroup::pillarId))
            .toList();
    if (groups.size() < 2) throw new IllegalStateException("Insufficient comparable metrics");
    var low = groups.getFirst();
    var high = groups.getLast();
    if (!low.comparisonKey().equals(high.comparisonKey())
        || high.rates()
                .getFirst()
                .percent()
                .subtract(low.rates().getFirst().percent())
                .compareTo(new java.math.BigDecimal("2"))
            < 0) throw new IllegalStateException("Insufficient descriptive difference");
    var rows =
        java.util.stream.Stream.concat(low.sources().stream(), high.sources().stream())
            .sorted(Comparator.comparing(PerformanceContribution::observationId))
            .toList();
    var proposal =
        new RecommendationProposal(
            low.pillarId(),
            high.pillarId(),
            5,
            rows.stream().map(PerformanceContribution::observationId).toList());
    var content = StrategyContent.from(s);
    proposal.apply(content, s.getPlaybook());
    var command =
        new RecommendationCommand(
            brand,
            b.getLockVersion(),
            b.getWorkspace().getLockVersion(),
            s.getId(),
            s.getVersion(),
            s.getLockVersion(),
            BrandAnalysisAttempt.checksum(s.getPlaybook().canonical(content)),
            identity,
            q,
            content,
            s.getPlaybook(),
            proposal,
            rows);
    return new Capture(b, s, command, List.copyOf(physical));
  }

  static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Recommendation resource not found");
  }
}
