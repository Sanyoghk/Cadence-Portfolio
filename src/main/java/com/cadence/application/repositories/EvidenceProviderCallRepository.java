package com.cadence.application.repositories;

import com.cadence.domain.EvidenceProviderCall;
import com.cadence.domain.SocialEvidenceProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EvidenceProviderCallRepository extends JpaRepository<EvidenceProviderCall, UUID> {
  long countByProviderAndRequestedAtGreaterThanEqual(
      SocialEvidenceProvider provider, Instant after);

  long countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
      UUID workspaceId, SocialEvidenceProvider provider, Instant after);

  @Query(
      "select c from EvidenceProviderCall c where c.requestedAt < :before and not exists (select o from EvidenceSocialOrigin o where o.providerCallId = c.id) order by c.requestedAt, c.id")
  List<EvidenceProviderCall> findUnreferencedBefore(Instant before, Pageable pageable);
}
