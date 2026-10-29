package com.cadence.presentation;

import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import com.cadence.presentation.dto.*;
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
public class RecommendationController {
  private final GenerateRecommendationsUseCase usecase;

  public RecommendationController(GenerateRecommendationsUseCase usecase) {
    this.usecase = usecase;
  }

  private Set<UUID> scope(Authentication a) {
    return ((CadencePrincipal) a.getPrincipal()).workspaceIds();
  }

  @PostMapping("/brands/{brandId}/recommendations")
  public ResponseEntity<RecommendationDetails> generate(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RecommendationRequest request) {
    var r =
        usecase.generate(
            brandId,
            scope(a),
            key,
            new RecommendationSources.Request(
                request.strategyId(),
                request.expectedStrategyVersion(),
                request.fromDate(),
                request.untilDate(),
                request.timezone(),
                request.platform()));
    return response(r);
  }

  @GetMapping("/brands/{brandId}/recommendations")
  public ResponseEntity<PageResponse<RecommendationSummary>> list(
      Authentication a,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(PageResponse.from(usecase.list(brandId, scope(a), page, size), r -> r));
  }

  @GetMapping("/recommendations/{id}")
  public ResponseEntity<RecommendationDetails> read(Authentication a, @PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(usecase.read(id, scope(a)));
  }

  @PostMapping("/recommendations/{id}/apply")
  public ResponseEntity<RecommendationDetails> apply(
      Authentication a,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RecommendationResolutionRequest request) {
    return response(
        usecase.resolve(
            id, scope(a), key, request.expectedVersion(), RecommendationResolution.Action.APPLY));
  }

  @PostMapping("/recommendations/{id}/dismiss")
  public ResponseEntity<RecommendationDetails> dismiss(
      Authentication a,
      @PathVariable UUID id,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody RecommendationResolutionRequest request) {
    return response(
        usecase.resolve(
            id, scope(a), key, request.expectedVersion(), RecommendationResolution.Action.DISMISS));
  }

  private ResponseEntity<RecommendationDetails> response(RecommendationService.Result r) {
    return ResponseEntity.status(r.created() ? 201 : 200)
        .location(URI.create("/api/v1/recommendations/" + r.details().id()))
        .cacheControl(CacheControl.noStore())
        .body(r.details());
  }
}
