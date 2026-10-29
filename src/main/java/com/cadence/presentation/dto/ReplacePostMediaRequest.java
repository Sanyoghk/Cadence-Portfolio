package com.cadence.presentation.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record ReplacePostMediaRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull @Size(max = 10) List<@NotNull UUID> assetIds)
    implements StrictPostRequest {}
