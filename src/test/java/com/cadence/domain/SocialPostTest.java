package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialPostTest {

  @Test
  void shouldCreateDraftAndNormalizeContent() {
    Strategy strategy = strategy();

    SocialPost post =
        new SocialPost(
            strategy,
            "  New product  ",
            "  Caption text  ",
            List.of("brin", "#luxury", "brin", " "),
            PostFormat.IMAGE);

    assertSame(strategy, post.getStrategy());
    assertEquals("New product", post.getTitle());
    assertEquals("Caption text", post.getCaption());
    assertEquals(List.of("#brin", "#luxury"), post.getHashtags());
    assertEquals(PostFormat.IMAGE, post.getFormat());
    assertEquals(PostStatus.DRAFT, post.getStatus());
    assertNull(post.getScheduledAt());
  }

  @Test
  void shouldFollowSuccessfulPublishingLifecycle() {
    SocialPost post = post();
    Instant scheduledAt = Instant.now().plusSeconds(3600);

    post.approve();
    post.schedule(scheduledAt);
    post.markPublished();

    assertEquals(PostStatus.PUBLISHED, post.getStatus());
    assertEquals(scheduledAt, post.getScheduledAt());
  }

  @Test
  void shouldMarkScheduledPostAsFailed() {
    SocialPost post = post();
    post.approve();
    post.schedule(Instant.now().plusSeconds(3600));

    post.markFailed();

    assertEquals(PostStatus.FAILED, post.getStatus());
  }

  @Test
  void shouldRegenerateUnpublishedPost() {
    SocialPost post = post();
    post.approve();
    post.schedule(Instant.now().plusSeconds(3600));

    post.regenerate();

    assertEquals(PostStatus.DRAFT, post.getStatus());
    assertNull(post.getScheduledAt());
  }

  @Test
  void shouldRejectInvalidLifecycleTransitions() {
    SocialPost draft = post();
    assertThrows(
        IllegalStateException.class, () -> draft.schedule(Instant.now().plusSeconds(3600)));
    assertThrows(IllegalStateException.class, draft::markPublished);
    assertThrows(IllegalStateException.class, draft::markFailed);

    draft.approve();
    assertThrows(IllegalStateException.class, draft::approve);
    assertThrows(IllegalArgumentException.class, () -> draft.schedule(null));
    assertThrows(
        IllegalArgumentException.class, () -> draft.schedule(Instant.now().minusSeconds(60)));

    draft.schedule(Instant.now().plusSeconds(3600));
    draft.markPublished();
    assertThrows(IllegalStateException.class, draft::regenerate);
  }

  @Test
  void shouldRejectInvalidRequiredFields() {
    Strategy strategy = strategy();

    assertThrows(
        IllegalArgumentException.class,
        () -> new SocialPost(null, "Title", "Caption", List.of(), PostFormat.TEXT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SocialPost(strategy, " ", "Caption", List.of(), PostFormat.TEXT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SocialPost(strategy, "Title", null, List.of(), PostFormat.TEXT));
    assertThrows(
        IllegalArgumentException.class,
        () -> new SocialPost(strategy, "Title", "Caption", List.of(), null));
  }

  @Test
  void shouldAllowEditingOnlyWhileDraft() {
    SocialPost post = post();
    post.approve();

    assertThrows(IllegalStateException.class, () -> post.setTitle("Changed"));
    assertThrows(IllegalStateException.class, () -> post.setCaption("Changed"));
    assertThrows(IllegalStateException.class, () -> post.setHashtags(List.of("changed")));
    assertThrows(IllegalStateException.class, () -> post.setFormat(PostFormat.TEXT));
  }

  private SocialPost post() {
    return new SocialPost(strategy(), "Title", "Caption", List.of("cadence"), PostFormat.REEL);
  }

  private Strategy strategy() {
    Workspace workspace = new Workspace("Creator Studio", "Food");
    Brand brand = new Brand("Brand", workspace);
    PublishingCadence cadence = new PublishingCadence(3, "UTC", List.of(LocalTime.NOON));
    ContentPillar pillar = new ContentPillar("Education", 100, "Explain the product");
    return new Strategy(brand, 1, cadence, List.of(pillar));
  }
}
