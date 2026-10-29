package com.cadence.application.repositories;

import com.cadence.domain.EvidenceScanResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Internal audit persistence; no HTTP exposure. */
public interface EvidenceScanResultRepository extends JpaRepository<EvidenceScanResult, UUID> {
  List<EvidenceScanResult> findByJobIdOrderByAttemptNumber(UUID jobId, Pageable page);

  @Query(
      "select r from EvidenceScanResult r where r.evidence.id = :evidenceId order by r.scannedAt desc, r.createdAt desc, r.id desc")
  List<EvidenceScanResult> latestForEvidence(UUID evidenceId, Pageable page);
}
