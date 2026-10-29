package com.cadence.application.repositories;

import com.cadence.domain.EvidenceAdmissionWithdrawal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceAdmissionWithdrawalRepository
    extends JpaRepository<EvidenceAdmissionWithdrawal, UUID> {
  interface SourceWithdrawal {
    long getSourceVersion();

    UUID getWithdrawnBy();

    java.time.Instant getWithdrawnAt();
  }

  @Query(
      "select w.admission.sourceVersion as sourceVersion, w.withdrawnBy as withdrawnBy, w.withdrawnAt as withdrawnAt from EvidenceAdmissionWithdrawal w where w.admission.evidence.id=:evidenceId order by w.withdrawnAt,w.id")
  java.util.List<SourceWithdrawal> historyForEvidence(
      UUID evidenceId, org.springframework.data.domain.Pageable page);

  @Query(
      "select count(w) > 0 from EvidenceAdmissionWithdrawal w where w.admission.id = :admissionId")
  boolean existsForAdmission(UUID admissionId);

  @Query(
      "select count(w) > 0 from EvidenceAdmissionWithdrawal w where w.admission.evidence.id = :evidenceId and w.admission.sourceVersion = :sourceVersion")
  boolean existsForSourceVersion(UUID evidenceId, long sourceVersion);
}
