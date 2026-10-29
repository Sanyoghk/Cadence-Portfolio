package com.cadence.application.repositories;

import com.cadence.domain.PostRevision;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRevisionRepository extends JpaRepository<PostRevision, UUID> {
  List<PostRevision> findAllByGenerationBatchIdOrderByGenerationSlotId(UUID batch);

  Optional<PostRevision> findTopByPostIdOrderByRevisionNumberDesc(UUID postId);

  Optional<PostRevision> findByIdAndPostId(UUID id, UUID postId);

  org.springframework.data.domain.Page<PostRevision> findAllByPostId(
      UUID postId, org.springframework.data.domain.Pageable page);
}
