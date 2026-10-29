package com.cadence.application.services;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.results.MediaDetails;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Internal policy: actual actor and parent locks, no request principal manufactured by worker. */
@Service
public class CsvExportSources {
  public record Capture(
      List<CsvExportItem> items, List<MediaDetails> media, List<List<String>> rows) {}

  private final AiGenerationSources parents;
  private final ContentPostRepository posts;
  private final PostRevisionRepository revisions;
  private final PostScheduleRepository schedules;
  private final StrategyRepository strategies;
  private final PostMediaPolicy media;

  public CsvExportSources(
      AiGenerationSources parents,
      ContentPostRepository posts,
      PostRevisionRepository revisions,
      PostScheduleRepository schedules,
      StrategyRepository strategies,
      PostMediaPolicy media) {
    this.parents = parents;
    this.posts = posts;
    this.revisions = revisions;
    this.schedules = schedules;
    this.strategies = strategies;
    this.media = media;
  }

  public Brand authorize(UUID actor, UUID brand, Set<UUID> scope) {
    var b = parents.authorize(actor, brand, scope);
    if (b.getStatus() != BrandStatus.ACTIVE
        || b.getWorkspace().getStatus() != WorkspaceStatus.ACTIVE)
      throw new IllegalStateException("Export parent archived");
    return b;
  }

  public void lock(UUID actor, UUID brand, Set<UUID> scope) {
    parents.lockParents(actor, brand, scope);
  }

  public Capture capture(UUID actor, Brand brand, CsvExportRequest request, Instant capturedAt) {
    var selected = new LinkedHashMap<UUID, ContentPost>();
    for (var s : request.selections())
      selected.put(
          s.postId(),
          posts
              .findByIdAndBrandWorkspaceIdIn(s.postId(), Set.of(brand.getWorkspace().getId()))
              .filter(p -> p.getBrand().getId().equals(brand.getId()))
              .orElseThrow(CsvExportSources::missing));
    selected.values().stream()
        .map(p -> p.getStrategy().getId())
        .distinct()
        .sorted()
        .forEach(
            id ->
                strategies
                    .findLockedByIdAndBrandId(id, brand.getId())
                    .orElseThrow(CsvExportSources::missing));
    selected.keySet().stream()
        .sorted()
        .forEach(id -> posts.findLocked(id, brand.getId()).orElseThrow(CsvExportSources::missing));
    var items = new ArrayList<CsvExportItem>();
    var assets = new LinkedHashMap<UUID, MediaDetails>();
    for (var s : request.selections()) {
      var p = selected.get(s.postId());
      p.requireCurrentSource();
      var r =
          revisions
              .findTopByPostIdOrderByRevisionNumberDesc(p.getId())
              .orElseThrow(CsvExportSources::missing);
      var e =
          schedules
              .findTopByPostIdOrderByEventNumberDesc(p.getId())
              .orElseThrow(() -> new IllegalStateException("Scheduled Post required"));
      if (r.getStatus() != PostRevision.State.APPROVED
          || r.getApprovedBy() == null
          || r.getApprovedAt() == null
          || !r.getId().equals(s.revisionId())
          || r.getLockVersion() != s.revisionVersion()
          || e.getEventNumber() != s.scheduleVersion()
          || e.getAction() != PostScheduleEvent.Action.SCHEDULE
          || !e.getRevision().getId().equals(r.getId())
          || e.getRevisionLockVersion() != r.getLockVersion()
          || !e.getContentChecksum().equals(PostScheduleEvent.checksum(r.content()))
          || e.getScheduledAt().isBefore(request.from())
          || !e.getScheduledAt().isBefore(request.until()))
        throw new IllegalStateException("Export source stale or ineligible");
      for (var a : media.capture(p, r.getFormat(), r.getMediaAssetIds())) assets.put(a.id(), a);
      items.add(new CsvExportItem(r, e));
    }
    items.sort(
        Comparator.comparing((CsvExportItem i) -> i.getSchedule().getScheduledAt())
            .thenComparing(i -> i.getRevision().getPost().getId().toString()));
    var rows = new ArrayList<List<String>>();
    for (var i : items) rows.add(row(i, actor, capturedAt, request.timezone(), assets));
    return new Capture(List.copyOf(items), List.copyOf(assets.values()), List.copyOf(rows));
  }

  public Capture current(CsvExportBatch e, UUID actor, Set<UUID> scope) {
    var brand = authorize(actor, e.getBrand().getId(), scope);
    var selections =
        e.getItems().stream()
            .map(
                i ->
                    new CsvExportRequest.Selection(
                        i.getRevision().getPost().getId(),
                        i.getRevision().getId(),
                        i.getRevisionVersion(),
                        i.getSchedule().getEventNumber()))
            .toList();
    var c =
        capture(
            e.getActorId(),
            brand,
            new CsvExportRequest(e.getFrom(), e.getUntil(), e.getTimezone(), selections),
            e.getCapturedAt());
    if (!CsvDocument.checksum(c.rows()).equals(e.getSnapshotChecksum())
        || !c.items().stream()
            .map(i -> i.getSchedule().getId())
            .toList()
            .equals(e.getItems().stream().map(i -> i.getSchedule().getId()).toList()))
      throw new IllegalStateException("Export snapshot changed");
    return c;
  }

  private List<String> row(
      CsvExportItem i,
      UUID actor,
      Instant captured,
      String timezone,
      Map<UUID, MediaDetails> assets) {
    var r = i.getRevision();
    var p = r.getPost();
    var s = i.getSchedule();
    var local = s.getScheduledAt().atZone(CalendarTime.zone(timezone));
    var refs = new ArrayList<String>();
    for (var id : r.getMediaAssetIds()) {
      var a = assets.get(id);
      refs.add(id + ":" + a.checksum() + ":" + a.origin() + ":" + a.moderation());
    }
    return java.util.stream.Stream.of(
            "CADENCE_CSV_V1",
            "APPROVED_CREATIVE_V1",
            "",
            actor,
            captured,
            p.getId(),
            r.getId(),
            r.getRevisionNumber(),
            r.getLockVersion(),
            i.getContentChecksum(),
            p.getPlatform(),
            r.getTitle(),
            r.getCaption(),
            String.join(" ", r.getHashtags()),
            r.getFormat(),
            r.getMediaPrompt(),
            r.getVideoScript(),
            p.getStrategy().getId(),
            p.getSourceRevision(),
            p.getSourceLockVersion(),
            p.getSourceChecksum(),
            s.getId(),
            s.getEventNumber(),
            s.getScheduledAt(),
            s.getTimezone(),
            s.getLocalTime(),
            ZoneOffset.ofTotalSeconds(s.getOffsetSeconds()),
            s.getRulesVersion(),
            timezone,
            local.toLocalDateTime(),
            local.getOffset(),
            r.getAuthoredBy(),
            r.getAuthoredAt(),
            r.getEditedBy(),
            r.getEditedAt(),
            r.getApprovedBy(),
            r.getApprovedAt(),
            refs.isEmpty() ? "TEXT_ONLY" : "DEMO_REFERENCES",
            String.join(";", refs),
            r.getGenerationBatchId(),
            r.getGenerationSlotId())
        .map(o -> o == null ? "" : o.toString())
        .toList();
  }

  private static ResourceNotFoundException missing() {
    return new ResourceNotFoundException("Export source not found");
  }
}
