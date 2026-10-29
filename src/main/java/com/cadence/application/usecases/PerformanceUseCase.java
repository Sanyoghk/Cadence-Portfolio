package com.cadence.application.usecases;

import com.cadence.application.results.*;
import com.cadence.application.services.PerformanceService;
import com.cadence.domain.PerformanceQuery;
import com.cadence.security.CadencePrincipal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class PerformanceUseCase {
  private final PerformanceService service;

  public PerformanceUseCase(PerformanceService service) {
    this.service = service;
  }

  private UUID actor() {
    return ((CadencePrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .userId();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public PerformanceReport read(UUID brand, Set<UUID> scope, PerformanceQuery query) {
    return service.read(actor(), brand, scope, query);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricAttributionDetails attribute(
      UUID observation, Set<UUID> scope, UUID revision, long version, String pillar) {
    return service.attribute(actor(), scope, observation, revision, version, pillar, 0);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricAttributionDetails reviseAttribution(
      UUID observation,
      Set<UUID> scope,
      UUID revision,
      long version,
      String pillar,
      int expectedAttributionVersion) {
    return service.attribute(
        actor(), scope, observation, revision, version, pillar, expectedAttributionVersion);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricAttributionDetails attribution(UUID observation, Set<UUID> scope) {
    return service.attribution(actor(), scope, observation, null);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricAttributionDetails attributionVersion(
      UUID observation, Set<UUID> scope, Integer number) {
    return service.attribution(actor(), scope, observation, number);
  }
}
