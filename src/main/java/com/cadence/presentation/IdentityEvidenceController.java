package com.cadence.presentation;

import com.cadence.application.results.IdentityEvidenceProvenance;
import com.cadence.application.usecases.IdentityEvidenceUseCase;
import com.cadence.presentation.dto.*;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("denyAll()")
public class IdentityEvidenceController {
  private final IdentityEvidenceUseCase identities;

  public IdentityEvidenceController(IdentityEvidenceUseCase identities) {
    this.identities = identities;
  }

  @PostMapping("/brands/{brandId}/identities/from-evidence-analysis")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<BrandIdentityResponse> copy(
      Authentication auth,
      @PathVariable UUID brandId,
      @Valid @RequestBody EvidenceIdentityFromAnalysisRequest request) {
    var p = (CadencePrincipal) auth.getPrincipal();
    var result =
        BrandIdentityResponse.from(
            identities.createFromAnalysis(
                brandId,
                p.workspaceIds(),
                request.analysisId(),
                request.expectedVersion(),
                p.userId()));
    return ResponseEntity.created(URI.create("/api/v1/identities/" + result.id()))
        .cacheControl(CacheControl.noStore())
        .body(result);
  }

  @GetMapping("/identities/{identityId}/evidence-provenance")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<IdentityEvidenceProvenance> read(
      Authentication auth, @PathVariable UUID identityId) {
    var p = (CadencePrincipal) auth.getPrincipal();
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(identities.read(identityId, p.workspaceIds(), p.userId()));
  }
}
