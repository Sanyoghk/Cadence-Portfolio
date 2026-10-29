package com.cadence.presentation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record VersionRequest(@NotNull @PositiveOrZero Long expectedVersion) {}
