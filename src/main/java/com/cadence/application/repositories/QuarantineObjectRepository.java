package com.cadence.application.repositories;

import com.cadence.domain.QuarantineObject;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface QuarantineObjectRepository extends JpaRepository<QuarantineObject, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select q from QuarantineObject q where q.objectKey=:key and q.evidenceId=:evidenceId and q.brandId=:brandId and q.workspaceId=:workspaceId")
  Optional<QuarantineObject> findLockedForEvidence(
      String key, UUID evidenceId, UUID brandId, UUID workspaceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from QuarantineObject q where q.id = :id")
  Optional<QuarantineObject> findLockedById(UUID id);

  @Query(
      "select coalesce(sum(q.retainedBytes), 0) from QuarantineObject q where q.workspaceId = :workspaceId and q.quotaReleased = false")
  long retainedBytes(UUID workspaceId);

  @Query(
      "select count(q) from QuarantineObject q where q.workspaceId = :workspaceId and q.quotaReleased = false")
  long retainedObjects(UUID workspaceId);

  @Query(
      "select count(q) from QuarantineObject q where q.workspaceId = :workspaceId and q.reservedAt > :cutoff")
  long writesAfter(UUID workspaceId, Instant cutoff);

  @Query(
      """
      select q.id from QuarantineObject q
      where q.state <> com.cadence.domain.QuarantineObject.State.REFERENCED and q.nextCleanupAt <= :now
        and not exists (select e.id from BrandEvidence e where
            e.sourceReference = concat('quarantine:', q.objectKey)
            or e.fetchedReference = concat('quarantine:', q.objectKey))
        and not exists (select a.id from EvidenceArtifact a where a.storageReference = concat('quarantine:', q.objectKey))
        and not exists (select i.id from QuarantineWriteIntent i where i.objectKey = q.objectKey and i.registeredAt is not null)
        and not exists (select j.id from DurableJob j where
            (j.payloadReference = q.objectKey or j.payloadReference = concat('quarantine:', q.objectKey))
            and j.status in (com.cadence.domain.JobStatus.PENDING, com.cadence.domain.JobStatus.RUNNING, com.cadence.domain.JobStatus.FAILED))
      order by q.quotaReleased, q.nextCleanupAt, q.id
      """)
  List<UUID> cleanupCandidates(Instant now, Pageable page);

  @Query(
      "select count(e) from BrandEvidence e where e.sourceReference = :reference or e.fetchedReference = :reference")
  long evidenceReferences(String reference);

  @Query(
      "select count(i) from QuarantineWriteIntent i where i.objectKey = :key and i.registeredAt is not null")
  long registeredIntents(String key);

  @Query(
      "select count(j) from DurableJob j where (j.payloadReference = :reference or j.payloadReference = :key) and j.status in (com.cadence.domain.JobStatus.PENDING, com.cadence.domain.JobStatus.RUNNING, com.cadence.domain.JobStatus.FAILED)")
  long activeJobs(String reference, String key);

  @Query("select count(a) from EvidenceArtifact a where a.storageReference = :reference")
  long artifactReferences(String reference);

  /** Historical objects have no trustworthy length. Charge the ingestion upper bound per row. */
  @Query(
      """
      select coalesce(sum(case when e.type = com.cadence.domain.EvidenceType.FILE then 10485760L else 2097152L end), 0)
      from BrandEvidence e where e.brand.workspace.id = :workspaceId
        and ((e.type = com.cadence.domain.EvidenceType.FILE and not exists (
            select q.id from QuarantineObject q where e.sourceReference = concat('quarantine:', q.objectKey)))
          or (e.type = com.cadence.domain.EvidenceType.URL and e.fetchedReference is not null and not exists (
            select q.id from QuarantineObject q where e.fetchedReference = concat('quarantine:', q.objectKey))))
      """)
  long legacyBytes(UUID workspaceId);

  @Query(
      """
      select count(e) from BrandEvidence e where e.brand.workspace.id = :workspaceId
        and ((e.type = com.cadence.domain.EvidenceType.FILE and not exists (
            select q.id from QuarantineObject q where e.sourceReference = concat('quarantine:', q.objectKey)))
          or (e.type = com.cadence.domain.EvidenceType.URL and e.fetchedReference is not null and not exists (
            select q.id from QuarantineObject q where e.fetchedReference = concat('quarantine:', q.objectKey))))
      """)
  long legacyObjects(UUID workspaceId);
}
