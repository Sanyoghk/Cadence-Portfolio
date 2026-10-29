package com.cadence.presentation;

import com.cadence.application.results.*;
import com.cadence.application.usecases.PerformanceUseCase;
import com.cadence.domain.*;
import com.cadence.presentation.dto.MetricAttributionRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class PerformanceController {
  private final PerformanceUseCase usecase;

  public PerformanceController(PerformanceUseCase usecase) {
    this.usecase = usecase;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @GetMapping("/brands/{brandId}/performance")
  public ResponseEntity<PerformanceReport> read(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestParam LocalDate fromDate,
      @RequestParam LocalDate untilDate,
      @RequestParam String timezone,
      @RequestParam(defaultValue = "PLATFORM") PerformanceQuery.Dimension dimension,
      @RequestParam(defaultValue = "DAY") PerformanceQuery.Bucket bucket,
      @RequestParam(required = false) PlaybookContent.Channel platform,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            usecase.read(
                brandId,
                scope(a),
                new PerformanceQuery(
                    fromDate, untilDate, timezone, dimension, platform, page, size, bucket)));
  }

  @PutMapping("/metric-observations/{observationId}/attribution")
  public ResponseEntity<MetricAttributionDetails> attribute(
      Authentication a,
      @PathVariable UUID observationId,
      @Valid @RequestBody MetricAttributionRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            usecase.reviseAttribution(
                observationId,
                scope(a),
                request.postRevisionId(),
                request.expectedVersion(),
                request.pillarId(),
                request.expectedAttributionVersion()));
  }

  @GetMapping("/metric-observations/{observationId}/attribution")
  public ResponseEntity<MetricAttributionDetails> attribution(
      Authentication a,
      @PathVariable UUID observationId,
      @RequestParam(required = false) Integer version) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(usecase.attributionVersion(observationId, scope(a), version));
  }
}
