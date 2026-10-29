package com.cadence.application.repositories;

import com.cadence.domain.ContentPost;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ContentPostRepository extends JpaRepository<ContentPost, UUID> {
  @Query("select p.brand.id from ContentPost p where p.id=:id and p.brand.workspace.id in :scope")
  Optional<UUID> findBrandIdIn(UUID id, Set<UUID> scope);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from ContentPost p where p.id=:id and p.brand.id=:brandId")
  Optional<ContentPost> findLocked(UUID id, UUID brandId);

  Optional<ContentPost> findByIdAndBrandWorkspaceIdIn(UUID id, Set<UUID> scope);

  org.springframework.data.domain.Page<ContentPost> findAllByBrandId(
      UUID brandId, org.springframework.data.domain.Pageable page);

  long countByBrandId(UUID brandId);
}
