package com.cadence.application.repositories;

import com.cadence.domain.PostStatus;
import com.cadence.domain.SocialPost;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialPostRepository extends JpaRepository<SocialPost, UUID> {

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<SocialPost> findAllByStrategyId(UUID strategyId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<SocialPost> findAllByStrategyBrandId(UUID brandId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Page<SocialPost> findAllByStrategyBrandWorkspaceId(UUID workspaceId, Pageable pageable);

  @EntityGraph(attributePaths = {"strategy", "strategy.brand", "strategy.brand.workspace"})
  Optional<SocialPost> findByIdAndStrategyBrandWorkspaceId(UUID id, UUID workspaceId);

  Page<SocialPost> findAllByStatusAndScheduledAtBetween(
      PostStatus status, Instant from, Instant to, Pageable pageable);
}
