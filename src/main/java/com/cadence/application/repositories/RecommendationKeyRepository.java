package com.cadence.application.repositories;

import com.cadence.domain.RecommendationKey;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationKeyRepository extends JpaRepository<RecommendationKey, UUID> {
  Optional<RecommendationKey> findByActorIdAndWorkspaceIdAndOperationAndKeyDigest(
      UUID actor, UUID workspace, String operation, String key);

  long countByBrandId(UUID brand);
}
