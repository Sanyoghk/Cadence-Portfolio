package com.cadence.application.repositories;

import com.cadence.domain.EvidenceProviderCapacity;
import com.cadence.domain.SocialEvidenceProvider;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceProviderCapacityRepository
    extends JpaRepository<EvidenceProviderCapacity, SocialEvidenceProvider> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from EvidenceProviderCapacity s where s.provider = :provider")
  Optional<EvidenceProviderCapacity> findLockedByProvider(SocialEvidenceProvider provider);
}
