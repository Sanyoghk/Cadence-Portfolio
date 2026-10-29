package com.cadence.application.usecases;

import com.cadence.application.results.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class GenerateRecommendationsUseCase {
  private final RecommendationService service;

  public GenerateRecommendationsUseCase(RecommendationService service) {
    this.service = service;
  }

  private UUID actor() {
    return ((CadencePrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .userId();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public RecommendationService.Result generate(
      UUID brand, Set<UUID> scope, String key, RecommendationSources.Request request) {
    return service.generate(actor(), brand, scope, key, request);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public RecommendationService.Result resolve(
      UUID id, Set<UUID> scope, String key, long version, RecommendationResolution.Action action) {
    return service.resolve(actor(), id, scope, key, version, action);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public RecommendationDetails read(UUID id, Set<UUID> scope) {
    return service.read(actor(), id, scope);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<RecommendationSummary> list(UUID brand, Set<UUID> scope, int page, int size) {
    return service.list(actor(), brand, scope, page, size);
  }
}
