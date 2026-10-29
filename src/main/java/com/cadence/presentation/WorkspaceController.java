package com.cadence.presentation;

import com.cadence.application.usecases.BrandManagementUseCase;
import com.cadence.application.usecases.WorkspaceManagementUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.BrandResponse;
import com.cadence.presentation.dto.CreateBrandRequest;
import com.cadence.presentation.dto.CreateWorkspaceRequest;
import com.cadence.presentation.dto.PageResponse;
import com.cadence.presentation.dto.UpdateWorkspaceRequest;
import com.cadence.presentation.dto.VersionRequest;
import com.cadence.presentation.dto.WorkspaceResponse;
import com.cadence.security.CadencePrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

  private final WorkspaceManagementUseCase workspaces;
  private final BrandManagementUseCase brands;

  public WorkspaceController(WorkspaceManagementUseCase workspaces, BrandManagementUseCase brands) {
    this.workspaces = workspaces;
    this.brands = brands;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('OWNER')")
  public WorkspaceResponse create(
      Authentication authentication, @Valid @RequestBody CreateWorkspaceRequest request) {
    CadencePrincipal owner = (CadencePrincipal) authentication.getPrincipal();
    return WorkspaceResponse.from(
        workspaces.create(owner.userId(), request.name(), request.industry()));
  }

  @GetMapping
  @PreAuthorize("hasRole('OWNER')")
  public PageResponse<WorkspaceResponse> findAll(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "name") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return PageResponse.from(
        workspaces.findAll(
            principal.workspaceIds(),
            ApiPagination.request(page, size, sort, direction, "name", "industry")),
        WorkspaceResponse::from);
  }

  @GetMapping("/{workspaceId}")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessWorkspace(authentication, #workspaceId)")
  public WorkspaceResponse findById(Authentication authentication, @PathVariable UUID workspaceId) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return WorkspaceResponse.from(workspaces.findById(workspaceId, principal.workspaceIds()));
  }

  @PatchMapping("/{workspaceId}")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessWorkspace(authentication, #workspaceId)")
  public WorkspaceResponse update(
      Authentication authentication,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody UpdateWorkspaceRequest request) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return WorkspaceResponse.from(
        workspaces.update(
            workspaceId,
            principal.workspaceIds(),
            request.expectedVersion(),
            request.name(),
            request.industry()));
  }

  @PostMapping("/{workspaceId}/archive")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessWorkspace(authentication, #workspaceId)")
  public WorkspaceResponse archive(
      Authentication authentication,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody VersionRequest request) {
    CadencePrincipal principal = (CadencePrincipal) authentication.getPrincipal();
    return WorkspaceResponse.from(
        workspaces.archive(workspaceId, principal.workspaceIds(), request.expectedVersion()));
  }

  @GetMapping("/{workspaceId}/brands")
  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessWorkspace(authentication, #workspaceId)")
  public PageResponse<BrandResponse> findBrands(
      Authentication authentication,
      @PathVariable UUID workspaceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "name") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    return PageResponse.from(
        brands.findAll(
            workspaceId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            ApiPagination.request(page, size, sort, direction, "name", "createdAt", "updatedAt")),
        BrandResponse::from);
  }

  @PostMapping("/{workspaceId}/brands")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('OWNER') and @access.canAccessWorkspace(authentication, #workspaceId)")
  public BrandResponse createBrand(
      Authentication authentication,
      @PathVariable UUID workspaceId,
      @Valid @RequestBody CreateBrandRequest request) {
    return BrandResponse.from(
        brands.create(
            workspaceId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            request.name(),
            request.websiteUrl(),
            request.instagramHandle(),
            request.tiktokHandle()));
  }
}
