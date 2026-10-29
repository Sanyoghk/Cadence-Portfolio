package com.cadence.application.repositories;

import com.cadence.domain.EvidenceSocialOrigin;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceSocialOriginRepository extends JpaRepository<EvidenceSocialOrigin, UUID> {}
