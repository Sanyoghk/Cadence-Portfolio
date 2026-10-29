package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostMetricTest {

  @Test
  void shouldCreateMetricAndCalculateEngagementRate() {
    SocialPost post = post();

    PostMetric metric = new PostMetric(post, 1000, 125, 30, 8, 45);

    assertSame(post, metric.getPost());
    assertEquals(1000, metric.getReach());
    assertEquals(125, metric.getEngagement());
    assertEquals(30, metric.getSaves());
    assertEquals(8, metric.getSignups());
    assertEquals(45, metric.getProfileToSiteClicks());
    assertEquals(12.5, metric.engagementRate(), 0.0001);
  }

  @Test
  void shouldReturnZeroRateWhenReachIsZero() {
    PostMetric metric = new PostMetric(post(), 0, 0, 0, 0, 0);

    assertEquals(0.0, metric.engagementRate());
  }

  @Test
  void shouldUpdateAllMetricValues() {
    PostMetric metric = new PostMetric(post(), 0, 0, 0, 0, 0);
    SocialPost replacement = post();

    metric.setPost(replacement);
    metric.setReach(200);
    metric.setEngagement(20);
    metric.setSaves(10);
    metric.setSignups(5);
    metric.setProfileToSiteClicks(15);

    assertSame(replacement, metric.getPost());
    assertEquals(10.0, metric.engagementRate(), 0.0001);
    assertEquals(10, metric.getSaves());
    assertEquals(5, metric.getSignups());
    assertEquals(15, metric.getProfileToSiteClicks());
  }

  @Test
  void shouldRejectNullPostAndNegativeValues() {
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(null, 0, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(post(), -1, 0, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(post(), 0, -1, 0, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(post(), 0, 0, -1, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(post(), 0, 0, 0, -1, 0));
    assertThrows(IllegalArgumentException.class, () -> new PostMetric(post(), 0, 0, 0, 0, -1));
  }

  private SocialPost post() {
    Workspace workspace = new Workspace("Creator Studio", "Food");
    Brand brand = new Brand("Brand", workspace);
    PublishingCadence cadence = new PublishingCadence(3, "UTC", List.of(LocalTime.NOON));
    Strategy strategy =
        new Strategy(
            brand, 1, cadence, List.of(new ContentPillar("Education", 100, "Explain the product")));
    return new SocialPost(strategy, "Title", "Caption", List.of(), PostFormat.IMAGE);
  }
}
