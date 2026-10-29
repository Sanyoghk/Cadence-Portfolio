package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationTest {

  @Test
  void shouldCreateAndNormalizeRecommendation() {
    Strategy strategy = strategy(2);

    Recommendation recommendation =
        new Recommendation(strategy, "  Increase educational posts  ", 0.85, "  +12% saves  ");

    assertSame(strategy, recommendation.getStrategy());
    assertEquals("Increase educational posts", recommendation.getText());
    assertEquals(0.85, recommendation.getConfidence());
    assertEquals("+12% saves", recommendation.getExpectedLift());
  }

  @Test
  void shouldAcceptConfidenceBoundaries() {
    Recommendation recommendation = new Recommendation(strategy(1), "Text", 0.0, "+1%");
    assertEquals(0.0, recommendation.getConfidence());

    recommendation.setConfidence(1.0);
    assertEquals(1.0, recommendation.getConfidence());
  }

  @Test
  void shouldCreateNextDraftStrategy() {
    Strategy strategy = strategy(4);
    strategy.approve(java.util.UUID.randomUUID(), java.time.Instant.now());
    Recommendation recommendation = new Recommendation(strategy, "Text", 0.9, "+10%");

    Strategy draft = recommendation.createDraftStrategy();

    assertEquals(5, draft.getVersion());
    assertEquals(ApprovalStatus.DRAFT, draft.getStatus());
    assertSame(strategy.getBrand(), draft.getBrand());
  }

  @Test
  void shouldRejectInvalidRecommendationFields() {
    Strategy strategy = strategy(1);

    assertThrows(
        IllegalArgumentException.class, () -> new Recommendation(null, "Text", 0.5, "+1%"));
    assertThrows(
        IllegalArgumentException.class, () -> new Recommendation(strategy, " ", 0.5, "+1%"));
    assertThrows(
        IllegalArgumentException.class, () -> new Recommendation(strategy, "Text", -0.01, "+1%"));
    assertThrows(
        IllegalArgumentException.class, () -> new Recommendation(strategy, "Text", 1.01, "+1%"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Recommendation(strategy, "Text", Double.NaN, "+1%"));
    assertThrows(
        IllegalArgumentException.class, () -> new Recommendation(strategy, "Text", 0.5, null));
  }

  private Strategy strategy(int version) {
    Workspace workspace = new Workspace("Creator Studio", "Food");
    Brand brand = new Brand("Brand", workspace);
    PublishingCadence cadence = new PublishingCadence(3, "UTC", List.of(LocalTime.NOON));
    return new Strategy(
        brand,
        version,
        cadence,
        List.of(new ContentPillar("Education", 100, "Explain the product")));
  }
}
