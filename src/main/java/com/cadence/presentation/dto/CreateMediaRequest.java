package com.cadence.presentation.dto;

import com.cadence.domain.DemoMediaFixture;
import jakarta.validation.constraints.NotNull;

public record CreateMediaRequest(@NotNull DemoMediaFixture fixture) implements StrictPostRequest {}
