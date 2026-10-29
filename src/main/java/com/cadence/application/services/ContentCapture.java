package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;

/** Private persisted schema. Server resource IDs are excluded from the model-facing command. */
public record ContentCapture(
    ContentGenerationRequest request,
    UUID strategyId,
    int strategyRevision,
    long strategyLockVersion,
    UUID strategyApprovedBy,
    Instant strategyApprovedAt,
    String strategyChecksum,
    UUID lineageOrigin,
    StrategyGenerationCommand identityContext,
    ContentGenerationCommand command) {}
