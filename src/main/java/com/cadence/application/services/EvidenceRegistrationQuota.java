package com.cadence.application.services;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.ports.EvidenceTransactionIsolation;
import com.cadence.application.repositories.BrandEvidenceRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.WorkspaceStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Counts TEXT and pending URL rows as well as objects; duplicates consume no new row budget. */
@Service
public class EvidenceRegistrationQuota {
  private final WorkspaceRepository workspaces;
  private final BrandEvidenceRepository evidence;
  private final Clock clock;
  private final int maxEvidence;
  private final int maxPerHour;
  private final EvidenceTransactionIsolation isolation;

  public EvidenceRegistrationQuota(
      WorkspaceRepository workspaces,
      BrandEvidenceRepository evidence,
      Clock clock,
      EvidenceTransactionIsolation isolation,
      @Value("${cadence.evidence.registration.max-workspace-evidence:1000}") int maxEvidence,
      @Value("${cadence.evidence.registration.max-workspace-registrations-per-hour:100}")
          int maxPerHour) {
    if (maxEvidence < 1 || maxEvidence > 1_000_000 || maxPerHour < 1 || maxPerHour > 100_000)
      throw new IllegalArgumentException("Invalid evidence registration quota configuration");
    this.workspaces = workspaces;
    this.evidence = evidence;
    this.clock = clock;
    this.maxEvidence = maxEvidence;
    this.maxPerHour = maxPerHour;
    this.isolation = isolation;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void lockWorkspace(UUID workspaceId) {
    isolation.requireReadCommitted();
    var workspace = workspaces.findLockedById(workspaceId).orElseThrow();
    if (workspace.getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Cannot add evidence to an archived workspace");
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void requireAvailable(UUID workspaceId) {
    lockWorkspace(workspaceId);
    if (evidence.countInWorkspace(workspaceId) >= maxEvidence
        || evidence.countRecentInWorkspace(workspaceId, clock.instant().minus(Duration.ofHours(1)))
            >= maxPerHour) throw new EvidenceQuotaExceededException();
  }
}
