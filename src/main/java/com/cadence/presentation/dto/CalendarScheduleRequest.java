package com.cadence.presentation.dto;

import com.cadence.application.usecases.PostCalendarUseCase;
import com.cadence.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record CalendarScheduleRequest(
    @NotNull @Size(min = 1, max = 84) List<@NotNull @Valid Item> changes)
    implements StrictPostRequest {
  public record Item(
      @NotNull UUID postId,
      @NotNull UUID revisionId,
      @NotNull @PositiveOrZero Long expectedRevisionVersion,
      @NotNull @Min(0) @Max(999) Integer expectedScheduleVersion,
      @NotNull PostScheduleEvent.Action action,
      @Size(max = 100) String timezone,
      LocalDateTime localTime,
      ZoneOffset offset)
      implements StrictPostRequest {
    public PostCalendarUseCase.Change change() {
      CalendarTime time = null;
      if (action == PostScheduleEvent.Action.SCHEDULE)
        time = new CalendarTime(timezone, localTime, offset);
      else if (timezone != null || localTime != null || offset != null)
        throw new IllegalArgumentException("Cancellation has no replacement time");
      return new PostCalendarUseCase.Change(
          postId, revisionId, expectedRevisionVersion, expectedScheduleVersion, action, time);
    }
  }
}
