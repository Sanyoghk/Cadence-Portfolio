package com.cadence.application.repositories;

import com.cadence.domain.DurableJob;
import com.cadence.domain.JobStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface DurableJobRepository extends JpaRepository<DurableJob, UUID> {

  /** Only evidence ingest jobs have a supported user-facing ownership relation today. */
  @Query(
      """
      select job from DurableJob job
      where job.id = :jobId and job.type = 'BRAND_EVIDENCE_INGEST'
        and exists (
          select evidence.id from BrandEvidence evidence
          where job.payloadReference = concat('evidence:', cast(evidence.id as string))
            and evidence.brand.workspace.id in :workspaceIds
        )
      """)
  Optional<DurableJob> findEvidenceJobInWorkspaces(UUID jobId, Set<UUID> workspaceIds);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select j from DurableJob j where j.id = :id")
  Optional<DurableJob> findLockedById(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select j from DurableJob j
      where j.type = 'BRAND_EVIDENCE_INGEST'
        and exists (select e.id from BrandEvidence e
        where j.payloadReference = concat('evidence:', cast(e.id as string))
        and e.type = com.cadence.domain.EvidenceType.URL
        and ((j.status in (com.cadence.domain.JobStatus.PENDING, com.cadence.domain.JobStatus.FAILED)
                and j.nextAttemptAt <= :now)
          or (j.status = com.cadence.domain.JobStatus.RUNNING and j.leaseExpiresAt <= :now)
          or (j.status = com.cadence.domain.JobStatus.DEAD and e.status = com.cadence.domain.EvidenceStatus.PENDING)))
      order by j.nextAttemptAt, j.createdAt
      """)
  List<DurableJob> findUrlFetchCandidates(Instant now, Pageable pageable);

  Optional<DurableJob> findByIdempotencyKey(String idempotencyKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select j from DurableJob j where j.type = 'EVIDENCE_DOCUMENT_EXTRACT'
        and ((j.status in (com.cadence.domain.JobStatus.PENDING, com.cadence.domain.JobStatus.FAILED)
                and j.nextAttemptAt <= :now)
          or (j.status = com.cadence.domain.JobStatus.RUNNING and j.leaseExpiresAt <= :now))
      order by j.nextAttemptAt, j.createdAt
      """)
  List<DurableJob> findArtifactCandidates(Instant now, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select j from DurableJob j
      where (j.type = 'EVIDENCE_MALWARE_SCAN'
          or (j.type = 'BRAND_EVIDENCE_INGEST' and exists (select legacy.id from BrandEvidence legacy
              where j.payloadReference = concat('evidence:', cast(legacy.id as string))
                and legacy.type = com.cadence.domain.EvidenceType.FILE)))
        and exists (select e.id from BrandEvidence e
          where j.payloadReference = concat('evidence:', cast(e.id as string))
                and ((e.type = com.cadence.domain.EvidenceType.FILE
                      and e.status = com.cadence.domain.EvidenceStatus.PENDING)
                  or (e.type = com.cadence.domain.EvidenceType.URL
                      and e.status = com.cadence.domain.EvidenceStatus.FETCHED)))
        and ((j.status in (com.cadence.domain.JobStatus.PENDING, com.cadence.domain.JobStatus.FAILED)
                and j.nextAttemptAt <= :now)
            or (j.status = com.cadence.domain.JobStatus.RUNNING and j.leaseExpiresAt <= :now)
            or j.status = com.cadence.domain.JobStatus.DEAD)
      order by j.nextAttemptAt, j.createdAt
      """)
  List<DurableJob> findEvidenceScanCandidates(Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select job from DurableJob job
      where job.type not in ('AI_GENERATION','CSV_EXPORT','METRICS_IMPORT','CREATOR_REPORT') and job.status in :statuses and job.nextAttemptAt <= :now
      order by job.nextAttemptAt, job.createdAt
      """)
  List<DurableJob> findClaimable(Collection<JobStatus> statuses, Instant now, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
      select job from DurableJob job
      where job.type not in ('AI_GENERATION','CSV_EXPORT','METRICS_IMPORT','CREATOR_REPORT') and job.status = :status and job.leaseExpiresAt <= :now
      order by job.leaseExpiresAt
      """)
  List<DurableJob> findExpiredLeases(JobStatus status, Instant now, Pageable pageable);
}
