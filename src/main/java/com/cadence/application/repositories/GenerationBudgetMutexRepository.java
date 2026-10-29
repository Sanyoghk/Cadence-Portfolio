package com.cadence.application.repositories;

import com.cadence.domain.GenerationBudgetMutex;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface GenerationBudgetMutexRepository
    extends JpaRepository<GenerationBudgetMutex, Integer> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select m from GenerationBudgetMutex m where m.id=1")
  Optional<GenerationBudgetMutex> lock();
}
