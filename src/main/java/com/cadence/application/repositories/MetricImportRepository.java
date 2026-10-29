package com.cadence.application.repositories;

import com.cadence.domain.MetricImport;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface MetricImportRepository extends JpaRepository<MetricImport, UUID> {
  /** Standard SQL transaction time: one authoritative ingestion instant for an atomic batch. */
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  @Query(value = "select current_timestamp", nativeQuery = true)
  Instant transactionTime();

  Optional<MetricImport> findByBrandIdAndActorIdAndClientKey(UUID brand, UUID actor, String key);

  long countByBrandId(UUID brand);

  interface Target {
    UUID getBrandId();

    UUID getWorkspaceId();

    UUID getActorId();
  }

  @Query(
      "select e.brand.id as brandId,e.brand.workspace.id as workspaceId,e.actorId as actorId from MetricImport e where e.id=:id")
  Optional<Target> target(UUID id);

  @Query("select e.id from MetricImport e where e.job.id=:job and e.brand.workspace.id in :scope")
  Optional<UUID> scopedJobId(UUID job, Set<UUID> scope);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from MetricImport e where e.id=:id")
  Optional<MetricImport> locked(UUID id);

  @Query(
      "select e.id from MetricImport e where (e.job.status in(com.cadence.domain.JobStatus.PENDING,com.cadence.domain.JobStatus.FAILED) and e.job.nextAttemptAt<=:now) or(e.job.status=com.cadence.domain.JobStatus.RUNNING and e.job.leaseExpiresAt<=:now) order by e.submittedAt,e.id")
  List<UUID> candidates(Instant now, Pageable page);
}
