package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;

public record ReviewMediaRequest(
    @NotNull @PositiveOrZero Long expectedVersion, @NotNull Boolean approved)
    implements StrictPostRequest {}
