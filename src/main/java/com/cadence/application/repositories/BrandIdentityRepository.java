package com.cadence.application.repositories;

import com.cadence.domain.BrandIdentity;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BrandIdentityRepository extends JpaRepository<BrandIdentity, UUID> {
  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Optional<BrandIdentity> findByIdAndBrandWorkspaceIdIn(UUID id, Set<UUID> scope);

  @EntityGraph(attributePaths = {"brand", "brand.workspace"})
  Page<BrandIdentity> findAllByBrandId(UUID brandId, Pageable page);

  Optional<BrandIdentity> findTopByBrandIdOrderByVersionDesc(UUID brandId);

  @Query("select i.brand.id from BrandIdentity i where i.id=:id and i.brand.workspace.id in :scope")
  Optional<UUID> findBrandIdIn(UUID id, Set<UUID> scope);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from BrandIdentity i where i.id=:id and i.brand.id=:brandId")
  Optional<BrandIdentity> findLockedByIdAndBrandId(UUID id, UUID brandId);
}
