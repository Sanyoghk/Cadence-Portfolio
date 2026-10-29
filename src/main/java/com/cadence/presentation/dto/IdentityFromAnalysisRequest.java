package com.cadence.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record IdentityFromAnalysisRequest(
    UUID analysisId, @NotNull @PositiveOrZero Long expectedVersion) {}
