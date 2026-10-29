package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.domain.ApprovedIdentitySource;
import java.util.*;

/** Private storage schema: independent of the model-facing JSON annotations. */
public record AiGenerationSnapshot(
    int schemaVersion,
    Analysis analysis,
    StrategyGenerationCommand strategy,
    ApprovedIdentitySource identity,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        ContentCapture content) {
  public AiGenerationSnapshot(
      int schemaVersion,
      Analysis analysis,
      StrategyGenerationCommand strategy,
      ApprovedIdentitySource identity) {
    this(schemaVersion, analysis, strategy, identity, null);
  }

  public AiGenerationSnapshot {
    if (content != null) {
      if (schemaVersion != 3 || analysis != null || strategy != null || identity == null)
        throw new IllegalArgumentException("Invalid Content snapshot");
    } else if ((schemaVersion != (strategy == null ? 1 : 2))
        || (analysis == null) == (strategy == null)
        || (strategy == null) != (identity == null))
      throw new IllegalArgumentException("Invalid snapshot schema");
  }

  public record Source(
      UUID admissionId,
      UUID evidenceId,
      long sourceVersion,
      String contentChecksum,
      String text,
      Map<String, String> fields) {
    static Source of(AnalysisTextSource s) {
      return new Source(
          s.admissionId(),
          s.evidenceId(),
          s.sourceVersion(),
          s.contentChecksum(),
          s.text(),
          s.fields());
    }

    AnalysisTextSource input() {
      return new AnalysisTextSource(
          admissionId, evidenceId, sourceVersion, contentChecksum, text, fields);
    }
  }

  public record Analysis(
      UUID analysisId,
      long brandVersion,
      long workspaceVersion,
      String brandName,
      String industry,
      List<Source> sources) {
    static Analysis of(BrandAnalysisCommand c) {
      return new Analysis(
          c.analysisId(),
          c.brandVersion(),
          c.workspaceVersion(),
          c.brandName(),
          c.industry(),
          c.sources().stream().map(Source::of).toList());
    }

    BrandAnalysisCommand input() {
      return new BrandAnalysisCommand(
          analysisId,
          brandVersion,
          workspaceVersion,
          brandName,
          industry,
          sources.stream().map(Source::input).toList());
    }
  }

  public static AiGenerationSnapshot of(AiGenerationSources.Input i) {
    return new AiGenerationSnapshot(
        i.content() != null ? 3 : i.strategy() == null ? 1 : 2,
        i.analysis() == null ? null : Analysis.of(i.analysis()),
        i.strategy(),
        i.identity(),
        i.content());
  }

  public AiGenerationSources.Input input() {
    return new AiGenerationSources.Input(
        analysis == null ? null : analysis.input(), strategy, identity, content);
  }
}
