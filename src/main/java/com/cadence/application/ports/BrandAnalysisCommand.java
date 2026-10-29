package com.cadence.application.ports;

import java.util.UUID;

public record BrandAnalysisCommand(
    UUID analysisId,
    long brandVersion,
    long workspaceVersion,
    String brandName,
    String industry,
    java.util.List<AnalysisTextSource> sources) {
  public static final int MAX_SOURCES = 5;
  public static final int MAX_SOURCE_BYTES = 20000;

  public BrandAnalysisCommand(
      UUID analysisId,
      long brandVersion,
      long workspaceVersion,
      String brandName,
      String industry) {
    this(analysisId, brandVersion, workspaceVersion, brandName, industry, java.util.List.of());
  }

  public BrandAnalysisCommand {
    if (analysisId == null || brandVersion < 0 || workspaceVersion < 0) {
      throw new IllegalArgumentException("Invalid analysis identity or source version");
    }
    brandName = text(brandName);
    industry = text(industry);
    if (sources == null || sources.size() > MAX_SOURCES)
      throw new IllegalArgumentException("Invalid analysis sources");
    sources = java.util.List.copyOf(sources);
    if (sources.stream().map(AnalysisTextSource::admissionId).distinct().count() != sources.size()
        || sources.stream().map(AnalysisTextSource::evidenceId).distinct().count() != sources.size()
        || sources.stream().mapToInt(AnalysisTextSource::utf8Bytes).sum() > MAX_SOURCE_BYTES)
      throw new IllegalArgumentException("Invalid analysis sources");
  }

  public String inputFormat() {
    if (sources.stream().anyMatch(AnalysisTextSource::social)) return "social-evidence-v3";
    return sources.stream().anyMatch(AnalysisTextSource::structured)
        ? "mixed-evidence-v2"
        : "text-evidence-v1";
  }

  public String sourceChecksum() {
    if (sources.isEmpty())
      return com.cadence.domain.BrandAnalysisAttempt.checksum(brandName + "\n" + industry);
    var input =
        new StringBuilder(inputFormat() + "\n").append(brandName).append('\n').append(industry);
    for (var s : sources)
      input
          .append('\n')
          .append(s.admissionId())
          .append('\n')
          .append(s.evidenceId())
          .append('\n')
          .append(s.sourceVersion())
          .append('\n')
          .append(s.contentChecksum());
    return com.cadence.domain.BrandAnalysisAttempt.checksum(input.toString());
  }

  public void validateCitations(java.util.List<AnalysisCitation> citations) {
    if (citations == null
        || citations.size() > 10
        || (!sources.isEmpty() && citations.isEmpty())
        || (sources.isEmpty() && !citations.isEmpty())
        || citations.stream().distinct().count() != citations.size())
      throw new IllegalArgumentException("Invalid analysis citations");
    for (var citation : citations) {
      var source =
          sources.stream()
              .filter(s -> s.admissionId().equals(citation.admissionId()))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("Invalid analysis citations"));
      if (!source.citationText(citation.fieldPath()).contains(citation.quote()))
        throw new IllegalArgumentException("Invalid analysis citations");
    }
  }

  private static String text(String value) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > 255
        || value.codePoints().anyMatch(Character::isISOControl)) {
      throw new IllegalArgumentException("Invalid declared analysis source");
    }
    return value.strip();
  }
}
