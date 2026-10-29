package com.cadence.presentation.dto;

import com.cadence.domain.CsvExportRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public record CsvExportRequestDto(
    @NotNull Instant from,
    @NotNull Instant until,
    @NotBlank @Size(max = 100) String timezone,
    @NotNull @Size(min = 1, max = 84) List<@NotNull @Valid Selection> selections)
    implements StrictPostRequest {
  public record Selection(
      @NotNull UUID postId,
      @NotNull UUID revisionId,
      @NotNull @PositiveOrZero Long revisionVersion,
      @NotNull @Min(1) @Max(999) Integer scheduleVersion)
      implements StrictPostRequest {}

  public CsvExportRequest toDomain() {
    return new CsvExportRequest(
        from,
        until,
        timezone,
        selections.stream()
            .map(
                s ->
                    new CsvExportRequest.Selection(
                        s.postId(), s.revisionId(), s.revisionVersion(), s.scheduleVersion()))
            .toList());
  }
}
