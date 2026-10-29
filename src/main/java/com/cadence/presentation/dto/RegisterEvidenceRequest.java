package com.cadence.presentation.dto;

import com.cadence.domain.EvidenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterEvidenceRequest(
    @NotNull EvidenceType type, @NotBlank @Size(max = 20000) String value) {}
