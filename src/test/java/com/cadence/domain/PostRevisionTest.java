package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.StrategyFixtures;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PostRevisionTest {
  private final UUID actor = UUID.randomUUID();
  private final Instant now = Instant.now();

  @Test
  void regenerationRequiresNewMediaReviewWhileHumanBranchPreservesCompatibleSelection() {
    var p = post();
    var prior = new PostRevision(p, content(PostFormat.REEL), actor, now);
    var ids = List.of(UUID.randomUUID());
    prior.replaceMedia(ids, actor, now);
    prior.approve(actor, now);
    org.springframework.test.util.ReflectionTestUtils.setField(prior, "id", UUID.randomUUID());
    var human = prior.branch(content(PostFormat.REEL), actor, now.plusSeconds(1));
    assertThat(human.getMediaAssetIds()).isEqualTo(ids);
    var generated =
        PostRevision.generated(
            p,
            content(PostFormat.REEL),
            actor,
            now.plusSeconds(1),
            UUID.randomUUID(),
            "w1s0",
            prior);
    assertThat(generated.getMediaAssetIds()).isEmpty();
    assertThat(prior.getMediaAssetIds()).isEqualTo(ids);
    assertThat(prior.getStatus()).isEqualTo(PostRevision.State.APPROVED);
    assertThat(generated.getEditedBy()).isNull();
  }

  private ContentPost post() {
    var s =
        new Strategy(
            new Brand("Brand", new Workspace("Studio", "Creator")),
            1,
            new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
            List.of(
                new ContentPillar("Education", 60, "Explain"),
                new ContentPillar("Community", 40, "Invite")));
    s.replacePlaybook(StrategyContent.from(s), StrategyFixtures.playbook());
    s.approve(actor, now);
    return new ContentPost(s, PlaybookContent.Channel.INSTAGRAM, actor, now);
  }

  private PostContent content(PostFormat format) {
    return new PostContent("Title", "Caption", List.of("#creator"), format, "Concept", null);
  }

  @ParameterizedTest
  @EnumSource(PostFormat.class)
  void exhaustivePlatformFormatMatrix(PostFormat format) {
    var c = content(format);
    if (Set.of(PostFormat.IMAGE, PostFormat.CAROUSEL, PostFormat.REEL, PostFormat.STORY)
        .contains(format))
      assertThatCode(() -> c.validateFor(PlaybookContent.Channel.INSTAGRAM))
          .doesNotThrowAnyException();
    else
      assertThatThrownBy(() -> c.validateFor(PlaybookContent.Channel.INSTAGRAM))
          .isInstanceOf(IllegalArgumentException.class);
    if (Set.of(PostFormat.VIDEO, PostFormat.CAROUSEL).contains(format))
      assertThatCode(() -> c.validateFor(PlaybookContent.Channel.TIKTOK))
          .doesNotThrowAnyException();
    else
      assertThatThrownBy(() -> c.validateFor(PlaybookContent.Channel.TIKTOK))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void transitionsNeverChangeApprovedHistory() {
    var p = post();
    var r = new PostRevision(p, content(PostFormat.REEL), actor, now);
    assertThatThrownBy(() -> r.branch(content(PostFormat.REEL), actor, now))
        .isInstanceOf(IllegalStateException.class);
    r.approve(actor, now);
    assertThat(r.getApprovedAt()).isEqualTo(now);
    assertThatThrownBy(() -> r.approve(actor, now)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> r.replace(content(PostFormat.IMAGE), actor, now))
        .isInstanceOf(IllegalStateException.class);
    var branch = r.branch(content(PostFormat.IMAGE), actor, now);
    assertThat(branch.getPost()).isSameAs(p);
    assertThat(branch.getRevisionNumber()).isEqualTo(2);
    assertThat(branch.getApprovedAt()).isNull();
    assertThat(r.getFormat()).isEqualTo(PostFormat.REEL);
  }

  @Test
  void revisionCapAndCorruptSourceFailClosed() {
    var p = post();
    var r = new PostRevision(p, content(PostFormat.REEL), actor, now);
    r.approve(actor, now);
    org.springframework.test.util.ReflectionTestUtils.setField(r, "revisionNumber", 1000);
    assertThatThrownBy(() -> r.branch(content(PostFormat.REEL), actor, now))
        .isInstanceOf(IllegalStateException.class);
    p.requireCurrentSource();
    org.springframework.test.util.ReflectionTestUtils.setField(p, "sourceChecksum", "a".repeat(64));
    assertThatThrownBy(p::requireCurrentSource).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void boundsAndDefensiveCopies() {
    assertThatThrownBy(
            () -> new PostContent("t", "bad\u0000text", List.of(), PostFormat.IMAGE, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
    var tags = new ArrayList<>(List.of("#creator"));
    var c =
        new PostContent(
            "a".repeat(255), "b".repeat(2200), tags, PostFormat.IMAGE, "c".repeat(2000), null);
    tags.clear();
    assertThat(c.hashtags()).containsExactly("#creator");
    c.validateFor(PlaybookContent.Channel.INSTAGRAM);
    assertThatThrownBy(() -> c.hashtags().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> new PostContent("a".repeat(256), "c", List.of(), PostFormat.IMAGE, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PostContent("t", "c", List.of("#a", "#a"), PostFormat.IMAGE, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PostContent("t", "c", List.of("#a b"), PostFormat.IMAGE, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PostContent("t", "c", List.of(), PostFormat.IMAGE, "m", "Script"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PostContent(
                    "t", "c", java.util.Collections.nCopies(31, "#x"), PostFormat.VIDEO, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> new PostContent("t", "c", List.of("#привет"), PostFormat.VIDEO, "m", null))
        .doesNotThrowAnyException();
    var tiktok =
        new PostContent("t", "c".repeat(4000), List.of(), PostFormat.VIDEO, "m", "s".repeat(5000));
    tiktok.validateFor(PlaybookContent.Channel.TIKTOK);
    assertThatThrownBy(
            () -> new PostContent("t", "c".repeat(4001), List.of(), PostFormat.VIDEO, "m", null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
