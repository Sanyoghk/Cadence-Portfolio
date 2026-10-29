package com.cadence.application.results;

import com.cadence.application.ports.StrategyGenerationResult;
import com.cadence.domain.ApprovalStatus;
import java.util.UUID;

public record BuiltStrategy(
    UUID id, UUID brandId, int version, ApprovalStatus status, StrategyGenerationResult content) {}
