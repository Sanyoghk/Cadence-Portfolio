package com.cadence.application.repositories;

import com.cadence.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationResolutionRepository
    extends JpaRepository<RecommendationResolution, UUID> {
  Optional<RecommendationResolution> findByRecommendationId(UUID id);

  Optional<RecommendationResolution> findByActorIdAndWorkspaceIdAndActionAndKeyDigest(
      UUID actor, UUID workspace, RecommendationResolution.Action action, String key);
}
