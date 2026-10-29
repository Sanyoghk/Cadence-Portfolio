package com.cadence.application.repositories;

import com.cadence.domain.PostMetric;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostMetricRepository extends JpaRepository<PostMetric, UUID> {

  @EntityGraph(attributePaths = "post")
  Page<PostMetric> findAllByPostId(UUID postId, Pageable pageable);

  @EntityGraph(attributePaths = "post")
  Page<PostMetric> findAllByPostStrategyBrandId(UUID brandId, Pageable pageable);

  @EntityGraph(attributePaths = "post")
  Page<PostMetric> findAllByPostStrategyBrandWorkspaceId(UUID workspaceId, Pageable pageable);

  Optional<PostMetric> findByIdAndPostStrategyBrandWorkspaceId(UUID id, UUID workspaceId);
}
