package com.cadence.application.repositories;

import com.cadence.domain.BrandAnalysisCitation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandAnalysisCitationRepository
    extends JpaRepository<BrandAnalysisCitation, UUID> {
  java.util.List<BrandAnalysisCitation> findByAttemptIdOrderByPositionAsc(UUID attemptId);
}
