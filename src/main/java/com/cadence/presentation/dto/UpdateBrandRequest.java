package com.cadence.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateBrandRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 2048) String websiteUrl,
    @Size(max = 255) String instagramHandle,
    @Size(max = 255) String tiktokHandle) {}
