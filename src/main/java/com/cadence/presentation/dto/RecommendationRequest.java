package com.cadence.presentation.dto;

import com.cadence.domain.PlaybookContent;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;

public record RecommendationRequest(
    @NotNull UUID strategyId,
    @NotNull @PositiveOrZero Long expectedStrategyVersion,
    @NotNull LocalDate fromDate,
    @NotNull LocalDate untilDate,
    @NotBlank @Size(max = 100) String timezone,
    @NotNull PlaybookContent.Channel platform)
    implements StrictPostRequest {}
