package com.cadence.presentation;

import com.cadence.application.usecases.GetJobUseCase;
import com.cadence.presentation.dto.JobResponse;
import com.cadence.security.CadencePrincipal;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {
  private final GetJobUseCase jobs;
  private final com.cadence.application.usecases.AiGenerationUseCase generation;
  private final com.cadence.application.usecases.CsvExportUseCase exports;
  private final com.cadence.application.usecases.ImportMetricsUseCase metrics;
  private final com.cadence.application.usecases.CreatorReportUseCase reports;

  public JobController(
      GetJobUseCase jobs,
      com.cadence.application.usecases.AiGenerationUseCase generation,
      com.cadence.application.usecases.CsvExportUseCase exports,
      com.cadence.application.usecases.ImportMetricsUseCase metrics,
      com.cadence.application.usecases.CreatorReportUseCase reports) {
    this.reports = reports;
    this.metrics = metrics;
    this.exports = exports;
    this.generation = generation;
    this.jobs = jobs;
  }

  @GetMapping("/{jobId}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<?> findById(Authentication authentication, @PathVariable UUID jobId) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    var report = reports.status(jobId, principal.workspaceIds(), true);
    if (report != null)
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(report);
    var imported = metrics.status(jobId, principal.workspaceIds(), true);
    if (imported != null)
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(imported);
    var csv = exports.status(jobId, principal.workspaceIds(), true);
    if (csv != null) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(csv);
    var ai = generation.status(principal.userId(), principal.workspaceIds(), jobId);
    if (ai != null) return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ai);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(JobResponse.from(jobs.execute(jobId, principal.workspaceIds())));
  }
}
