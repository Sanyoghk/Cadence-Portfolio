package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record MetricAttributionRequest(
    @NotNull UUID postRevisionId,
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull @Min(0) @Max(100) Integer expectedAttributionVersion,
    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String pillarId)
    implements StrictPostRequest {}
