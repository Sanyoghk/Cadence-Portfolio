package com.cadence.presentation.dto;

import com.cadence.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreatePostRequest(
    @NotNull UUID strategyId,
    @NotNull @PositiveOrZero Long expectedStrategyVersion,
    @NotNull PlaybookContent.Channel platform,
    @NotNull @Valid PostContentRequest content)
    implements StrictPostRequest {}
