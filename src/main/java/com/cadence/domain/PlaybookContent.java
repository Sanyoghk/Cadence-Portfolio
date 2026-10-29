package com.cadence.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** ADR0029 v2 extension. IDs refer only to values within this exact Strategy revision. */
public record PlaybookContent(
    @com.fasterxml.jackson.annotation.JsonProperty(required = true) int schemaVersion,
    List<String> pillarIds,
    List<Audience> audienceAssumptions,
    List<ChannelPlan> channels,
    List<Tone> toneSpectrum,
    Whitespace competitiveWhitespace,
    List<Week> fourWeekOutline)
    implements StrictPlaybookValue {
  public enum Channel {
    INSTAGRAM,
    TIKTOK
  }

  public enum EvidenceStatus {
    HYPOTHESES,
    INSUFFICIENT_EVIDENCE
  }

  public record Audience(String id, String segment, String needs, String rationale)
      implements StrictPlaybookValue {
    public Audience {
      id = localId(id);
      segment = text(segment, 200);
      needs = text(needs, 1000);
      rationale = text(rationale, 1000);
    }
  }

  public record Goal(String objective, String metricHint, String rationale)
      implements StrictPlaybookValue {
    public Goal {
      objective = text(objective, 500);
      if (metricHint != null) metricHint = text(metricHint, 100);
      rationale = text(rationale, 1000);
    }
  }

  public record ChannelPlan(
      Channel channel,
      @com.fasterxml.jackson.annotation.JsonProperty(required = true) int sharePercent,
      List<Goal> goals)
      implements StrictPlaybookValue {
    public ChannelPlan {
      Objects.requireNonNull(channel);
      if (sharePercent < 1 || sharePercent > 100) invalid();
      goals = bounded(goals, 1, 3);
    }
  }

  public record Tone(
      String id,
      String leftLabel,
      String rightLabel,
      @com.fasterxml.jackson.annotation.JsonProperty(required = true) int position,
      String rationale)
      implements StrictPlaybookValue {
    public Tone {
      id = localId(id);
      leftLabel = text(leftLabel, 100);
      rightLabel = text(rightLabel, 100);
      rationale = text(rationale, 1000);
      if (leftLabel.equalsIgnoreCase(rightLabel) || position < 0 || position > 100) invalid();
    }
  }

  public record Hypothesis(String statement, String rationale) implements StrictPlaybookValue {
    public Hypothesis {
      statement = text(statement, 1000);
      rationale = text(rationale, 1000);
    }
  }

  public record Whitespace(EvidenceStatus status, List<Hypothesis> hypotheses)
      implements StrictPlaybookValue {
    public Whitespace {
      Objects.requireNonNull(status);
      hypotheses =
          bounded(
              hypotheses,
              status == EvidenceStatus.HYPOTHESES ? 1 : 0,
              status == EvidenceStatus.HYPOTHESES ? 5 : 0);
    }
  }

  public record Slot(
      String slotId,
      Channel channel,
      String pillarId,
      String audienceId,
      String topic,
      String intent,
      @com.fasterxml.jackson.annotation.JsonProperty(required = true) int dayOffset)
      implements StrictPlaybookValue {
    public Slot {
      slotId = localId(slotId);
      Objects.requireNonNull(channel);
      pillarId = localId(pillarId);
      audienceId = localId(audienceId);
      topic = text(topic, 500);
      intent = text(intent, 500);
      if (dayOffset < 0 || dayOffset > 6) invalid();
    }
  }

  public record Week(
      @com.fasterxml.jackson.annotation.JsonProperty(required = true) int week, List<Slot> slots)
      implements StrictPlaybookValue {
    public Week {
      if (week < 1 || week > 4) invalid();
      slots = bounded(slots, 1, 21);
    }
  }

  public PlaybookContent {
    if (schemaVersion != 2) invalid();
    pillarIds = bounded(pillarIds, 1, 10).stream().map(PlaybookContent::localId).toList();
    unique(pillarIds);
    audienceAssumptions = bounded(audienceAssumptions, 1, 5);
    unique(audienceAssumptions.stream().map(Audience::id).toList());
    channels = bounded(channels, 1, 2);
    unique(channels.stream().map(ChannelPlan::channel).toList());
    if (channels.stream().mapToInt(ChannelPlan::sharePercent).sum() != 100) invalid();
    toneSpectrum = bounded(toneSpectrum, 1, 5);
    unique(toneSpectrum.stream().map(Tone::id).toList());
    Objects.requireNonNull(competitiveWhitespace);
    fourWeekOutline =
        bounded(fourWeekOutline, 4, 4).stream()
            .sorted(Comparator.comparingInt(Week::week))
            .toList();
    unique(fourWeekOutline.stream().map(Week::week).toList());
    var audienceIds = audienceAssumptions.stream().map(Audience::id).toList();
    var channelIds = channels.stream().map(ChannelPlan::channel).toList();
    var slots = fourWeekOutline.stream().flatMap(w -> w.slots().stream()).toList();
    unique(slots.stream().map(Slot::slotId).toList());
    for (var slot : slots)
      if (!pillarIds.contains(slot.pillarId())
          || !audienceIds.contains(slot.audienceId())
          || !channelIds.contains(slot.channel())) invalid();
    var expected = allocation(channels, slots.size());
    for (var c : channels)
      if (slots.stream().filter(s -> s.channel() == c.channel()).count()
          != expected.get(c.channel())) invalid();
  }

  public void validateWith(StrategyContent base) {
    Objects.requireNonNull(base);
    if (pillarIds.size() != base.pillars().size()
        || fourWeekOutline.stream().anyMatch(w -> w.slots().size() != base.postsPerWeek()))
      invalid();
    if (canonical(base).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65536) invalid();
  }

  public String canonical(StrategyContent base) {
    var value = new TreeMap<String, Object>();
    value.put("playbook", this);
    value.put("pillars", base.pillars());
    value.put("postsPerWeek", base.postsPerWeek());
    value.put("timezone", base.timezone());
    value.put("bestTimes", base.bestTimes().stream().map(Object::toString).toList());
    try {
      return new ObjectMapper().writeValueAsString(value);
    } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
      throw new IllegalArgumentException("Invalid Playbook");
    }
  }

  public static Map<Channel, Integer> allocation(List<ChannelPlan> channels, int total) {
    var result = new EnumMap<Channel, Integer>(Channel.class);
    for (var c : channels) result.put(c.channel(), total * c.sharePercent() / 100);
    int left = total - result.values().stream().mapToInt(Integer::intValue).sum();
    var order =
        channels.stream()
            .sorted(
                Comparator.<ChannelPlan>comparingInt(c -> -(total * c.sharePercent() % 100))
                    .thenComparing(ChannelPlan::channel))
            .toList();
    for (int i = 0; i < left; i++) result.compute(order.get(i).channel(), (k, v) -> v + 1);
    return Map.copyOf(result);
  }

  private static <T> List<T> bounded(List<T> values, int min, int max) {
    if (values == null
        || values.size() < min
        || values.size() > max
        || values.stream().anyMatch(Objects::isNull)) invalid();
    return List.copyOf(values);
  }

  private static void unique(List<?> values) {
    if (new HashSet<>(values).size() != values.size()) invalid();
  }

  private static String localId(String s) {
    if (s == null || !s.matches("[A-Za-z0-9_-]{1,64}")) invalid();
    return s;
  }

  private static String text(String s, int max) {
    if (s == null
        || s.isBlank()
        || s.strip().codePointCount(0, s.strip().length()) > max
        || s.codePoints()
            .anyMatch(cp -> Character.isISOControl(cp) || (cp >= 0xD800 && cp <= 0xDFFF)))
      invalid();
    return s.strip();
  }

  private static void invalid() {
    throw new IllegalArgumentException("Invalid Playbook content");
  }
}
