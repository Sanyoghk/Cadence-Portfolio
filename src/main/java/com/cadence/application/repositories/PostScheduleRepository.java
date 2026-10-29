package com.cadence.application.repositories;

import com.cadence.domain.PostScheduleEvent;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface PostScheduleRepository extends JpaRepository<PostScheduleEvent, UUID> {
  Optional<PostScheduleEvent> findTopByPostIdOrderByEventNumberDesc(UUID postId);

  Page<PostScheduleEvent> findAllByPostId(UUID postId, Pageable page);

  @Query(
      "select e from PostScheduleEvent e where e.brandId=:brandId and e.action=com.cadence.domain.PostScheduleEvent.Action.SCHEDULE and e.scheduledAt>=:from and e.scheduledAt<:until and not exists(select n.id from PostScheduleEvent n where n.post=e.post and n.eventNumber>e.eventNumber)")
  Page<PostScheduleEvent> calendar(UUID brandId, Instant from, Instant until, Pageable page);

  @Query(
      "select e from PostScheduleEvent e where e.brandId=:brandId and e.action=com.cadence.domain.PostScheduleEvent.Action.SCHEDULE and e.scheduledAt in :times and not exists(select n.id from PostScheduleEvent n where n.post=e.post and n.eventNumber>e.eventNumber)")
  List<PostScheduleEvent> slots(UUID brandId, Set<Instant> times);
}
