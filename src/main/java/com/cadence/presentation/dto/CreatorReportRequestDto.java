package com.cadence.presentation.dto;

import com.cadence.domain.*;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public record CreatorReportRequestDto(
    @NotNull UUID strategyId,
    @NotNull @PositiveOrZero Long expectedStrategyVersion,
    @NotNull LocalDate fromDate,
    @NotNull LocalDate untilDate,
    @NotBlank @Size(max = 100) String timezone,
    @NotNull PlaybookContent.Channel platform,
    @NotNull PerformanceQuery.Bucket bucket,
    @NotNull @Size(max = 10) List<@NotNull UUID> recommendationIds)
    implements StrictPostRequest {
  public CreatorReportRequest toDomain() {
    return new CreatorReportRequest(
        strategyId,
        expectedStrategyVersion,
        fromDate,
        untilDate,
        timezone,
        platform,
        bucket,
        recommendationIds);
  }
}
