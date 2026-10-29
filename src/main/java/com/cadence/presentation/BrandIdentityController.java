package com.cadence.presentation;

import com.cadence.application.usecases.BrandIdentityUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.*;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("denyAll()")
public class BrandIdentityController {
  private final BrandIdentityUseCase identities;

  public BrandIdentityController(BrandIdentityUseCase identities) {
    this.identities = identities;
  }

  @PostMapping("/brands/{brandId}/identities")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<BrandIdentityResponse> create(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody CreateBrandIdentityRequest request) {
    var response =
        BrandIdentityResponse.from(
            identities.create(
                brandId,
                ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
                request.content().toContent()));
    return ResponseEntity.created(URI.create("/api/v1/identities/" + response.id())).body(response);
  }

  @GetMapping("/brands/{brandId}/identities")
  @PreAuthorize("hasRole('OWNER')")
  public PageResponse<BrandIdentityResponse> list(
      Authentication authentication,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "version") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    return PageResponse.from(
        identities.findAll(
            brandId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            ApiPagination.request(
                page, size, sort, direction, "version", "createdAt", "updatedAt")),
        BrandIdentityResponse::from);
  }

  @PostMapping("/brands/{brandId}/identities/from-analysis")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<BrandIdentityResponse> fromAnalysis(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody IdentityFromAnalysisRequest request) {
    var response =
        BrandIdentityResponse.from(
            identities.createFromAnalysis(
                brandId,
                ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
                request.analysisId(),
                request.expectedVersion()));
    return ResponseEntity.created(URI.create("/api/v1/identities/" + response.id())).body(response);
  }

  @GetMapping("/identities/{identityId}")
  @PreAuthorize("hasRole('OWNER')")
  public BrandIdentityResponse read(Authentication authentication, @PathVariable UUID identityId) {
    return BrandIdentityResponse.from(
        identities.findById(
            identityId, ((CadencePrincipal) authentication.getPrincipal()).workspaceIds()));
  }

  @PatchMapping("/identities/{identityId}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<BrandIdentityResponse> update(
      Authentication authentication,
      @PathVariable UUID identityId,
      @Valid @RequestBody UpdateBrandIdentityRequest request) {
    var result =
        identities.update(
            identityId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            request.expectedVersion(),
            request.content().toContent());
    var response = BrandIdentityResponse.from(result.details());
    return result.created()
        ? ResponseEntity.created(URI.create("/api/v1/identities/" + response.id())).body(response)
        : ResponseEntity.ok(response);
  }

  @PostMapping("/identities/{identityId}/approval")
  @PreAuthorize("hasRole('OWNER')")
  public BrandIdentityResponse approve(
      Authentication authentication,
      @PathVariable UUID identityId,
      @Valid @RequestBody VersionRequest request) {
    var principal = (CadencePrincipal) authentication.getPrincipal();
    return BrandIdentityResponse.from(
        identities.approve(
            identityId, principal.workspaceIds(), request.expectedVersion(), principal.userId()));
  }
}
