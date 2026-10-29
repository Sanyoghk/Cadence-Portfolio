package com.cadence.application.repositories;

import com.cadence.domain.AiGenerationOperation;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface AiGenerationOperationRepository
    extends JpaRepository<AiGenerationOperation, UUID> {
  Optional<AiGenerationOperation> findByActorIdAndWorkspaceIdAndKindAndKeyDigest(
      UUID actor, UUID workspace, AiGenerationOperation.Kind kind, String key);

  Optional<AiGenerationOperation> findByJobId(UUID job);

  Optional<AiGenerationOperation> findByResultId(UUID result);

  long countByWorkspaceId(UUID workspace);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from AiGenerationOperation o where o.id=:id")
  Optional<AiGenerationOperation> locked(UUID id);

  @Query(
      "select o.id from AiGenerationOperation o where o.status in (com.cadence.domain.AiGenerationOperation.Status.QUEUED,com.cadence.domain.AiGenerationOperation.Status.RUNNING,com.cadence.domain.AiGenerationOperation.Status.RETRY_WAIT) and (o.deadline<=:now or (o.status=com.cadence.domain.AiGenerationOperation.Status.RUNNING and o.job.leaseExpiresAt<=:now) or (o.status<>com.cadence.domain.AiGenerationOperation.Status.RUNNING and o.job.nextAttemptAt<=:now)) order by o.submittedAt,o.id")
  List<UUID> candidates(java.time.Instant now, Pageable page);
}
