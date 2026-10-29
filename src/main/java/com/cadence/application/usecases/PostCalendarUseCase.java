package com.cadence.application.usecases;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.ScheduleDetails;
import com.cadence.application.results.ScheduleDetails.Eligibility;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@PreAuthorize("denyAll()")
@Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
public class PostCalendarUseCase {
  public record Change(
      UUID postId,
      UUID revisionId,
      long expectedRevisionVersion,
      int expectedScheduleVersion,
      PostScheduleEvent.Action action,
      CalendarTime time) {
    public Change {
      if (postId == null
          || revisionId == null
          || expectedRevisionVersion < 0
          || expectedScheduleVersion < 0
          || expectedScheduleVersion >= 1000
          || action == null
          || (action == PostScheduleEvent.Action.SCHEDULE) != (time != null))
        throw new IllegalArgumentException("Invalid schedule change");
    }
  }

  private final PostScheduleRepository schedules;
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;
  private final BrandRepository brands;
  private final WorkspaceRepository workspaces;
  private final CadenceUserRepository users;
  private final StrategyRepository strategies;
  private final Clock clock;

  public PostCalendarUseCase(
      PostScheduleRepository schedules,
      ContentPostRepository posts,
      PostRevisionRepository revisions,
      BrandRepository brands,
      WorkspaceRepository workspaces,
      CadenceUserRepository users,
      StrategyRepository strategies,
      Clock clock) {
    this.schedules = schedules;
    this.posts = posts;
    this.revisions = revisions;
    this.brands = brands;
    this.workspaces = workspaces;
    this.users = users;
    this.strategies = strategies;
    this.clock = clock;
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public List<ScheduleDetails> change(UUID brandId, Set<UUID> scope, List<Change> changes) {
    if (changes == null
        || changes.isEmpty()
        || changes.size() > 84
        || changes.stream().anyMatch(Objects::isNull)
        || changes.stream().map(Change::postId).distinct().count() != changes.size())
      throw new IllegalArgumentException("Schedule batch must contain 1 to 84 distinct Posts");
    parents(brandId, scope, true);
    UUID actor = principal().userId();
    var identities = new HashMap<UUID, ContentPost>();
    for (var c : changes) {
      var post =
          posts
              .findByIdAndBrandWorkspaceIdIn(c.postId(), scope)
              .orElseThrow(PostCalendarUseCase::missing);
      if (!post.getBrand().getId().equals(brandId)) throw missing();
      identities.put(c.postId(), post);
    }
    identities.values().stream()
        .map(p -> p.getStrategy().getId())
        .distinct()
        .sorted()
        .forEach(
            id ->
                strategies
                    .findLockedByIdAndBrandId(id, brandId)
                    .orElseThrow(PostCalendarUseCase::missing));
    identities.keySet().stream()
        .sorted()
        .forEach(id -> posts.findLocked(id, brandId).orElseThrow(PostCalendarUseCase::missing));
    var events = new ArrayList<PostScheduleEvent>();
    Instant now = clock.instant();
    for (var c : changes) {
      var revision = latest(c.postId());
      if (!revision.getId().equals(c.revisionId())
          || revision.getLockVersion() != c.expectedRevisionVersion())
        throw new IllegalStateException("Post revision changed");
      var prior = schedules.findTopByPostIdOrderByEventNumberDesc(c.postId()).orElse(null);
      if ((prior == null ? 0 : prior.getEventNumber()) != c.expectedScheduleVersion())
        throw new IllegalStateException("Schedule changed");
      events.add(new PostScheduleEvent(revision, prior, c.action(), c.time(), actor, now));
    }
    var slots = new HashSet<String>();
    var times = new HashSet<Instant>();
    for (var e : events)
      if (e.getAction() == PostScheduleEvent.Action.SCHEDULE) {
        if (!slots.add(slot(e))) throw new IllegalStateException("Calendar slot occupied");
        times.add(e.getScheduledAt());
      }
    if (!times.isEmpty())
      for (var existing : schedules.slots(brandId, times)) {
        if (!identities.containsKey(existing.getPost().getId())
            && eligibility(existing) == Eligibility.READY
            && slots.contains(slot(existing)))
          throw new IllegalStateException("Calendar slot occupied");
      }
    schedules.saveAllAndFlush(events);
    return events.stream()
        .map(e -> ScheduleDetails.from(e, true, eligibility(e), e.getTimezone()))
        .toList();
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<ScheduleDetails> calendar(
      UUID brandId, Set<UUID> scope, Instant from, Instant until, String timezone, Pageable page) {
    CalendarTime.interval(from, until, timezone);
    page(page, Set.of("scheduledAt", "id"));
    parents(brandId, scope, false);
    return schedules
        .calendar(brandId, from, until, page)
        .map(e -> ScheduleDetails.from(e, true, eligibility(e), timezone));
  }

  @PreAuthorize("hasRole('OWNER') and @access.permitsWorkspaceScope(authentication,#scope)")
  public Page<ScheduleDetails> history(UUID postId, Set<UUID> scope, Pageable page) {
    page(page, Set.of("eventNumber", "id"));
    var brandId = posts.findBrandIdIn(postId, scope).orElseThrow(PostCalendarUseCase::missing);
    parents(brandId, scope, false);
    var current =
        schedules
            .findTopByPostIdOrderByEventNumberDesc(postId)
            .map(PostScheduleEvent::getId)
            .orElse(null);
    return schedules
        .findAllByPostId(postId, page)
        .map(
            e -> {
              boolean head = e.getId().equals(current);
              return ScheduleDetails.from(
                  e, head, head ? eligibility(e) : Eligibility.HISTORICAL, e.getTimezone());
            });
  }

  private Eligibility eligibility(PostScheduleEvent e) {
    if (e.getAction() == PostScheduleEvent.Action.CANCEL) return Eligibility.CANCELLED;
    var p = e.getPost();
    if (p.getBrand().getStatus() != BrandStatus.ACTIVE
        || p.getBrand().getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      return Eligibility.PARENT_ARCHIVED;
    var current = latest(p.getId());
    if (!current.getId().equals(e.getRevision().getId())
        || current.getStatus() != PostRevision.State.APPROVED
        || current.getLockVersion() != e.getRevisionLockVersion()
        || !PostScheduleEvent.checksum(current.content()).equals(e.getContentChecksum()))
      return Eligibility.CONTENT_CHANGED;
    try {
      p.requireCurrentSource();
    } catch (IllegalStateException unavailable) {
      return Eligibility.SOURCE_UNAVAILABLE;
    }
    return Eligibility.READY;
  }

  private String slot(PostScheduleEvent e) {
    return e.getPost().getPlatform() + ":" + e.getScheduledAt();
  }

  private PostRevision latest(UUID id) {
    return revisions
        .findTopByPostIdOrderByRevisionNumberDesc(id)
        .orElseThrow(PostCalendarUseCase::missing);
  }

  private Brand parents(UUID brandId, Set<UUID> scope, boolean active) {
    UUID workspace =
        brands.findWorkspaceIdIn(brandId, scope).orElseThrow(PostCalendarUseCase::missing);
    var actor =
        users
            .findLockedById(principal().userId())
            .orElseThrow(() -> new AccessDeniedException("Calendar access denied"));
    if (!actor.isActive()
        || actor.getRole() != UserRole.OWNER
        || !users.hasWorkspaceMembership(actor.getId(), workspace))
      throw new AccessDeniedException("Calendar access denied");
    var w =
        workspaces
            .findLockedByIdAndIdIn(workspace, scope)
            .orElseThrow(PostCalendarUseCase::missing);
    var b =
        brands.findLockedWithWorkspaceIn(brandId, scope).orElseThrow(PostCalendarUseCase::missing);
    if (active && (w.getStatus() != WorkspaceStatus.ACTIVE || b.getStatus() != BrandStatus.ACTIVE))
      throw new IllegalStateException("Calendar parent archived");
    return b;
  }

  private CadencePrincipal principal() {
    var a = SecurityContextHolder.getContext().getAuthentication();
    if (a == null || !(a.getPrincipal() instanceof CadencePrincipal p))
      throw new AccessDeniedException("Calendar access denied");
    return p;
  }

  private void page(Pageable p, Set<String> allowed) {
    if (p == null
        || p.isUnpaged()
        || p.getPageSize() < 1
        || p.getPageNumber() < 0
        || p.getPageSize() > 100
        || p.getPageNumber() > 10000
        || p.getSort().stream().anyMatch(o -> !allowed.contains(o.getProperty())))
      throw new IllegalArgumentException("Invalid calendar pagination");
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Calendar or Post not found");
  }
}
