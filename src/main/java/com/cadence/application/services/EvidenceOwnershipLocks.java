package com.cadence.application.services;

import com.cadence.application.repositories.BrandEvidenceRepository;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.BrandEvidence;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lock parents before children; a join with FOR UPDATE does not specify row lock order. */
@Service
public class EvidenceOwnershipLocks {
  private final WorkspaceRepository workspaces;
  private final BrandRepository brands;
  private final BrandEvidenceRepository evidence;

  public EvidenceOwnershipLocks(
      WorkspaceRepository workspaces, BrandRepository brands, BrandEvidenceRepository evidence) {
    this.workspaces = workspaces;
    this.brands = brands;
    this.evidence = evidence;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<BrandEvidence> findLocked(UUID id) {
    var ownership = evidence.findOwnership(id);
    if (ownership.isEmpty()) return Optional.empty();
    var ids = ownership.get();
    if (workspaces.findLockedById(ids.getWorkspaceId()).isEmpty()
        || brands
            .findLockedWithWorkspaceIn(ids.getBrandId(), Set.of(ids.getWorkspaceId()))
            .isEmpty()) {
      return Optional.empty();
    }
    return evidence.findLockedById(id);
  }
}
