package com.cadence.presentation;

import com.cadence.application.results.*;
import com.cadence.application.usecases.CreatorReportUseCase;
import com.cadence.presentation.dto.CreatorReportRequestDto;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class CreatorReportController {
  private final CreatorReportUseCase reports;

  public CreatorReportController(CreatorReportUseCase reports) {
    this.reports = reports;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping("/brands/{brandId}/reports")
  public ResponseEntity<CreatorReportDetails> submit(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CreatorReportRequestDto request) {
    var r = reports.submit(brandId, scope(a), key, request.toDomain());
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/jobs/" + r.jobId()))
        .cacheControl(CacheControl.noStore())
        .body(r);
  }

  @GetMapping("/reports/{id}")
  public ResponseEntity<CreatorReportDetails> status(Authentication a, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reports.status(id, scope(a), false));
  }

  @GetMapping("/reports/{id}/result")
  public ResponseEntity<CreatorReportSnapshot> result(Authentication a, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(reports.result(id, scope(a)));
  }

  @GetMapping("/reports/{id}/download")
  public ResponseEntity<byte[]> download(
      Authentication a,
      @PathVariable UUID id,
      @RequestHeader(value = "Range", required = false) String range) {
    if (range != null) throw new IllegalArgumentException("Report Range requests unsupported");
    var d = reports.download(id, scope(a));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
        .header(
            "Content-Disposition", "attachment; filename=\"cadence-report-" + d.id() + ".html\"")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("Accept-Ranges", "none")
        .header("X-Cadence-Report-SHA256", d.checksum())
        .contentLength(d.bytes().length)
        .body(d.bytes());
  }
}
