package com.cadence.application.repositories;

import com.cadence.domain.BrandAnalysisAttempt;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandAnalysisAttemptRepository extends JpaRepository<BrandAnalysisAttempt, UUID> {
  Optional<BrandAnalysisAttempt> findByIdAndBrandId(UUID id, UUID brandId);

  Optional<BrandAnalysisAttempt>
      findFirstByBrandIdAndBrandVersionAndWorkspaceVersionAndStatusOrderByStartedAtDescIdDesc(
          UUID brandId,
          long brandVersion,
          long workspaceVersion,
          BrandAnalysisAttempt.Status status);

  Optional<BrandAnalysisAttempt> findByBrandIdAndStatus(
      UUID brandId, BrandAnalysisAttempt.Status status);
}
