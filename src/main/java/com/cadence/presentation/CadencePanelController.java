package com.cadence.presentation;

import com.cadence.application.usecases.AnalyzeBrandUseCase;
import com.cadence.presentation.dto.BrandSignatureResponse;
import com.cadence.security.CadencePrincipal;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cadence")
public class CadencePanelController {

  private final AnalyzeBrandUseCase analyzeBrandUseCase;

  public CadencePanelController(AnalyzeBrandUseCase analyzeBrandUseCase) {
    this.analyzeBrandUseCase = analyzeBrandUseCase;
  }

  @PostMapping("/brands/{brandId}/analyze")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public BrandSignatureResponse analyzeBrand(
      Authentication authentication, @PathVariable UUID brandId) {
    return BrandSignatureResponse.from(
        analyzeBrandUseCase.execute(
            brandId, ((CadencePrincipal) authentication.getPrincipal()).workspaceIds()));
  }
}
