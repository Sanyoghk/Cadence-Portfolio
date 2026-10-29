package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;

public record RecommendationResolutionRequest(@NotNull @Min(0) @Max(0) Long expectedVersion)
    implements StrictPostRequest {}
