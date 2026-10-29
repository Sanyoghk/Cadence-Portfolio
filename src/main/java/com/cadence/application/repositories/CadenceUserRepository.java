package com.cadence.application.repositories;

import com.cadence.domain.CadenceUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CadenceUserRepository extends JpaRepository<CadenceUser, UUID> {

  @org.springframework.data.jpa.repository.Query(
      "select count(w)>0 from CadenceUser u join u.workspaces w where u.id=:actor and w.id=:workspace")
  boolean hasWorkspaceMembership(UUID actor, UUID workspace);

  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from CadenceUser u where u.id = :id")
  Optional<CadenceUser> findLockedById(UUID id);

  @org.springframework.data.jpa.repository.Query(
      "select count(w) from CadenceUser u join u.workspaces w where u.id = :id")
  long countWorkspaceMemberships(UUID id);

  @EntityGraph(attributePaths = "workspaces")
  Optional<CadenceUser> findByExternalSubject(String externalSubject);

  @EntityGraph(attributePaths = "workspaces")
  Optional<CadenceUser> findWithWorkspacesById(UUID id);
}
