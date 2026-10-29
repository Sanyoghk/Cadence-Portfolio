package com.cadence.application.repositories;

import com.cadence.domain.CsvExportBatch;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CsvExportRepository extends JpaRepository<CsvExportBatch, UUID> {
  Optional<CsvExportBatch> findByBrandIdAndActorIdAndClientKey(UUID brand, UUID actor, String key);

  long countByBrandId(UUID brand);

  @Query(
      "select e.id from CsvExportBatch e where e.job.id=:jobId and e.brand.workspace.id in :scope")
  Optional<UUID> scopedJobId(UUID jobId, Set<UUID> scope);

  interface Target {
    UUID getBrandId();

    UUID getWorkspaceId();

    UUID getActorId();
  }

  @Query(
      "select e.brand.id as brandId,e.brand.workspace.id as workspaceId,e.actorId as actorId from CsvExportBatch e where e.id=:id")
  Optional<Target> target(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from CsvExportBatch e where e.id=:id")
  Optional<CsvExportBatch> locked(UUID id);

  @Query(
      "select e.id from CsvExportBatch e where (e.job.status in (com.cadence.domain.JobStatus.PENDING,com.cadence.domain.JobStatus.FAILED) and e.job.nextAttemptAt<=:now) or (e.job.status=com.cadence.domain.JobStatus.RUNNING and e.job.leaseExpiresAt<=:now) order by e.capturedAt,e.id")
  List<UUID> candidates(Instant now, Pageable page);
}
