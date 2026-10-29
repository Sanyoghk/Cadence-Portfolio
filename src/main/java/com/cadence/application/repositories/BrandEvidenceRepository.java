package com.cadence.application.repositories;

import com.cadence.domain.BrandEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandEvidenceRepository extends JpaRepository<BrandEvidence, UUID> {

  @org.springframework.data.jpa.repository.Query(
      "select count(e) from BrandEvidence e where e.brand.workspace.id = :workspaceId")
  long countInWorkspace(UUID workspaceId);

  @org.springframework.data.jpa.repository.Query(
      "select count(e) from BrandEvidence e where e.brand.workspace.id = :workspaceId and e.capturedAt >= :since")
  long countRecentInWorkspace(UUID workspaceId, java.time.Instant since);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select e from BrandEvidence e where e.id = :id")
  Optional<BrandEvidence> findLockedById(UUID id);

  interface Ownership {
    UUID getBrandId();

    UUID getWorkspaceId();
  }

  @org.springframework.data.jpa.repository.Query(
      "select e.brand.id as brandId, e.brand.workspace.id as workspaceId from BrandEvidence e where e.id = :id")
  Optional<Ownership> findOwnership(UUID id);

  @org.springframework.data.jpa.repository.Query(
      "select e.brand.id as brandId, e.brand.workspace.id as workspaceId from BrandEvidence e where e.id = :id and e.brand.workspace.id in :scope")
  Optional<Ownership> findOwnershipIn(UUID id, java.util.Set<UUID> scope);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select e from BrandEvidence e where e.id = :id and e.brand.id = :brandId")
  Optional<BrandEvidence> findLockedByIdAndBrandId(UUID id, UUID brandId);

  @EntityGraph(attributePaths = "brand.workspace")
  Optional<BrandEvidence> findByBrandIdAndChecksum(UUID brandId, String checksum);

  @EntityGraph(attributePaths = "brand.workspace")
  Page<BrandEvidence> findAllByBrandId(UUID brandId, Pageable pageable);
}
