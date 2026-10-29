package com.cadence.application.usecases;

import com.cadence.application.results.*;
import com.cadence.application.services.CreatorReportService;
import com.cadence.domain.CreatorReportRequest;
import com.cadence.security.CadencePrincipal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class CreatorReportUseCase {
  private final CreatorReportService service;

  public CreatorReportUseCase(CreatorReportService service) {
    this.service = service;
  }

  private UUID actor() {
    return ((CadencePrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .userId();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CreatorReportDetails submit(
      UUID brand, Set<UUID> scope, String key, CreatorReportRequest request) {
    return service.submit(actor(), brand, scope, key, request);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CreatorReportDetails status(UUID id, Set<UUID> scope, boolean byJob) {
    return service.status(actor(), scope, id, byJob);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CreatorReportSnapshot result(UUID id, Set<UUID> scope) {
    return service.result(actor(), scope, id);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CreatorReportService.Download download(UUID id, Set<UUID> scope) {
    return service.download(actor(), scope, id);
  }
}
