package com.cadence.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cadence.application.repositories.DurableJobRepository;
import com.cadence.application.services.DurableJobQueue;
import com.cadence.domain.DurableJob;
import com.cadence.domain.JobStatus;
import com.cadence.domain.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DurableJobQueueTest {
  private Optional<DurableJob> claimAt(String worker, Instant at, Duration lease) {
    org.mockito.Mockito.when(clock.instant()).thenReturn(at);
    return queue.claimNext(worker, lease);
  }

  @Test
  void rejectsLateCallbacksUsingAuthoritativeClockAndAttemptNumber() {
    var job = queue.enqueue("analysis", "brand:42", "fenced", 3, NOW);
    claimAt("worker", NOW, Duration.ofSeconds(30)).orElseThrow();
    org.mockito.Mockito.when(clock.instant()).thenReturn(NOW.plusSeconds(30));
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class, () -> queue.succeed(job.getId(), "worker", 1));
    claimAt("worker", NOW.plusSeconds(30), Duration.ofSeconds(30)).orElseThrow();
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalStateException.class,
        () ->
            queue.fail(
                job.getId(),
                "worker",
                1,
                "stale",
                new RetryPolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)),
                true));
    assertEquals(JobStatus.RUNNING, jobs.findById(job.getId()).orElseThrow().getStatus());
    queue.succeed(job.getId(), "worker", 2);
    assertEquals(JobStatus.SUCCEEDED, jobs.findById(job.getId()).orElseThrow().getStatus());
  }

  @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;

  @org.junit.jupiter.api.BeforeEach
  void clockTime() {
    org.mockito.Mockito.when(clock.instant()).thenReturn(NOW);
  }

  private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

  @Autowired private DurableJobQueue queue;
  @Autowired private DurableJobRepository jobs;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void cleanJobs() {
    jobs.deleteAll();
  }

  @Test
  void shouldReturnTheExistingJobForADuplicateIdempotencyKey() {
    DurableJob first = queue.enqueue("analysis", "brand:42", "analysis:42", 3, NOW);
    DurableJob duplicate = queue.enqueue("analysis", "brand:42", "analysis:42", 3, NOW);

    assertEquals(first.getId(), duplicate.getId());
    assertEquals(1, jobs.count());
  }

  @Test
  void shouldEnqueueInTheCallersTransaction() {
    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    transaction.executeWithoutResult(
        status -> {
          queue.enqueue("analysis", "brand:42", "rolled-back", 3, NOW);
          status.setRollbackOnly();
        });

    assertTrue(jobs.findByIdempotencyKey("rolled-back").isEmpty());
  }

  @Test
  void shouldAllowOnlyOneWorkerToClaimAJob() throws Exception {
    queue.enqueue("analysis", "brand:42", "concurrent", 3, NOW);
    Callable<Optional<DurableJob>> firstClaim =
        () -> claimAt("worker-a", NOW, Duration.ofSeconds(30));
    Callable<Optional<DurableJob>> secondClaim =
        () -> claimAt("worker-b", NOW, Duration.ofSeconds(30));

    try (var executor = Executors.newFixedThreadPool(2)) {
      List<Optional<DurableJob>> results =
          executor.invokeAll(List.of(firstClaim, secondClaim)).stream()
              .map(
                  future -> {
                    try {
                      return future.get();
                    } catch (Exception exception) {
                      throw new AssertionError(exception);
                    }
                  })
              .toList();

      assertEquals(1, results.stream().filter(Optional::isPresent).count());
    }
    assertEquals(JobStatus.RUNNING, jobs.findAll().getFirst().getStatus());
  }

  @Test
  void shouldRecoverAnExpiredLeaseAndRedeliverTheJob() {
    queue.enqueue("analysis", "brand:42", "lease-recovery", 3, NOW);
    DurableJob first = claimAt("worker-a", NOW, Duration.ofSeconds(30)).orElseThrow();

    assertFalse(claimAt("worker-b", NOW.plusSeconds(29), Duration.ofSeconds(30)).isPresent());
    DurableJob recovered =
        claimAt("worker-b", NOW.plusSeconds(31), Duration.ofSeconds(30)).orElseThrow();

    assertEquals(first.getId(), recovered.getId());
    assertEquals(2, recovered.getAttempts());
    assertEquals("worker-b", recovered.getLeaseOwner());
  }

  @Test
  void shouldRetryAtTheScheduledTimeAndThenSucceed() {
    RetryPolicy policy = new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1));
    DurableJob job = queue.enqueue("analysis", "brand:42", "retry", 3, NOW);
    claimAt("worker-a", NOW, Duration.ofSeconds(30)).orElseThrow();
    queue.fail(job.getId(), "worker-a", 1, "provider unavailable", policy, false);

    assertFalse(claimAt("worker-b", NOW.plusSeconds(9), Duration.ofSeconds(30)).isPresent());
    DurableJob retry =
        claimAt("worker-b", NOW.plusSeconds(10), Duration.ofSeconds(30)).orElseThrow();
    org.mockito.Mockito.when(clock.instant()).thenReturn(NOW.plusSeconds(11));
    queue.succeed(retry.getId(), "worker-b", retry.getAttempts());

    DurableJob completed = jobs.findById(job.getId()).orElseThrow();
    assertEquals(JobStatus.SUCCEEDED, completed.getStatus());
    assertEquals(2, completed.getAttempts());
  }
}
