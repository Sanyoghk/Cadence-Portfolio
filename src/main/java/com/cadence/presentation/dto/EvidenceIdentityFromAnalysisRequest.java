package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record EvidenceIdentityFromAnalysisRequest(
    @NotNull UUID analysisId, @NotNull @PositiveOrZero Long expectedVersion) {}
