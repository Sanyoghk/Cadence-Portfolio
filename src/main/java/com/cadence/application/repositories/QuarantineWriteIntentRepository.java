package com.cadence.application.repositories;

import com.cadence.domain.QuarantineWriteIntent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuarantineWriteIntentRepository
    extends JpaRepository<QuarantineWriteIntent, UUID> {
  /** Read-only recovery inventory. A returned row alone never authorizes object deletion. */
  @Query(
      """
      select i from QuarantineWriteIntent i
      where i.registeredAt is null and i.startedAt < :cutoff
        and not exists (
          select e.id from BrandEvidence e
          where e.sourceReference = concat('quarantine:', i.objectKey)
             or e.fetchedReference = concat('quarantine:', i.objectKey))
      order by i.startedAt, i.id
      """)
  List<QuarantineWriteIntent> findUnresolvedWithoutReference(Instant cutoff, Pageable pageable);
}
