package com.cadence.application.repositories;

import com.cadence.domain.BrandAnalysisSource;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandAnalysisSourceRepository extends JpaRepository<BrandAnalysisSource, UUID> {
  List<BrandAnalysisSource> findByAttemptIdOrderByPositionAsc(UUID attemptId);
}
