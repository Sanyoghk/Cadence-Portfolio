package com.cadence.presentation;

import com.cadence.application.usecases.BuildStrategyUseCase;
import com.cadence.application.usecases.ManageStrategyUseCase;
import com.cadence.application.usecases.ReadStrategyUseCase;
import com.cadence.http.ApiPagination;
import com.cadence.presentation.dto.PageResponse;
import com.cadence.presentation.dto.StrategyResponse;
import com.cadence.presentation.dto.UpdateStrategyRequest;
import com.cadence.presentation.dto.VersionRequest;
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
public class StrategyController {
  private final BuildStrategyUseCase build;
  private final ReadStrategyUseCase read;
  private final ManageStrategyUseCase manage;

  public StrategyController(
      BuildStrategyUseCase build, ReadStrategyUseCase read, ManageStrategyUseCase manage) {
    this.build = build;
    this.read = read;
    this.manage = manage;
  }

  @PatchMapping("/strategies/{strategyId}")
  @PreAuthorize("hasRole('OWNER')")
  public ResponseEntity<StrategyResponse> update(
      Authentication authentication,
      @PathVariable UUID strategyId,
      @Valid @RequestBody UpdateStrategyRequest request) {
    var principal = (CadencePrincipal) authentication.getPrincipal();
    var result =
        manage.updatePlaybook(
            strategyId,
            principal.workspaceIds(),
            request.expectedVersion(),
            request.content(),
            request.playbook());
    var response = StrategyResponse.from(result.details());
    return result.created()
        ? ResponseEntity.created(URI.create("/api/v1/strategies/" + response.id())).body(response)
        : ResponseEntity.ok(response);
  }

  @PostMapping("/strategies/{strategyId}/approval")
  @PreAuthorize("hasRole('OWNER')")
  public StrategyResponse approve(
      Authentication authentication,
      @PathVariable UUID strategyId,
      @Valid @RequestBody VersionRequest request) {
    var principal = (CadencePrincipal) authentication.getPrincipal();
    return StrategyResponse.from(
        manage.approve(
            strategyId, principal.workspaceIds(), request.expectedVersion(), principal.userId()));
  }

  @PostMapping("/brands/{brandId}/strategies")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public ResponseEntity<StrategyResponse> create(
      Authentication authentication, @PathVariable UUID brandId) {
    var scope = ((CadencePrincipal) authentication.getPrincipal()).workspaceIds();
    var created = build.execute(brandId, scope);
    return ResponseEntity.created(URI.create("/api/v1/strategies/" + created.id()))
        .body(StrategyResponse.from(read.findById(created.id(), scope)));
  }

  @GetMapping("/brands/{brandId}/strategies")
  @PreAuthorize("hasRole('OWNER') and @access.canAccessBrand(authentication, #brandId)")
  public PageResponse<StrategyResponse> list(
      Authentication authentication,
      @PathVariable UUID brandId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "version") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    return PageResponse.from(
        read.findAll(
            brandId,
            ((CadencePrincipal) authentication.getPrincipal()).workspaceIds(),
            ApiPagination.request(
                page, size, sort, direction, "version", "createdAt", "updatedAt")),
        StrategyResponse::from);
  }

  @GetMapping("/strategies/{strategyId}")
  @PreAuthorize("hasRole('OWNER')")
  public StrategyResponse findById(Authentication authentication, @PathVariable UUID strategyId) {
    return StrategyResponse.from(
        read.findById(
            strategyId, ((CadencePrincipal) authentication.getPrincipal()).workspaceIds()));
  }
}
