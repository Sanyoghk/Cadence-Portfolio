package com.cadence.application.results;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;

public record RecommendationDetails(
    Instant windowFrom,
    Instant windowUntil,
    String rulesVersion,
    UUID operationId,
    UUID id,
    UUID brandId,
    UUID workspaceId,
    UUID actorId,
    long lockVersion,
    String status,
    RecommendationEngine.Attribution attribution,
    String confidence,
    Double calibratedProbability,
    String expectedLift,
    String claimType,
    String explanation,
    List<String> uncertainty,
    RecommendationCommand capture,
    String snapshotChecksum,
    Instant capturedAt,
    Resolution resolution) {
  public record Resolution(
      UUID operationId,
      UUID id,
      String action,
      UUID actorId,
      Instant resolvedAt,
      UUID draftId,
      String initialDraftChecksum) {}
}
