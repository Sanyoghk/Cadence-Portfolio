package com.cadence.presentation;

import com.cadence.application.results.CsvExportDetails;
import com.cadence.application.usecases.CsvExportUseCase;
import com.cadence.presentation.dto.CsvExportRequestDto;
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
public class CsvExportController {
  private final CsvExportUseCase exports;

  public CsvExportController(CsvExportUseCase exports) {
    this.exports = exports;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping("/brands/{brandId}/exports")
  public ResponseEntity<CsvExportDetails> submit(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody CsvExportRequestDto request) {
    var result = exports.submit(brandId, scope(a), key, request.toDomain());
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/jobs/" + result.jobId()))
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping("/exports/{exportId}")
  public ResponseEntity<CsvExportDetails> status(Authentication a, @PathVariable UUID exportId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(exports.status(exportId, scope(a), false));
  }

  @GetMapping("/exports/{exportId}/download")
  public ResponseEntity<byte[]> download(
      Authentication a,
      @PathVariable UUID exportId,
      @RequestHeader(value = "Range", required = false) String range) {
    if (range != null) throw new IllegalArgumentException("CSV Range requests unsupported");
    var d = exports.download(exportId, scope(a));
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore().cachePrivate())
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=\"cadence-export-" + d.id() + ".csv\"")
        .header("X-Content-Type-Options", "nosniff")
        .header("Content-Security-Policy", "default-src 'none'; sandbox")
        .header("Accept-Ranges", "none")
        .header("X-Cadence-CSV-SHA256", d.checksum())
        .contentLength(d.bytes().length)
        .body(d.bytes());
  }
}
