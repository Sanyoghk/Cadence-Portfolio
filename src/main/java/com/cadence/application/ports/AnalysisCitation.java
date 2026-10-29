package com.cadence.application.ports;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Exact quotation provenance; this does not prove semantic truth or relevance. */
public record AnalysisCitation(UUID admissionId, String quote, String fieldPath) {
  public AnalysisCitation(UUID admissionId, String quote) {
    this(admissionId, quote, null);
  }

  public AnalysisCitation {
    if (fieldPath != null && !com.cadence.domain.SocialTextProjection.citationPath(fieldPath))
      throw new IllegalArgumentException("Invalid citation field path");
    if (admissionId == null
        || quote == null
        || quote.isBlank()
        || quote.length() > 500
        || quote.getBytes(StandardCharsets.UTF_8).length > 1000
        || !StandardCharsets.UTF_8.newEncoder().canEncode(quote)
        || quote
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
      throw new IllegalArgumentException("Invalid analysis citation");
  }
}
