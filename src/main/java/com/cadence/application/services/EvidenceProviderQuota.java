package com.cadence.application.services;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import com.cadence.domain.SocialEvidenceProvider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("denyAll()")
public class EvidenceProviderQuota {
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final EvidenceProviderCallRepository calls;
  private final EvidenceProviderCapacityRepository capacity;
  private final ApprovedSocialEvidenceSource source;
  private final Clock clock;
  private final int hourlyLimit;
  private final int providerHourlyLimit;

  public EvidenceProviderQuota(
      BrandRepository brands,
      WorkspaceRepository workspaces,
      EvidenceProviderCallRepository calls,
      EvidenceProviderCapacityRepository capacity,
      ApprovedSocialEvidenceSource source,
      Clock clock,
      @Value("${cadence.evidence.social.max-workspace-calls-per-hour:100}") int hourlyLimit,
      @Value("${cadence.evidence.social.max-provider-calls-per-hour:50}") int providerHourlyLimit) {
    if (hourlyLimit < 1
        || hourlyLimit > 1000
        || providerHourlyLimit < 1
        || providerHourlyLimit > 500)
      throw new IllegalArgumentException("Invalid social provider quota configuration");
    this.brands = brands;
    this.workspaces = workspaces;
    this.calls = calls;
    this.capacity = capacity;
    this.source = source;
    this.clock = clock;
    this.hourlyLimit = hourlyLimit;
    this.providerHourlyLimit = providerHourlyLimit;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication, #scope)")
  public UUID reserve(UUID brandId, Set<UUID> scope, SocialEvidenceProvider provider) {
    if (provider == null) throw new IllegalArgumentException("Social provider is required");
    Set<UUID> immutableScope = Set.copyOf(scope);
    // A scoped scalar preflight rejects foreign brands before global quota access and avoids
    // loading a stale managed Brand before waiting on the admission locks.
    UUID workspaceId =
        brands
            .findWorkspaceIdIn(brandId, immutableScope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    // Unbound clients must not consume a shared provider budget. This is a local, no-I/O check.
    try {
      source.requireApprovedBinding(brandId, provider);
    } catch (RuntimeException unavailableBinding) {
      throw new SocialEvidenceUnavailableException();
    }
    // Every replica takes locks in the same order. No external I/O holds these locks.
    capacity
        .findLockedByProvider(provider)
        .orElseThrow(com.cadence.application.exceptions.SocialEvidenceUnavailableException::new);
    Workspace workspace = workspaces.findLockedById(workspaceId).orElseThrow();
    Brand brand =
        brands
            .findLockedWithWorkspaceIn(brandId, immutableScope)
            .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
    if (!brand.getWorkspace().getId().equals(workspaceId))
      throw new ResourceNotFoundException("Brand not found");
    if (brand.getStatus() != BrandStatus.ACTIVE || workspace.getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Cannot add evidence to an archived brand or workspace");
    Instant now = Instant.now(clock);
    if (calls.countByProviderAndRequestedAtGreaterThanEqual(
            provider, now.minus(Duration.ofHours(1)))
        >= providerHourlyLimit) throw new EvidenceQuotaExceededException();
    if (calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
            workspace.getId(), provider, now.minus(Duration.ofHours(1)))
        >= hourlyLimit) throw new EvidenceQuotaExceededException();
    return calls
        .saveAndFlush(new EvidenceProviderCall(workspace.getId(), brandId, provider, now))
        .getId();
  }
}
