package com.cadence.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String industry) {}
