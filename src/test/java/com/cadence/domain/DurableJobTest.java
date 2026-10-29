package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DurableJobTest {

  @Test
  void rejectsExpiredLeaseAndOldAttemptEvenWhenWorkerNameIsReused() {
    DurableJob job = new DurableJob("analysis", "brand:42", "fenced", 3, NOW);
    job.claim("worker", NOW, Duration.ofSeconds(30));
    assertThrows(IllegalStateException.class, () -> job.succeed("worker", 1, NOW.plusSeconds(30)));
    assertThrows(
        IllegalStateException.class,
        () -> job.fail("worker", 1, NOW.plusSeconds(30), "late", RETRY_POLICY, true));
    assertEquals(JobStatus.RUNNING, job.getStatus());
    job.recoverExpiredLease(NOW.plusSeconds(30));
    job.claim("worker", NOW.plusSeconds(30), Duration.ofSeconds(30));
    assertThrows(IllegalStateException.class, () -> job.succeed("worker", 1, NOW.plusSeconds(31)));
    assertThrows(
        IllegalStateException.class,
        () -> job.fail("worker", 1, NOW.plusSeconds(31), "stale", RETRY_POLICY, true));
    job.succeed("worker", 2, NOW.plusSeconds(31));
    assertEquals(JobStatus.SUCCEEDED, job.getStatus());
  }

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");
  private static final RetryPolicy RETRY_POLICY =
      new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1));

  @Test
  void shouldApplyCappedExponentialBackoffAndDeadLetterAfterLastAttempt() {
    DurableJob job = new DurableJob("analysis", "brand:42", "analysis:42", 3, NOW);

    job.claim("worker-a", NOW, Duration.ofSeconds(30));
    job.fail("worker-a", job.getAttempts(), NOW, "temporary failure", RETRY_POLICY, false);
    assertEquals(JobStatus.FAILED, job.getStatus());
    assertEquals(NOW.plusSeconds(10), job.getNextAttemptAt());

    job.claim("worker-b", NOW.plusSeconds(10), Duration.ofSeconds(30));
    job.fail(
        "worker-b",
        job.getAttempts(),
        NOW.plusSeconds(10),
        "temporary failure",
        RETRY_POLICY,
        false);
    assertEquals(NOW.plusSeconds(30), job.getNextAttemptAt());

    job.claim("worker-c", NOW.plusSeconds(30), Duration.ofSeconds(30));
    job.fail(
        "worker-c", job.getAttempts(), NOW.plusSeconds(30), "final failure", RETRY_POLICY, false);

    assertEquals(JobStatus.DEAD, job.getStatus());
    assertEquals(3, job.getAttempts());
    assertEquals(NOW.plusSeconds(30), job.getCompletedAt());
    assertNull(job.getLeaseOwner());
  }

  @Test
  void shouldRejectCompletionByAWorkerThatDoesNotOwnTheLease() {
    DurableJob job = new DurableJob("analysis", "brand:42", "analysis:42", 3, NOW);
    job.claim("worker-a", NOW, Duration.ofSeconds(30));

    assertThrows(
        IllegalStateException.class,
        () -> job.succeed("worker-b", job.getAttempts(), NOW.plusSeconds(1)));
  }

  @Test
  void shouldStoreOnlyACompactSingleLineSafeError() {
    DurableJob job = new DurableJob("analysis", "brand:42", "analysis:42", 1, NOW);
    job.claim("worker-a", NOW, Duration.ofSeconds(30));

    job.fail(
        "worker-a", job.getAttempts(), NOW, "public message\nstack detail", RETRY_POLICY, false);

    assertEquals("public message stack detail", job.getLastError());
  }

  @Test
  void shouldCapRetryDelay() {
    assertEquals(Duration.ofSeconds(10), RETRY_POLICY.delayForAttempt(1));
    assertEquals(Duration.ofSeconds(20), RETRY_POLICY.delayForAttempt(2));
    assertEquals(Duration.ofSeconds(60), RETRY_POLICY.delayForAttempt(20));
  }
}
