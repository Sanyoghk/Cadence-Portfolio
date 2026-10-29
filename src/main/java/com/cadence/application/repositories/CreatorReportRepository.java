package com.cadence.application.repositories;

import com.cadence.domain.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CreatorReportRepository extends JpaRepository<CreatorReport, UUID> {
  Optional<CreatorReport> findByActorIdAndWorkspaceIdAndKeyDigest(
      UUID actor, UUID workspace, String key);

  long countByBrandId(UUID brand);

  @Query("select r.id from CreatorReport r where r.job.id=:job and r.workspaceId in :scope")
  Optional<UUID> scopedJobId(UUID job, Set<UUID> scope);

  interface Target {
    UUID getBrandId();

    UUID getWorkspaceId();

    UUID getActorId();
  }

  @Query(
      "select r.brand.id as brandId,r.workspaceId as workspaceId,r.actorId as actorId from CreatorReport r where r.id=:id")
  Optional<Target> target(UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from CreatorReport r where r.id=:id")
  Optional<CreatorReport> locked(UUID id);

  @Query(
      "select r.id from CreatorReport r where (r.job.status in(com.cadence.domain.JobStatus.PENDING,com.cadence.domain.JobStatus.FAILED) and r.job.nextAttemptAt<=:now) or (r.job.status=com.cadence.domain.JobStatus.RUNNING and r.job.leaseExpiresAt<=:now) order by r.capturedAt,r.id")
  List<UUID> candidates(Instant now, Pageable page);
}
