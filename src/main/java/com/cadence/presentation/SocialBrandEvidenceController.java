package com.cadence.presentation;

import com.cadence.application.usecases.RegisterSocialBrandEvidenceUseCase;
import com.cadence.presentation.dto.EvidenceRegistrationResponse;
import com.cadence.presentation.dto.RegisterSocialEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/brands/{brandId}/evidence/social")
public class SocialBrandEvidenceController {
  private final RegisterSocialBrandEvidenceUseCase registration;

  public SocialBrandEvidenceController(RegisterSocialBrandEvidenceUseCase registration) {
    this.registration = registration;
  }

  @PostMapping
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public ResponseEntity<EvidenceRegistrationResponse> register(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody RegisterSocialEvidenceRequest request) {
    var result =
        registration.register(
            brandId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            request.provider());
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/jobs/" + result.job().getId()))
        .cacheControl(CacheControl.noStore())
        .body(EvidenceRegistrationResponse.from(result));
  }
}
