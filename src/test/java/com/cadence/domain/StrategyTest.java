package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StrategyTest {

  @Test
  void shouldCreateDraftAndApproveIt() {
    Brand brand = brand();
    PublishingCadence cadence = cadence();
    ContentPillar pillar = pillar();

    Strategy strategy = new Strategy(brand, 1, cadence, List.of(pillar));

    assertSame(brand, strategy.getBrand());
    assertNotSame(cadence, strategy.getCadence());
    assertEquals(cadence.getPostsPerWeek(), strategy.getCadence().getPostsPerWeek());
    assertEquals(1, strategy.getVersion());
    assertEquals(ApprovalStatus.DRAFT, strategy.getStatus());
    assertEquals(pillar.getName(), strategy.getContentPillars().getFirst().getName());
    assertNotSame(pillar, strategy.getContentPillars().getFirst());

    strategy.approve(java.util.UUID.randomUUID(), java.time.Instant.now());
    assertEquals(ApprovalStatus.APPROVED, strategy.getStatus());
  }

  @Test
  void shouldCreateNextIndependentDraftVersion() {
    ContentPillar first = pillar();
    Strategy original = new Strategy(brand(), 3, cadence(), List.of(first));
    original.approve(java.util.UUID.randomUUID(), java.time.Instant.now());

    Strategy draft = original.createDraftVersion();
    draft.setContentPillars(List.of(new ContentPillar("Community", 100, "Stories")));

    assertEquals(4, draft.getVersion());
    assertEquals(ApprovalStatus.DRAFT, draft.getStatus());
    assertSame(original.getBrand(), draft.getBrand());
    assertEquals(first.getName(), original.getContentPillars().getFirst().getName());
    assertNotSame(original.getCadence(), draft.getCadence());
    assertNotSame(original.getContentPillars().getFirst(), draft.getContentPillars().getFirst());
  }

  @Test
  void shouldProtectAndReplacePillarCollection() {
    List<ContentPillar> source = new ArrayList<>(List.of(pillar()));
    Strategy strategy = new Strategy(brand(), 1, cadence(), source);
    source.clear();

    assertEquals(1, strategy.getContentPillars().size());
    assertThrows(UnsupportedOperationException.class, () -> strategy.getContentPillars().clear());

    assertThrows(IllegalArgumentException.class, () -> strategy.setContentPillars(null));
    assertThrows(IllegalArgumentException.class, () -> strategy.setContentPillars(List.of()));
    assertEquals(1, strategy.getContentPillars().size());
  }

  @Test
  void shouldRejectInvalidConstructionAndLifecycleTransitions() {
    assertThrows(
        IllegalArgumentException.class, () -> new Strategy(null, 1, cadence(), List.of(pillar())));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Strategy(brand(), 0, cadence(), List.of(pillar())));
    assertThrows(
        IllegalArgumentException.class, () -> new Strategy(brand(), 1, null, List.of(pillar())));

    Strategy strategy = new Strategy(brand(), 1, cadence(), List.of(pillar()));
    assertThrows(IllegalStateException.class, strategy::createDraftVersion);
    assertThrows(IllegalStateException.class, strategy::lock);

    strategy.approve(java.util.UUID.randomUUID(), java.time.Instant.now());
    assertThrows(
        IllegalStateException.class,
        () -> strategy.approve(java.util.UUID.randomUUID(), java.time.Instant.now()));
    assertThrows(IllegalStateException.class, () -> strategy.setCadence(cadence()));
    assertThrows(IllegalStateException.class, () -> strategy.setContentPillars(List.of(pillar())));

    strategy.lock();
    assertEquals(ApprovalStatus.LOCKED, strategy.getStatus());
  }

  @Test
  void approvedSnapshotCannotBeEditedThroughConstructorOrGetterAliases() {
    var cadence = cadence();
    var pillar = pillar();
    var strategy = new Strategy(brand(), 1, cadence, List.of(pillar));
    strategy.approve(java.util.UUID.randomUUID(), java.time.Instant.now());
    cadence.setPostsPerWeek(20);
    pillar.setDescription("Forged source alias");
    strategy.getCadence().setPostsPerWeek(30);
    strategy.getContentPillars().getFirst().setDescription("Forged getter alias");
    assertEquals(3, strategy.getCadence().getPostsPerWeek());
    assertEquals("Explain the product", strategy.getContentPillars().getFirst().getDescription());
  }

  private Brand brand() {
    Workspace workspace = new Workspace("Creator Studio", "Food");
    return new Brand("Brand", workspace);
  }

  private PublishingCadence cadence() {
    return new PublishingCadence(3, "UTC", List.of(LocalTime.NOON));
  }

  private ContentPillar pillar() {
    return new ContentPillar("Education", 100, "Explain the product");
  }
}
