package com.cadence.presentation;

import com.cadence.application.services.AiGenerationService;
import com.cadence.application.usecases.GenerateContentCalendarUseCase;
import com.cadence.domain.ContentGenerationRequest;
import com.cadence.presentation.dto.StrictPostRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('OWNER')")
public class ContentGenerationController {
  public record Request(
      @NotNull @PositiveOrZero Long expectedBrandVersion,
      @NotNull UUID strategyId,
      @NotNull @PositiveOrZero Long expectedStrategyVersion,
      @NotNull @Size(min = 1, max = 84)
          List<@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String> slotIds)
      implements StrictPostRequest {}

  public record Regenerate(
      @NotNull UUID revisionId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String slotId,
      @NotNull ContentGenerationRequest.Field field)
      implements StrictPostRequest {}

  private final GenerateContentCalendarUseCase useCase;

  public ContentGenerationController(GenerateContentCalendarUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping("/brands/{brandId}/content-jobs")
  public ResponseEntity<AiGenerationService.View> submit(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Request r) {
    var p = (CadencePrincipal) a.getPrincipal();
    return response(
        useCase.submit(
            p.userId(),
            p.workspaceIds(),
            brandId,
            r.expectedBrandVersion(),
            key,
            new ContentGenerationRequest(
                r.strategyId(),
                r.expectedStrategyVersion(),
                r.slotIds(),
                null,
                null,
                null,
                ContentGenerationRequest.Field.ALL)));
  }

  @PostMapping("/posts/{postId}/regeneration-jobs")
  public ResponseEntity<AiGenerationService.View> regenerate(
      Authentication a,
      @PathVariable UUID postId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Regenerate r) {
    var p = (CadencePrincipal) a.getPrincipal();
    return response(
        useCase.regenerate(
            p.userId(),
            p.workspaceIds(),
            postId,
            r.revisionId(),
            r.expectedVersion(),
            r.slotId(),
            r.field(),
            key));
  }

  @GetMapping("/content-results/{id}")
  public ResponseEntity<AiGenerationService.ContentResult> result(
      Authentication a, @PathVariable UUID id) {
    var p = (CadencePrincipal) a.getPrincipal();
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
