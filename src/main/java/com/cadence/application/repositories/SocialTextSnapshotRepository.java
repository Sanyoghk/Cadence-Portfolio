package com.cadence.application.repositories;

import com.cadence.domain.SocialTextSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialTextSnapshotRepository extends JpaRepository<SocialTextSnapshot, UUID> {
  Optional<SocialTextSnapshot> findByEvidenceIdAndSourceVersion(
      UUID evidenceId, long sourceVersion);

  Optional<SocialTextSnapshot> findByIdAndEvidenceId(UUID id, UUID evidenceId);

  long countByEvidenceId(UUID evidenceId);
}
