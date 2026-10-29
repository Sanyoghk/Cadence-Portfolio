package com.cadence.application.usecases;

import com.cadence.application.results.CsvExportDetails;
import com.cadence.application.services.CsvExportService;
import com.cadence.domain.CsvExportRequest;
import com.cadence.security.CadencePrincipal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class CsvExportUseCase {
  private final CsvExportService service;

  public CsvExportUseCase(CsvExportService service) {
    this.service = service;
  }

  private UUID actor() {
    return ((CadencePrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .userId();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CsvExportDetails submit(
      UUID brand, Set<UUID> scope, String key, CsvExportRequest request) {
    return service.submit(actor(), brand, scope, key, request);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CsvExportDetails status(UUID id, Set<UUID> scope, boolean byJob) {
    return service.status(actor(), scope, id, byJob);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public CsvExportService.Download download(UUID id, Set<UUID> scope) {
    return service.download(actor(), scope, id);
  }
}
