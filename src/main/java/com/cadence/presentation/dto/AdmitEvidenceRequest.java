package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;

public record AdmitEvidenceRequest(
    @NotNull @PositiveOrZero Long sourceVersion,
    @NotNull @PositiveOrZero Long brandVersion,
    @NotNull @PositiveOrZero Long workspaceVersion,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentChecksum) {}
