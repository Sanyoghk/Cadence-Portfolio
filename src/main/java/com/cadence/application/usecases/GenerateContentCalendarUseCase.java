package com.cadence.application.usecases;

import com.cadence.application.services.AiGenerationService;
import com.cadence.domain.ContentGenerationRequest;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
@PreAuthorize(
    "hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope) and @access.isCurrentUser(authentication,#actor)")
public class GenerateContentCalendarUseCase {
  private final AiGenerationService service;

  public GenerateContentCalendarUseCase(AiGenerationService service) {
    this.service = service;
  }

  public AiGenerationService.View submit(
      UUID actor,
      Set<UUID> scope,
      UUID brand,
      long expected,
      String key,
      ContentGenerationRequest request) {
    return service.submitContent(actor, scope, brand, expected, key, request);
  }

  public AiGenerationService.View regenerate(
      UUID actor,
      Set<UUID> scope,
      UUID post,
      UUID revision,
      long expected,
      String slot,
      ContentGenerationRequest.Field field,
      String key) {
    return service.regenerate(actor, scope, post, revision, expected, slot, field, key);
  }

  public AiGenerationService.ContentResult result(UUID actor, Set<UUID> scope, UUID id) {
    return service.contentResult(actor, scope, id);
  }
}
