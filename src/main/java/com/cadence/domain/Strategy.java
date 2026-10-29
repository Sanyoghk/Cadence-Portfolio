package com.cadence.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "strategies")
public class Strategy extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private int version;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private ApprovalStatus status = ApprovalStatus.DRAFT;

  @Embedded private PublishingCadence cadence;
  @Embedded private StrategyGenerationProvenance generation;

  @Column(name = "revised_from_id", updatable = false)
  private UUID revisedFromId;

  @Column(name = "approved_by")
  private UUID approvedBy;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @ElementCollection
  @CollectionTable(
      name = "strategy_content_pillars",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @OrderColumn(name = "pillar_order")
  private List<ContentPillar> contentPillars = new ArrayList<>();

  @ManyToOne(optional = false)
  @JoinColumn(name = "brand_id", nullable = false, updatable = false)
  private Brand brand;

  @Column(name = "content_format_version", nullable = false)
  private int contentFormatVersion = 1;

  @Column(name = "whitespace_status", length = 30)
  private String whitespaceStatus;

  @Column(name = "edited_by")
  private UUID editedBy;

  @Column(name = "edited_at")
  private Instant editedAt;

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_pillar_ids",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookPillarId> playbookPillarIds = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_audience",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookAudience> playbookAudience = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_channels",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookChannel> playbookChannels = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_goals",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookGoal> playbookGoals = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_tones",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookTone> playbookTones = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_hypotheses",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookHypothesis> playbookHypotheses = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "strategy_playbook_slots",
      joinColumns = @JoinColumn(name = "strategy_id"))
  @jakarta.persistence.OrderBy("entryOrder ASC")
  private List<PlaybookSlot> playbookSlots = new ArrayList<>();

  protected Strategy() {}

  public Strategy(
      Brand brand, int version, PublishingCadence cadence, List<ContentPillar> contentPillars) {
    if (brand == null) {
      throw new IllegalArgumentException("Strategy brand must not be null");
    }
    if (version < 1) {
      throw new IllegalArgumentException("Strategy version must be greater than zero");
    }
    this.brand = brand;
    this.version = version;
    replaceCadence(cadence);
    replaceContentPillars(contentPillars);

    this.status = ApprovalStatus.DRAFT;
  }

  public void approve(UUID actorId, Instant now) {
    requireDraft();
    if (actorId == null || now == null)
      throw new IllegalArgumentException("Approval requires actor and time");
    validateContent();
    this.approvedBy = actorId;
    this.approvedAt = now;
    this.status = ApprovalStatus.APPROVED;
  }

  public void lock() {
    if (status != ApprovalStatus.APPROVED) {
      throw new IllegalStateException("Only approved strategies can be locked");
    }
    this.status = ApprovalStatus.LOCKED;
  }

  public Strategy createDraftVersion() {
    if (version == Integer.MAX_VALUE)
      throw new IllegalStateException("Strategy version limit reached");
    return createDraftVersion(version + 1);
  }

  public Strategy createDraftVersion(int nextVersion) {
    if (status == ApprovalStatus.DRAFT) {
      throw new IllegalStateException("A draft strategy cannot create another draft version");
    }
    if (nextVersion <= version)
      throw new IllegalArgumentException("New revision must follow source version");
    PublishingCadence cadenceSnapshot =
        new PublishingCadence(
            cadence.getPostsPerWeek(), cadence.getTimezone(), cadence.getBestTimes());
    List<ContentPillar> pillarSnapshots =
        contentPillars.stream()
            .map(
                pillar ->
                    new ContentPillar(
                        pillar.getName(), pillar.getWeight(), pillar.getDescription()))
            .toList();
    Strategy draft = new Strategy(this.brand, nextVersion, cadenceSnapshot, pillarSnapshots);
    if (contentFormatVersion == 2) draft.replacePlaybook(StrategyContent.from(this), getPlaybook());
    draft.revisedFromId = getId();
    return draft;
  }

  public void replaceContent(StrategyContent content) {
    requireDraft();
    if (content == null) throw new IllegalArgumentException("Strategy content is required");
    if (contentFormatVersion == 2)
      throw new IllegalStateException("Full Playbook replacement required");
    replaceCadence(content.cadence());
    replaceContentPillars(content.domainPillars());
  }

  public UUID getRevisedFromId() {
    return revisedFromId;
  }

  public UUID getApprovedBy() {
    return approvedBy;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public Brand getBrand() {
    return brand;
  }

  public int getVersion() {
    return version;
  }

  public ApprovalStatus getStatus() {
    return status;
  }

  public PublishingCadence getCadence() {
    return copyCadence(cadence);
  }

  public List<ContentPillar> getContentPillars() {
    return contentPillars.stream().map(Strategy::copyPillar).toList();
  }

  public void setCadence(PublishingCadence cadence) {
    requireDraft();
    if (contentFormatVersion == 2)
      throw new IllegalStateException("Full Playbook replacement required");
    replaceCadence(cadence);
  }

  public void setContentPillars(List<ContentPillar> contentPillars) {
    requireDraft();
    if (contentFormatVersion == 2)
      throw new IllegalStateException("Full Playbook replacement required");
    replaceContentPillars(contentPillars);
  }

  private void replaceCadence(PublishingCadence cadence) {
    if (cadence == null) {
      throw new IllegalArgumentException("Strategy cadence must not be null");
    }
    this.cadence = copyCadence(cadence);
  }

  private void replaceContentPillars(List<ContentPillar> contentPillars) {
    if (contentPillars == null || contentPillars.isEmpty()) {
      throw new IllegalArgumentException("Strategy must contain at least one content pillar");
    }
    if (contentPillars.stream().anyMatch(java.util.Objects::isNull)) {
      throw new IllegalArgumentException("Content pillars must not contain null values");
    }
    this.contentPillars.clear();
    this.contentPillars.addAll(contentPillars.stream().map(Strategy::copyPillar).toList());
  }

  public StrategyGenerationProvenance getGeneration() {
    return generation;
  }

  public void attachGeneration(StrategyGenerationProvenance generation) {
    requireDraft();
    if (this.generation != null || generation == null)
      throw new IllegalStateException("Strategy generation provenance is immutable");
    this.generation = generation;
  }

  private static PublishingCadence copyCadence(PublishingCadence value) {
    return new PublishingCadence(
        value.getPostsPerWeek(), value.getTimezone(), value.getBestTimes());
  }

  private static ContentPillar copyPillar(ContentPillar value) {
    return new ContentPillar(value.getName(), value.getWeight(), value.getDescription());
  }

  public int getContentFormatVersion() {
    return contentFormatVersion;
  }

  public UUID getEditedBy() {
    return editedBy;
  }

  public Instant getEditedAt() {
    return editedAt;
  }

  public boolean isReadyContentSource() {
    return contentFormatVersion == 2
        && status == ApprovalStatus.APPROVED
        && approvedBy != null
        && approvedAt != null;
  }

  public void recordHumanEdit(UUID actor, Instant time) {
    requireDraft();
    editedBy = java.util.Objects.requireNonNull(actor);
    editedAt = java.util.Objects.requireNonNull(time);
  }

  public void validateContent() {
    var base = StrategyContent.from(this);
    if (contentFormatVersion == 2) getPlaybook().validateWith(base);
    else if (contentFormatVersion != 1) throw new IllegalStateException("Unknown Strategy format");
  }

  public void replacePlaybook(StrategyContent base, PlaybookContent p) {
    requireDraft();
    java.util.Objects.requireNonNull(p).validateWith(base);
    replaceCadence(base.cadence());
    replaceContentPillars(base.domainPillars());
    contentFormatVersion = 2;
    whitespaceStatus = p.competitiveWhitespace().status().name();
    playbookPillarIds.clear();
    p.pillarIds()
        .forEach(id -> playbookPillarIds.add(new PlaybookPillarId(playbookPillarIds.size(), id)));
    playbookAudience.clear();
    p.audienceAssumptions()
        .forEach(
            a ->
                playbookAudience.add(
                    new PlaybookAudience(
                        playbookAudience.size(), a.id(), a.segment(), a.needs(), a.rationale())));
    playbookChannels.clear();
    playbookGoals.clear();
    for (var c : p.channels()) {
      playbookChannels.add(
          new PlaybookChannel(playbookChannels.size(), c.channel().name(), c.sharePercent()));
      for (int i = 0; i < c.goals().size(); i++) {
        var g = c.goals().get(i);
        playbookGoals.add(
            new PlaybookGoal(
                playbookGoals.size(),
                c.channel().name(),
                i,
                g.objective(),
                g.metricHint(),
                g.rationale()));
      }
    }
    playbookTones.clear();
    p.toneSpectrum()
        .forEach(
            t ->
                playbookTones.add(
                    new PlaybookTone(
                        playbookTones.size(),
                        t.id(),
                        t.leftLabel(),
                        t.rightLabel(),
                        t.position(),
                        t.rationale())));
    playbookHypotheses.clear();
    p.competitiveWhitespace()
        .hypotheses()
        .forEach(
            h ->
                playbookHypotheses.add(
                    new PlaybookHypothesis(
                        playbookHypotheses.size(), h.statement(), h.rationale())));
    playbookSlots.clear();
    for (var w : p.fourWeekOutline())
      for (var t : w.slots())
        playbookSlots.add(
            new PlaybookSlot(
                playbookSlots.size(),
                w.week(),
                t.slotId(),
                t.channel().name(),
                t.pillarId(),
                t.audienceId(),
                t.topic(),
                t.intent(),
                t.dayOffset()));
  }

  public PlaybookContent getPlaybook() {
    if (contentFormatVersion == 1) return null;
    return new PlaybookContent(
        contentFormatVersion,
        playbookPillarIds.stream().map(PlaybookPillarId::localId).toList(),
        playbookAudience.stream()
            .map(
                a ->
                    new PlaybookContent.Audience(
                        a.localId(), a.segment(), a.needs(), a.rationale()))
            .toList(),
        playbookChannels.stream()
            .map(
                c ->
                    new PlaybookContent.ChannelPlan(
                        PlaybookContent.Channel.valueOf(c.channel()),
                        c.sharePercent(),
                        playbookGoals.stream()
                            .filter(g -> g.channel().equals(c.channel()))
                            .sorted(java.util.Comparator.comparingInt(PlaybookGoal::goalOrder))
                            .map(
                                g ->
                                    new PlaybookContent.Goal(
                                        g.objective(), g.metricHint(), g.rationale()))
                            .toList()))
            .toList(),
        playbookTones.stream()
            .map(
                t ->
                    new PlaybookContent.Tone(
                        t.localId(), t.leftLabel(), t.rightLabel(), t.position(), t.rationale()))
            .toList(),
        new PlaybookContent.Whitespace(
            PlaybookContent.EvidenceStatus.valueOf(whitespaceStatus),
            playbookHypotheses.stream()
                .map(h -> new PlaybookContent.Hypothesis(h.statement(), h.rationale()))
                .toList()),
        java.util.stream.IntStream.rangeClosed(1, 4)
            .mapToObj(
                w ->
                    new PlaybookContent.Week(
                        w,
                        playbookSlots.stream()
                            .filter(t -> t.weekNumber() == w)
                            .map(
                                t ->
                                    new PlaybookContent.Slot(
                                        t.slotId(),
                                        PlaybookContent.Channel.valueOf(t.channel()),
                                        t.pillarId(),
                                        t.audienceId(),
                                        t.topic(),
                                        t.intent(),
                                        t.dayOffset()))
                            .toList()))
            .toList());
  }

  private void requireDraft() {
    if (status != ApprovalStatus.DRAFT) {
      throw new IllegalStateException("Only draft strategies can be edited");
    }
  }
}
