package com.cadence.presentation;

import com.cadence.application.usecases.BrandManagementUseCase;
import com.cadence.presentation.dto.BrandResponse;
import com.cadence.presentation.dto.UpdateBrandRequest;
import com.cadence.presentation.dto.VersionRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/brands")
public class BrandController {

  private final BrandManagementUseCase brands;

  public BrandController(BrandManagementUseCase brands) {
    this.brands = brands;
  }

  @GetMapping("/{brandId}")
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public BrandResponse findById(Authentication authentication, @PathVariable UUID brandId) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return BrandResponse.from(brands.findById(brandId, principal.workspaceIds()));
  }

  @PatchMapping("/{brandId}")
  @Transactional
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public BrandResponse update(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody UpdateBrandRequest request) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return BrandResponse.from(
        brands.update(
            brandId,
            principal.workspaceIds(),
            request.expectedVersion(),
            request.name(),
            request.websiteUrl(),
            request.instagramHandle(),
            request.tiktokHandle()));
  }

  @PostMapping("/{brandId}/archive")
  @Transactional
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public BrandResponse archive(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody VersionRequest request) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return BrandResponse.from(
        brands.archive(brandId, principal.workspaceIds(), request.expectedVersion()));
  }
}
