package com.cadence.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record UpdateBrandIdentityRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull @Valid IdentityContentRequest content) {}
