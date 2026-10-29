package com.cadence.application.repositories;

import com.cadence.domain.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface RecommendationInsightRepository
    extends JpaRepository<RecommendationInsight, UUID> {
  Optional<RecommendationInsight> findByActorIdAndWorkspaceIdAndKeyDigest(
      UUID actor, UUID workspace, String key);

  @Query(
      "select r.brand.id from RecommendationInsight r where r.id=:id and r.workspaceId in :scope")
  Optional<UUID> findBrandIdIn(UUID id, Set<UUID> scope);

  Page<RecommendationInsight> findByBrandId(UUID brand, Pageable page);

  long countByBrandId(UUID brand);
}
