package com.cadence.presentation;

import com.cadence.application.services.AiGenerationService;
import com.cadence.application.usecases.AiGenerationUseCase;
import com.cadence.domain.AiGenerationOperation;
import com.cadence.security.CadencePrincipal;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class AiGenerationController {
  public enum Mode {
    DECLARED,
    EVIDENCE
  }

  public record AnalysisRequest(@NotNull @PositiveOrZero Long expectedVersion, @NotNull Mode mode) {
    @JsonAnySetter
    public void unexpected(String key, Object value) {
      throw new IllegalArgumentException("Unknown field");
    }
  }

  public record StrategyRequest(@NotNull @PositiveOrZero Long expectedVersion) {
    @JsonAnySetter
    public void unexpected(String key, Object value) {
      throw new IllegalArgumentException("Unknown field");
    }
  }

  private final AiGenerationUseCase useCase;

  public AiGenerationController(AiGenerationUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping("/brands/{brandId}/analysis-jobs")
  public ResponseEntity<AiGenerationService.View> analysis(
      Authentication auth,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AnalysisRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return response(
        useCase.submit(
            p.userId(),
            p.workspaceIds(),
            brandId,
            AiGenerationOperation.Kind.valueOf(request.mode().name()),
            request.expectedVersion(),
            key));
  }

  @PostMapping("/brands/{brandId}/strategy-jobs")
  public ResponseEntity<AiGenerationService.View> strategy(
      Authentication auth,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody StrategyRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return response(
        useCase.submit(
            p.userId(),
            p.workspaceIds(),
            brandId,
            AiGenerationOperation.Kind.STRATEGY,
            request.expectedVersion(),
            key));
  }

  @GetMapping("/analysis-results/{id}")
  public ResponseEntity<com.cadence.application.ports.BrandAnalysisResult> result(
      Authentication auth, @PathVariable UUID id) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(useCase.result(p.userId(), p.workspaceIds(), id));
  }

  private ResponseEntity<AiGenerationService.View> response(AiGenerationService.View v) {
    return ResponseEntity.status(v.terminal() ? 200 : 202)
        .location(URI.create("/api/v1/jobs/" + v.jobId()))
        .cacheControl(CacheControl.noStore())
        .body(v);
  }
}
