package com.cadence.application.usecases;

import com.cadence.application.results.MetricImportDetails;
import com.cadence.application.services.MetricImportService;
import com.cadence.security.CadencePrincipal;
import java.time.Instant;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class ImportMetricsUseCase {
  private final MetricImportService service;

  public ImportMetricsUseCase(MetricImportService service) {
    this.service = service;
  }

  private UUID actor() {
    return ((CadencePrincipal)
            SecurityContextHolder.getContext().getAuthentication().getPrincipal())
        .userId();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricImportDetails submit(
      UUID brand, Set<UUID> scope, String key, String origin, byte[] raw) {
    return service.submit(actor(), brand, scope, key, origin, raw);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricImportDetails status(UUID id, Set<UUID> scope, boolean byJob) {
    return service.status(actor(), scope, id, byJob);
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public MetricImportService.ObservationPage read(
      UUID post,
      Set<UUID> scope,
      Instant from,
      Instant until,
      boolean history,
      int page,
      int size) {
    return service.read(actor(), scope, post, from, until, history, page, size);
  }
}
