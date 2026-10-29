package com.cadence.application.ports;

import com.cadence.application.results.PerformanceReport.PerformanceContribution;
import com.cadence.domain.*;
import java.util.*;

/** Only scoped normalized data; no storage references, raw imports, prompts or tools. */
public record RecommendationCommand(
    UUID brandId,
    long brandVersion,
    long workspaceVersion,
    UUID strategyId,
    int strategyRevision,
    long strategyVersion,
    String strategyChecksum,
    ApprovedIdentitySource identitySource,
    PerformanceQuery query,
    StrategyContent content,
    PlaybookContent playbook,
    RecommendationProposal expected,
    List<PerformanceContribution> sources) {
  public RecommendationCommand {
    sources = List.copyOf(sources);
  }

  public void validate(RecommendationProposal output) {
    if (output == null
        || !expected.fromPillar().equals(output.fromPillar())
        || !expected.toPillar().equals(output.toPillar())
        || expected.points() != output.points()
        || !new HashSet<>(expected.citations()).equals(new HashSet<>(output.citations())))
      throw new IllegalStateException("Uncaptured recommendation proposal or evidence");
    output.apply(content, playbook);
  }
}
