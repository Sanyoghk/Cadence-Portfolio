package com.cadence.application.repositories;

import com.cadence.domain.MediaAsset;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
  @Query("select a.brand.id from MediaAsset a where a.id=:id and a.brand.workspace.id in :scope")
  Optional<UUID> findBrandIdIn(UUID id, Set<UUID> scope);

  @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from MediaAsset a where a.id=:id and a.brand.id=:brandId")
  Optional<MediaAsset> findLocked(UUID id, UUID brandId);

  Page<MediaAsset> findAllByBrandId(UUID brandId, Pageable page);

  long countByBrandId(UUID brandId);

  @Query("select coalesce(sum(a.byteSize),0) from MediaAsset a where a.brand.id=:brandId")
  long reservedBytes(UUID brandId);
}
