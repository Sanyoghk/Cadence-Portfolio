package com.cadence.application.repositories;

import com.cadence.domain.Strategy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyRepository extends JpaRepository<Strategy, UUID> {

  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Page<Strategy> findAllByBrandId(UUID brandId, Pageable pageable);

  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Page<Strategy> findAllByBrandWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Optional<Strategy> findByIdAndBrandWorkspaceId(UUID id, UUID workspaceId);

  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Optional<Strategy> findByIdAndBrandWorkspaceIdIn(UUID id, java.util.Set<UUID> workspaceIds);

  @org.springframework.data.jpa.repository.Query(
      "select s.brand.id from Strategy s where s.id=:id and s.brand.workspace.id in :scope")
  Optional<UUID> findBrandIdIn(UUID id, java.util.Set<UUID> scope);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select s from Strategy s where s.id=:id and s.brand.id=:brandId")
  Optional<Strategy> findLockedByIdAndBrandId(UUID id, UUID brandId);

  Optional<Strategy> findTopByBrandIdOrderByVersionDesc(UUID brandId);

  boolean existsByBrandIdAndVersion(UUID brandId, int version);
}
