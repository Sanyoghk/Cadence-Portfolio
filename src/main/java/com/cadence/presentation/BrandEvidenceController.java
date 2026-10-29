package com.cadence.presentation;

import com.cadence.application.usecases.RegisterBrandEvidenceUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.BrandEvidenceResponse;
import com.cadence.presentation.dto.EvidenceRegistrationResponse;
import com.cadence.presentation.dto.PageResponse;
import com.cadence.presentation.dto.RegisterEvidenceRequest;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/brands/{brandId}/evidence")
public class BrandEvidenceController {

  private final RegisterBrandEvidenceUseCase evidence;

  public BrandEvidenceController(RegisterBrandEvidenceUseCase evidence) {
    this.evidence = evidence;
  }

  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public ResponseEntity<EvidenceRegistrationResponse> register(
      Authentication authentication,
      @PathVariable UUID brandId,
      @Valid @RequestBody RegisterEvidenceRequest request) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return accepted(
        evidence.register(brandId, principal.workspaceIds(), request.type(), request.value()));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public ResponseEntity<EvidenceRegistrationResponse> upload(
      Authentication authentication,
      @PathVariable UUID brandId,
      @RequestPart("file") MultipartFile file) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    try {
      return accepted(
          evidence.registerFile(
              brandId,
              principal.workspaceIds(),
              file.getOriginalFilename(),
              file.getContentType(),
              file.getBytes()));
    } catch (java.io.IOException exception) {
      throw new IllegalArgumentException("Evidence file could not be read", exception);
    }
  }

  private ResponseEntity<EvidenceRegistrationResponse> accepted(
      RegisterBrandEvidenceUseCase.Registration registration) {
    return ResponseEntity.accepted()
        .location(URI.create("/api/v1/jobs/" + registration.job().getId()))
        .cacheControl(CacheControl.noStore())
        .body(EvidenceRegistrationResponse.from(registration));
  }

  @GetMapping
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public PageResponse<BrandEvidenceResponse> findAll(
      Authentication authentication,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {

    return PageResponse.from(
        evidence.findAll(
            brandId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            ApiPagination.request(page, size, "capturedAt", "desc", "capturedAt")),
        BrandEvidenceResponse::from);
  }
}
