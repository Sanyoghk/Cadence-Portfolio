package com.cadence.application.repositories;

import com.cadence.domain.GenerationReservation;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenerationReservationRepository
    extends JpaRepository<GenerationReservation, UUID> {
  Optional<GenerationReservation> findByRequestId(UUID request);
}
