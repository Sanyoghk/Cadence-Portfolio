package com.cadence.application.repositories;

import com.cadence.domain.Recommendation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRepository extends JpaRepository<Recommendation, UUID> {

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<Recommendation> findAllByStrategyId(UUID strategyId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<Recommendation> findAllByStrategyBrandId(UUID brandId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<Recommendation> findAllByStrategyBrandWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Optional<Recommendation> findByIdAndStrategyBrandWorkspaceId(UUID id, UUID workspaceId);
}
