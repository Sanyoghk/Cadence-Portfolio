package com.cadence.application.usecases;

import com.cadence.application.ports.BrandAnalysisResult;
import com.cadence.application.services.AiGenerationService;
import com.cadence.domain.AiGenerationOperation;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize("denyAll()")
public class AiGenerationUseCase {
  private final AiGenerationService service;

  public AiGenerationUseCase(AiGenerationService service) {
    this.service = service;
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actor)")
  public AiGenerationService.View submit(
      UUID actor,
      Set<UUID> scope,
      UUID brand,
      AiGenerationOperation.Kind kind,
      long expected,
      String key) {
    return service.submit(actor, scope, brand, kind, expected, key);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actor)")
  public AiGenerationService.View status(UUID actor, Set<UUID> scope, UUID job) {
    return service.status(actor, scope, job);
  }

  @PreAuthorize(
      "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actor)")
  public BrandAnalysisResult result(UUID actor, Set<UUID> scope, UUID id) {
    return service.result(actor, scope, id);
  }
}
