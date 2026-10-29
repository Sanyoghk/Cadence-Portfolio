package com.cadence.application.results;

import com.cadence.application.ports.RecommendationEngine;
import java.time.Instant;
import java.util.UUID;

/** Bounded listing; full captured normalized sources are available only on detail read. */
public record RecommendationSummary(
    UUID id,
    UUID operationId,
    UUID brandId,
    UUID strategyId,
    String status,
    RecommendationEngine.Attribution attribution,
    String confidence,
    Instant capturedAt) {}
