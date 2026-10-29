package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.StrategyFixtures;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlaybookContentTest {
  final ObjectMapper json = new ObjectMapper();

  @Test
  void completeFixtureAndDefensiveCopies() {
    var p = StrategyFixtures.playbook();
    p.validateWith(StrategyFixtures.output().content());
    assertThat(p.fourWeekOutline()).hasSize(4);
    assertThatThrownBy(() -> p.channels().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void malformedStructureAndForeignReferencesDenied() throws Exception {
    for (String path :
        List.of(
            "week",
            "pillarId",
            "audienceId",
            "channel",
            "sharePercent",
            "schemaVersion",
            "position",
            "dayOffset",
            "slotId")) {
      var n = json.valueToTree(StrategyFixtures.playbook());
      var slot =
          (com.fasterxml.jackson.databind.node.ObjectNode) n.at("/fourWeekOutline/0/slots/0");
      switch (path) {
        case "week" ->
            ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/fourWeekOutline/0"))
                .put("week", 2);
        case "sharePercent" ->
            ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/channels/0")).put(path, 99);
        case "schemaVersion" -> ((com.fasterxml.jackson.databind.node.ObjectNode) n).put(path, 3);
        case "position" ->
            ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/toneSpectrum/0"))
                .put(path, 101);
        case "dayOffset" -> slot.put(path, 7);
        case "slotId" -> slot.put(path, "w1s1");
        default -> slot.put(path, "foreign");
      }
      assertThatThrownBy(() -> json.treeToValue(n, PlaybookContent.class))
          .as(path)
          .isInstanceOf(Exception.class);
    }
  }

  @Test
  void unknownFieldsAndMissingNumericFieldsDenied() {
    for (String pointer :
        List.of(
            "",
            "/audienceAssumptions/0",
            "/channels/0",
            "/channels/0/goals/0",
            "/toneSpectrum/0",
            "/competitiveWhitespace",
            "/fourWeekOutline/0",
            "/fourWeekOutline/0/slots/0")) {
      var n = json.valueToTree(StrategyFixtures.playbook());
      ((com.fasterxml.jackson.databind.node.ObjectNode) n.at(pointer)).put("approvedBy", "forged");
      assertThatThrownBy(() -> json.treeToValue(n, PlaybookContent.class))
          .as(pointer)
          .isInstanceOf(Exception.class);
    }
    var n = json.valueToTree(StrategyFixtures.playbook());
    ((com.fasterxml.jackson.databind.node.ObjectNode) n.at("/toneSpectrum/0")).remove("position");
    assertThatThrownBy(() -> json.treeToValue(n, PlaybookContent.class))
        .isInstanceOf(Exception.class);
  }

  @Test
  void unicodeAndTextBoundaries() {
    new PlaybookContent.Audience("a", "😀".repeat(200), "Need", "Rationale");
    for (String text : List.of("😀".repeat(201), " ", "x\n", "\uD800"))
      assertThatThrownBy(() -> new PlaybookContent.Audience("a", text, "Need", "Why"))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PlaybookContent.Tone("x", "CLEAR", "clear", 0, "Why"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void largestRemainderIsDeterministicAndNotWeekly() {
    var g = List.of(new PlaybookContent.Goal("Goal", null, "Why"));
    var channels =
        List.of(
            new PlaybookContent.ChannelPlan(PlaybookContent.Channel.TIKTOK, 50, g),
            new PlaybookContent.ChannelPlan(PlaybookContent.Channel.INSTAGRAM, 50, g));
    assertThat(PlaybookContent.allocation(channels, 3))
        .containsEntry(PlaybookContent.Channel.INSTAGRAM, 2)
        .containsEntry(PlaybookContent.Channel.TIKTOK, 1);
    assertThat(PlaybookContent.allocation(channels, 12))
        .containsEntry(PlaybookContent.Channel.INSTAGRAM, 6)
        .containsEntry(PlaybookContent.Channel.TIKTOK, 6);
  }

  @Test
  void oldOutputCannotPublishAsFullPlaybook() {
    var r = StrategyFixtures.output();
    var old =
        new com.cadence.application.ports.StrategyGenerationResult(
            r.pillars(),
            r.postsPerWeek(),
            r.timezone(),
            r.bestTimes(),
            r.warnings(),
            r.provider(),
            r.modelVersion(),
            r.promptVersion());
    assertThatThrownBy(old::requireCompletePlaybook).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void legacyAndImmutableApprovalRemainExplicit() {
    var s =
        new Strategy(
            new Brand("B", new Workspace("W", "I")),
            1,
            new PublishingCadence(3, "UTC", List.of(java.time.LocalTime.NOON)),
            List.of(new ContentPillar("One", 100, "Text")));
    assertThat(s.getPlaybook()).isNull();
    assertThat(s.isReadyContentSource()).isFalse();
    s.replacePlaybook(StrategyFixtures.output().content(), StrategyFixtures.playbook());
    s.approve(UUID.randomUUID(), java.time.Instant.now());
    assertThat(s.isReadyContentSource()).isTrue();
    assertThatThrownBy(
            () ->
                s.replacePlaybook(StrategyFixtures.output().content(), StrategyFixtures.playbook()))
        .isInstanceOf(IllegalStateException.class);
    var branch = s.createDraftVersion();
    assertThat(branch.getPlaybook()).isEqualTo(s.getPlaybook());
    assertThat(branch.getGeneration()).isNull();
    assertThat(branch.isReadyContentSource()).isFalse();
    assertThatThrownBy(() -> branch.replaceContent(StrategyFixtures.output().content()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void mixedChannelsHypothesesAndPayloadCap() {
    var p = StrategyFixtures.playbook();
    var g = p.channels().getFirst().goals();
    var channels =
        List.of(
            new PlaybookContent.ChannelPlan(PlaybookContent.Channel.INSTAGRAM, 50, g),
            new PlaybookContent.ChannelPlan(PlaybookContent.Channel.TIKTOK, 50, g));
    var weeks =
        p.fourWeekOutline().stream()
            .map(
                w ->
                    new PlaybookContent.Week(
                        w.week(),
                        w.slots().stream()
                            .map(
                                t ->
                                    new PlaybookContent.Slot(
                                        t.slotId(),
                                        w.week() <= 2
                                            ? PlaybookContent.Channel.INSTAGRAM
                                            : PlaybookContent.Channel.TIKTOK,
                                        t.pillarId(),
                                        t.audienceId(),
                                        t.topic(),
                                        t.intent(),
                                        t.dayOffset()))
                            .toList()))
            .toList();
    var mixed =
        new PlaybookContent(
            2,
            p.pillarIds(),
            p.audienceAssumptions(),
            channels,
            p.toneSpectrum(),
            new PlaybookContent.Whitespace(
                PlaybookContent.EvidenceStatus.HYPOTHESES,
                List.of(new PlaybookContent.Hypothesis("Possible gap", "Unverified assumption"))),
            weeks);
    mixed.validateWith(StrategyFixtures.output().content());
    var large =
        new PlaybookContent(
            2,
            p.pillarIds(),
            p.audienceAssumptions(),
            p.channels(),
            p.toneSpectrum(),
            p.competitiveWhitespace(),
            java.util.stream.IntStream.rangeClosed(1, 4)
                .mapToObj(
                    w ->
                        new PlaybookContent.Week(
                            w,
                            java.util.stream.IntStream.range(0, 21)
                                .mapToObj(
                                    i ->
                                        new PlaybookContent.Slot(
                                            "w" + w + "s" + i,
                                            PlaybookContent.Channel.INSTAGRAM,
                                            "education",
                                            "creators",
                                            "x".repeat(500),
                                            "y".repeat(500),
                                            0))
                                .toList()))
                .toList());
    var base = StrategyFixtures.output().content();
    assertThatThrownBy(
            () ->
                large.validateWith(
                    new StrategyContent(base.pillars(), 21, base.timezone(), base.bestTimes())))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void schema1StrategyCaptureIsNotSilentlyReinterpreted() {
    var i =
        new ApprovedIdentitySource(
            UUID.randomUUID(),
            1,
            1,
            "a".repeat(64),
            UUID.randomUUID(),
            java.time.Instant.now(),
            null,
            UUID.randomUUID());
    assertThatThrownBy(
            () ->
                new com.cadence.application.services.AiGenerationSnapshot(
                    1, null, StrategyFixtures.command(), i))
        .isInstanceOf(IllegalArgumentException.class);
    new com.cadence.application.services.AiGenerationSnapshot(
        2, null, StrategyFixtures.command(), i);
  }
}
