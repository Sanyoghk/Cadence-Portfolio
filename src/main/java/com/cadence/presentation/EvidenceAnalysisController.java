package com.cadence.presentation;

import com.cadence.application.usecases.AnalyzeEvidenceUseCase;
import com.cadence.presentation.dto.EvidenceAnalysisResponse;
import com.cadence.security.CadencePrincipal;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cadence/brands/{brandId}/analyze-evidence")
@PreAuthorize("denyAll()")
public class EvidenceAnalysisController {
  private final AnalyzeEvidenceUseCase useCase;

  public EvidenceAnalysisController(AnalyzeEvidenceUseCase useCase) {
    this.useCase = useCase;
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<EvidenceAnalysisResponse> analyze(
      Authentication authentication, @PathVariable UUID brandId) {
    var p = (CadencePrincipal) authentication.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            EvidenceAnalysisResponse.from(useCase.execute(brandId, p.workspaceIds(), p.userId())));
  }
}
