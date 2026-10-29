package com.cadence.infrastructure.analysis;

import com.cadence.application.ports.*;
import java.util.List;

/** Explicit demo fixture selected only by the guarded local/test configuration. */
final class DeterministicBrandAnalysisAdapter implements BrandAnalysisPort {
  @Override
  public BrandAnalysisResult analyze(BrandAnalysisCommand command) {
    if (command == null) throw new IllegalArgumentException("Missing analysis command");
    var citations =
        command.sources().stream()
            .map(
                s -> {
                  String path =
                      s.structured()
                          ? s.fields().keySet().stream()
                              .sorted()
                              .filter(k -> !s.fields().get(k).isBlank())
                              .findFirst()
                              .orElseThrow()
                          : null;
                  String quote =
                      s.citationText(path)
                          .strip()
                          .codePoints()
                          .limit(40)
                          .collect(
                              StringBuilder::new,
                              StringBuilder::appendCodePoint,
                              StringBuilder::append)
                          .toString();
                  return new AnalysisCitation(s.admissionId(), quote, path);
                })
            .toList();
    return new BrandAnalysisResult(
        new BrandAnalysisResult.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        List.of("Inter"),
        List.of("professional", "confident"),
        List.of("aggressive", "spammy"),
        "Example brand caption",
        command.sources().isEmpty()
            ? List.of(
                BrandAnalysisResult.Warning.DEMONSTRATION_ONLY,
                BrandAnalysisResult.Warning.DECLARED_FIELDS_ONLY,
                BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED)
            : List.of(
                BrandAnalysisResult.Warning.DEMONSTRATION_ONLY,
                BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
        "local-fake",
        "deterministic-v1",
        command.sources().isEmpty() ? "declared-brand-v1" : command.inputFormat(),
        citations);
  }
}
