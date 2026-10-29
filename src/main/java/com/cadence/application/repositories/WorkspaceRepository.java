package com.cadence.application.repositories;

import com.cadence.domain.Workspace;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select w from Workspace w where w.id = :id")
  Optional<Workspace> findLockedById(UUID id);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query(
      "select w from Workspace w where w.id = :id and w.id in :ids")
  Optional<Workspace> findLockedByIdAndIdIn(UUID id, Set<UUID> ids);

  Page<Workspace> findAllByIdIn(Set<UUID> ids, Pageable pageable);

  Optional<Workspace> findByIdAndIdIn(UUID id, Set<UUID> ids);
}
