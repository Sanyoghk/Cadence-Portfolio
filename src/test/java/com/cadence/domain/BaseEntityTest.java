package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class BaseEntityTest {

  @Test
  void auditTimestampsRepresentAnInstantRatherThanLocalWallTime() {
    var before = java.time.Instant.now();
    var entity = new TestEntity();
    entity.onCreate();
    var after = java.time.Instant.now();
    assertFalse(entity.getCreatedAt().isBefore(before));
    assertFalse(entity.getCreatedAt().isAfter(after));
    assertTrue(entity.getCreatedAt().toString().endsWith("Z"));
  }

  private static class TestEntity extends BaseEntity {}

  @Test
  void shouldSetCreatedAtAndUpdatedAtBeforePersist() {

    TestEntity entity = new TestEntity();

    entity.onCreate();

    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getUpdatedAt());
    assertEquals(entity.getCreatedAt(), entity.getUpdatedAt());
  }

  @Test
  void shouldUpdateUpdatedAtBeforeUpdate() throws InterruptedException {

    TestEntity entity = new TestEntity();

    entity.onCreate();
    var oldUpdatedAt = entity.getUpdatedAt();

    Thread.sleep(10);

    entity.onUpdate();

    assertTrue(entity.getUpdatedAt().isAfter(oldUpdatedAt));
  }
}
