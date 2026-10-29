package com.cadence.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record EditPostRequest(
    @NotNull @PositiveOrZero Long expectedVersion, @NotNull @Valid PostContentRequest content)
    implements StrictPostRequest {}
