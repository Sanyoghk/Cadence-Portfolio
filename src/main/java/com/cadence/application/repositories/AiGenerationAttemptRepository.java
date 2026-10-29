package com.cadence.application.repositories;

import com.cadence.domain.AiGenerationAttempt;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiGenerationAttemptRepository extends JpaRepository<AiGenerationAttempt, UUID> {
  Optional<AiGenerationAttempt> findByOperationIdAndAttemptNumber(UUID operation, int attempt);
}
