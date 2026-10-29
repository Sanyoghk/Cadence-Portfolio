package com.cadence.application.repositories;

import com.cadence.domain.UrlTextSnapshot;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlTextSnapshotRepository extends JpaRepository<UrlTextSnapshot, UUID> {
  Optional<UrlTextSnapshot> findByEvidenceIdAndSourceVersion(UUID evidenceId, long sourceVersion);
}
