package com.cadence.security;

import com.cadence.application.repositories.BrandRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("access")
public class ObjectAuthorization {

  private final BrandRepository brands;

  public ObjectAuthorization(BrandRepository brands) {
    this.brands = brands;
  }

  public boolean canAccessWorkspace(Authentication authentication, UUID workspaceId) {
    CadencePrincipal principal = principal(authentication);
    return principal != null && principal.workspaceIds().contains(workspaceId);
  }

  public boolean canAccessBrand(Authentication authentication, UUID brandId) {
    CadencePrincipal principal = principal(authentication);
    return principal != null
        && principal.workspaceIds().stream()
            .anyMatch(workspaceId -> brands.existsByIdAndWorkspaceId(brandId, workspaceId));
  }

  /** An internal caller may narrow the authenticated scope, but cannot expand it. */
  public boolean permitsWorkspaceScope(Authentication authentication, Set<UUID> workspaceIds) {
    CadencePrincipal principal = principal(authentication);
    return principal != null
        && workspaceIds != null
        && principal.workspaceIds().containsAll(workspaceIds);
  }

  public boolean isCurrentUser(Authentication authentication, UUID userId) {
    CadencePrincipal principal = principal(authentication);
    return principal != null && userId != null && userId.equals(principal.userId());
  }

  private CadencePrincipal principal(Authentication authentication) {
    return authentication != null
            && authentication.isAuthenticated()
            && authentication.getPrincipal() instanceof CadencePrincipal value
        ? value
        : null;
  }
}
