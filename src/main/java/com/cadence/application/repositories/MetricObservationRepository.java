package com.cadence.application.repositories;

import com.cadence.domain.MetricObservation;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MetricObservationRepository extends JpaRepository<MetricObservation, UUID> {
  interface Target {
    UUID getBrandId();

    UUID getWorkspaceId();

    UUID getPostId();
  }

  @Query(
      "select o.post.brand.id as brandId,o.post.brand.workspace.id as workspaceId,o.post.id as postId from MetricObservation o where o.id=:id")
  Optional<Target> target(UUID id);

  Optional<MetricObservation> findTopByPostIdAndObservedAtOrderByRevisionNumberDesc(
      UUID post, Instant observedAt);

  long countByPostBrandId(UUID brand);

  @Query(
      "select o from MetricObservation o where o.post.id=:post and o.observedAt>=:from and o.observedAt<:until and (:history=true or not exists(select n.id from MetricObservation n where n.post=o.post and n.observedAt=o.observedAt and n.revisionNumber>o.revisionNumber)) order by o.observedAt,o.revisionNumber,o.id")
  Page<MetricObservation> observations(
      UUID post, Instant from, Instant until, boolean history, Pageable page);
}
