package com.cadence.security;

import com.cadence.domain.UserRole;
import java.util.Set;
import java.util.UUID;

public record CadencePrincipal(
    UUID userId, String externalSubject, UserRole role, Set<UUID> workspaceIds) {

  public CadencePrincipal {
    workspaceIds = Set.copyOf(workspaceIds);
  }
}
