package com.cadence.presentation;

import com.cadence.application.results.ScheduleDetails;
import com.cadence.application.usecases.PostCalendarUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.*;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class CalendarController {
  private final PostCalendarUseCase calendar;

  public CalendarController(PostCalendarUseCase calendar) {
    this.calendar = calendar;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping("/brands/{brandId}/calendar/schedules")
  public List<ScheduleDetails> change(
      Authentication a, @PathVariable UUID brandId, @Valid @RequestBody CalendarScheduleRequest r) {
    return calendar.change(
        brandId, scope(a), r.changes().stream().map(CalendarScheduleRequest.Item::change).toList());
  }

  @GetMapping("/brands/{brandId}/calendar")
  public PageResponse<ScheduleDetails> calendar(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestParam Instant from,
      @RequestParam Instant until,
      @RequestParam String timezone,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "100") int size) {
    return PageResponse.from(
        calendar.calendar(
            brandId,
            scope(a),
            from,
            until,
            timezone,
            ApiPagination.request(page, size, "scheduledAt", "asc", "scheduledAt")),
        d -> d);
  }

  @GetMapping("/posts/{postId}/schedule-history")
  public PageResponse<ScheduleDetails> history(
      Authentication a,
      @PathVariable UUID postId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return PageResponse.from(
        calendar.history(
            postId,
            scope(a),
            ApiPagination.request(page, size, "eventNumber", "desc", "eventNumber")),
        d -> d);
  }
}
