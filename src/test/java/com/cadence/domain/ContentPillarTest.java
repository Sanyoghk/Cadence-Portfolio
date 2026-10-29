package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContentPillarTest {

  @Test
  void shouldCreateAndUpdateContentPillar() {
    ContentPillar pillar = new ContentPillar("Education", 40, "Explain the product");

    pillar.setName("Community");
    pillar.setWeight(60);
    pillar.setDescription("Show customer stories");

    assertEquals("Community", pillar.getName());
    assertEquals(60, pillar.getWeight());
    assertEquals("Show customer stories", pillar.getDescription());
  }

  @Test
  void shouldAcceptBoundaryWeights() {
    ContentPillar pillar = new ContentPillar("Education", 0, "Description");
    assertEquals(0, pillar.getWeight());

    pillar.setWeight(100);
    assertEquals(100, pillar.getWeight());
  }

  @Test
  void shouldRejectInvalidFields() {
    assertThrows(IllegalArgumentException.class, () -> new ContentPillar(" ", 50, "Description"));
    assertThrows(
        IllegalArgumentException.class, () -> new ContentPillar("Education", -1, "Description"));
    assertThrows(
        IllegalArgumentException.class, () -> new ContentPillar("Education", 101, "Description"));
    assertThrows(IllegalArgumentException.class, () -> new ContentPillar("Education", 50, null));
  }
}
