package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StrategyContentTest {
  private StrategyContent.Pillar pillar(String name, int weight) {
    return new StrategyContent.Pillar(name, weight, "Useful content");
  }

  @Test
  void validatesCompleteContentAndCopiesInputCollections() {
    var pillars = new ArrayList<>(List.of(pillar(" Education ", 100)));
    var times = new ArrayList<>(List.of(LocalTime.NOON));
    var content = new StrategyContent(pillars, 21, " Europe/London ", times);
    pillars.clear();
    times.clear();
    assertThat(content.pillars().getFirst().name()).isEqualTo("Education");
    assertThat(content.timezone()).isEqualTo("Europe/London");
    assertThat(content.bestTimes()).containsExactly(LocalTime.NOON);
    assertThatThrownBy(() -> content.pillars().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsDuplicateNamesBadAllocationAndUnboundedCollections() {
    for (var pillars :
        List.of(
            List.<StrategyContent.Pillar>of(),
            List.of(pillar("One", 99)),
            List.of(pillar("One", 50), pillar("one", 50)),
            Collections.nCopies(11, pillar("One", 100)))) {
      assertThatThrownBy(() -> new StrategyContent(pillars, 3, "UTC", List.of(LocalTime.NOON)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> pillar("Bad\nName", 100)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pillar("X".repeat(256), 100))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> pillar("One", 101)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsInvalidTimeFrequencyZoneAndNulls() {
    for (var times :
        List.of(
            List.<LocalTime>of(),
            List.of(LocalTime.NOON, LocalTime.NOON),
            List.of(LocalTime.of(12, 0, 0, 1)),
            Collections.nCopies(8, LocalTime.NOON))) {
      assertThatThrownBy(() -> new StrategyContent(List.of(pillar("One", 100)), 3, "UTC", times))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (int posts : List.of(0, 22))
      assertThatThrownBy(
              () ->
                  new StrategyContent(
                      List.of(pillar("One", 100)), posts, "UTC", List.of(LocalTime.NOON)))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new StrategyContent(
                    List.of(pillar("One", 100)), 3, "+01:00", List.of(LocalTime.NOON)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new StrategyContent(null, 3, "UTC", List.of(LocalTime.NOON)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void approvalRequiresAuditAndRevisionDoesNotCopyAudit() {
    var strategy =
        new Strategy(
            new Brand("Brand", new Workspace("Studio", "Creator")),
            1,
            new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
            List.of(new ContentPillar("One", 100, "Text")));
    var actor = UUID.randomUUID();
    var time = Instant.now();
    assertThatThrownBy(() -> strategy.approve(null, time))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> strategy.approve(actor, null))
        .isInstanceOf(IllegalArgumentException.class);
    strategy.approve(actor, time);
    assertThat(strategy.getApprovedBy()).isEqualTo(actor);
    assertThat(strategy.getApprovedAt()).isEqualTo(time);
    var draft = strategy.createDraftVersion(7);
    assertThat(draft.getVersion()).isEqualTo(7);
    assertThat(draft.getApprovedBy()).isNull();
    assertThat(draft.getApprovedAt()).isNull();
    assertThatThrownBy(() -> strategy.createDraftVersion(1))
        .isInstanceOf(IllegalArgumentException.class);
    var max =
        new Strategy(
            strategy.getBrand(),
            Integer.MAX_VALUE,
            strategy.getCadence(),
            strategy.getContentPillars());
    max.approve(actor, time);
    assertThatThrownBy(max::createDraftVersion).isInstanceOf(IllegalStateException.class);
  }
}
