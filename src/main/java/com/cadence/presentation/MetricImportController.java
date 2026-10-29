package com.cadence.presentation;

import com.cadence.application.results.MetricImportDetails;
import com.cadence.application.services.MetricImportService;
import com.cadence.application.usecases.ImportMetricsUseCase;
import com.cadence.security.CadencePrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class MetricImportController {
  private final ImportMetricsUseCase imports;

  public MetricImportController(ImportMetricsUseCase imports) {
    this.imports = imports;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping(
      value = "/brands/{brandId}/metric-imports",
      consumes = {"application/json", "text/csv"})
  public ResponseEntity<MetricImportDetails> submit(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestHeader("Content-Type") String type,
      @RequestBody byte[] raw) {
    var media = MediaType.parseMediaType(type);
    if (media.getCharset() != null
        && !java.nio.charset.StandardCharsets.UTF_8.equals(media.getCharset()))
      throw new IllegalArgumentException("Metrics encoding must be UTF-8");
    var result =
        imports.submit(
            brandId,
            scope(a),
            key,
            media.isCompatibleWith(MediaType.APPLICATION_JSON) ? "MANUAL_JSON" : "CSV",
            raw);
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/jobs/" + result.jobId()))
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping("/metric-imports/{importId}")
  public ResponseEntity<MetricImportDetails> status(Authentication a, @PathVariable UUID importId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(imports.status(importId, scope(a), false));
  }

  @GetMapping("/posts/{postId}/metrics")
  public ResponseEntity<MetricImportService.ObservationPage> read(
      Authentication a,
      @PathVariable UUID postId,
      @RequestParam Instant from,
      @RequestParam Instant until,
      @RequestParam(defaultValue = "false") boolean history,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(imports.read(postId, scope(a), from, until, history, page, size));
  }
}
