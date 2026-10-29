package com.cadence.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record CreateBrandIdentityRequest(@NotNull @Valid IdentityContentRequest content) {}
