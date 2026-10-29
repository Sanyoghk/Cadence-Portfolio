package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;

public record PostApprovalRequest(@NotNull @PositiveOrZero Long expectedVersion)
    implements StrictPostRequest {}
