package com.cadence.application.repositories;

import com.cadence.domain.Brand;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

  @org.springframework.data.jpa.repository.Query(
      "select b.workspace.id from Brand b where b.id = :id and b.workspace.id in :workspaceIds")
  Optional<UUID> findWorkspaceIdIn(UUID id, Set<UUID> workspaceIds);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select b from Brand b join fetch b.workspace w where b.id = :id and w.id in :workspaceIds")
  Optional<Brand> findLockedWithWorkspaceIn(UUID id, Set<UUID> workspaceIds);

  @EntityGraph(attributePaths = "workspace")
  Page<Brand> findAllByWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = "workspace")
  Optional<Brand> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  @EntityGraph(attributePaths = "workspace")
  Optional<Brand> findByIdAndWorkspaceIdIn(UUID id, Set<UUID> workspaceIds);

  boolean existsByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
