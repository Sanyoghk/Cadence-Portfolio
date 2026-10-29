package com.cadence.application.repositories;

import com.cadence.domain.ContentGenerationBatch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentGenerationBatchRepository
    extends JpaRepository<ContentGenerationBatch, UUID> {}
