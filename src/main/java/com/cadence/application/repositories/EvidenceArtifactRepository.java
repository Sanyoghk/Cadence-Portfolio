package com.cadence.application.repositories;

import com.cadence.domain.EvidenceArtifact;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceArtifactRepository extends JpaRepository<EvidenceArtifact, UUID> {
  @Query(
      "select a from EvidenceArtifact a where a.evidence.id = :evidenceId and a.sourceChecksum = :sourceChecksum and a.sourceVersion = :sourceVersion and a.kind = :kind")
  Optional<EvidenceArtifact> findByEvidenceIdAndSourceChecksumAndSourceVersionAndKind(
      UUID evidenceId, String sourceChecksum, long sourceVersion, EvidenceArtifact.Kind kind);
}
