package com.cadence.application.repositories;

import com.cadence.domain.MetricAttribution;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MetricAttributionRepository extends JpaRepository<MetricAttribution, UUID> {
  Optional<MetricAttribution> findTopByObservationIdOrderByAttributionNumberDesc(
      UUID observationId);

  Optional<MetricAttribution> findByObservationIdAndAttributionNumber(
      UUID observationId, int number);

  long countByObservationPostBrandId(UUID brandId);
}
