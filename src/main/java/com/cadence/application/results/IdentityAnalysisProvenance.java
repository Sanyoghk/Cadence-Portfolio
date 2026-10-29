package com.cadence.application.results;

import com.cadence.domain.BrandAnalysisAttempt;
import java.time.Instant;
import java.util.UUID;

/** Receipt of the original generation, never a claim that later manual edits were model output. */
public record IdentityAnalysisProvenance(
    UUID analysisId,
    String provider,
    String modelVersion,
    String promptVersion,
    String warnings,
    String initialOutputChecksum,
    Instant generatedAt) {
  public static IdentityAnalysisProvenance from(BrandAnalysisAttempt attempt) {
    return attempt == null
        ? null
        : new IdentityAnalysisProvenance(
            attempt.getId(),
            attempt.getProvider(),
            attempt.getModelVersion(),
            attempt.getPromptVersion(),
            attempt.getWarnings(),
            attempt.getOutputChecksum(),
            attempt.getCompletedAt());
  }
}
