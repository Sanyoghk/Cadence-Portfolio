package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.ports.EvidenceMalwareScanner;
import com.cadence.application.ports.EvidenceObjectLifecycleStorage;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** The same concurrency and lifecycle contract runs on H2 and migrated PostgreSQL. */
public abstract class QuarantineLifecycleContract {
  @Autowired protected QuarantineLifecycle lifecycle;
  @Autowired protected QuarantineCleanupWorker cleanup;
  @Autowired protected QuarantineObjectRepository objects;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected DurableJobQueue jobs;
  @Autowired protected PlatformTransactionManager manager;
  @Autowired protected MutableClock clock;

  @Autowired
  @Qualifier("cleanupTestStorage")
  protected EvidenceObjectLifecycleStorage deletion;

  @MockitoBean protected EvidenceObjectStorage unusedStorage;
  @MockitoBean protected EvidenceMalwareScanner unusedScanner;
  protected Workspace workspace;
  protected Brand brand;
  protected Map<String, byte[]> providerObjects;

  @TestConfiguration
  public static class Fixtures {
    @Bean
    @Primary
    MutableClock lifecycleTestClock() {
      return new MutableClock();
    }

    @Bean
    @Primary
    EvidenceObjectLifecycleStorage cleanupTestStorage() {
      return mock(EvidenceObjectLifecycleStorage.class);
    }
  }

  public static class MutableClock extends Clock {
    private volatile Instant now = Instant.now();

    public void set(Instant now) {
      this.now = now;
    }

    public void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @BeforeEach
  void prepareLifecycle() {
    // Deliberately exercise values which a TIMESTAMP(6) database would round into the future.
    clock.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS).plusNanos(999));
    reset(deletion);
    providerObjects = new ConcurrentHashMap<>();
    doAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              providerObjects.remove(call.getArgument(0));
              return null;
            })
        .when(deletion)
        .deleteQuarantined(anyString());
    workspace = workspaces.saveAndFlush(new Workspace("Lifecycle studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Lifecycle brand", workspace));
  }

  protected QuarantineLifecycle.Reservation reserve(long bytes) {
    return lifecycle.reserve(workspace.getId(), brand.getId(), bytes, "pdf");
  }

  protected BrandEvidence publish(QuarantineLifecycle.Reservation reservation, Brand targetBrand) {
    return new TransactionTemplate(manager)
        .execute(
            status -> {
              BrandEvidence stored =
                  evidence.saveAndFlush(
                      BrandEvidence.quarantinedFile(
                          targetBrand,
                          reservation.reference(),
                          UUID.randomUUID().toString().replace("-", "").repeat(2),
                          clock.instant(),
                          "source.pdf",
                          "application/pdf"));
              lifecycle.reference(reservation.id(), stored.getId(), reservation.reference(), 1);
              return stored;
            });
  }

  @Test
  void reservationsAreDurableAndEachRetryOwnsANeverReusedKey() {
    var a = reserve(40);
    var b = reserve(40);
    assertThat(a.objectKey()).isNotEqualTo(b.objectKey());
    assertThat(a.objectKey()).startsWith("quarantine/writes/" + workspace.getId() + "/");
    assertThat(objects.findById(a.id()).orElseThrow().getReservedAt())
        .isEqualTo(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(80);
    assertThat(objects.retainedObjects(workspace.getId())).isEqualTo(2);
    assertThatThrownBy(() -> reserve(1)).isInstanceOf(EvidenceQuotaExceededException.class);
  }

  @Test
  void concurrentReservationsCannotOversubscribeTheWorkspaceAcrossBrands() throws Exception {
    Brand second = brands.saveAndFlush(new Brand("Second brand", workspace));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> first = () -> reserveConcurrently(brand, ready, start);
      Callable<Boolean> other = () -> reserveConcurrently(second, ready, start);
      var a = executor.submit(first);
      var b = executor.submit(other);
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(60);
    assertThat(objects.retainedObjects(workspace.getId())).isEqualTo(1);
  }

  private boolean reserveConcurrently(Brand target, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS))
      throw new AssertionError("Reservation barrier timed out");
    try {
      lifecycle.reserve(workspace.getId(), target.getId(), 60, "pdf");
      return true;
    } catch (EvidenceQuotaExceededException expected) {
      return false;
    }
  }

  @Test
  void quotaIsIsolatedByWorkspaceAndWrongBrandScopeIsRejected() {
    reserve(100);
    Workspace other = workspaces.saveAndFlush(new Workspace("Other studio", "Creator"));
    Brand otherBrand = brands.saveAndFlush(new Brand("Other brand", other));
    assertThat(lifecycle.reserve(other.getId(), otherBrand.getId(), 100, "pdf")).isNotNull();
    assertThatThrownBy(() -> lifecycle.reserve(other.getId(), brand.getId(), 1, "pdf"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(objects.retainedBytes(other.getId())).isEqualTo(100);
  }

  @Test
  void expiredReservationCannotPublishOrWriteEvenIfExternalIoCompleted() {
    var reservation = reserve(10);
    clock.advance(Duration.ofSeconds(10));
    assertThatThrownBy(() -> lifecycle.requireWritable(reservation.id()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> publish(reservation, brand)).isInstanceOf(IllegalStateException.class);
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.RESERVED);
    assertThat(objects.evidenceReferences(reservation.reference())).isZero();
  }

  @Test
  void referenceTransitionRequiresTheEvidenceTransactionAndRollsBackTogether() {
    var reservation = reserve(10);
    assertThatThrownBy(
            () ->
                lifecycle.reference(
                    reservation.id(), UUID.randomUUID(), reservation.reference(), 1))
        .isInstanceOf(IllegalTransactionStateException.class);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              BrandEvidence stored =
                  evidence.saveAndFlush(
                      BrandEvidence.quarantinedFile(
                          brand,
                          reservation.reference(),
                          "a".repeat(64),
                          clock.instant(),
                          "source.pdf",
                          "application/pdf"));
              lifecycle.reference(reservation.id(), stored.getId(), reservation.reference(), 1);
              jobs.enqueue(
                  "EVIDENCE_MALWARE_SCAN",
                  "evidence:" + stored.getId(),
                  "rollback:" + stored.getId(),
                  3,
                  clock.instant());
              status.setRollbackOnly();
            });
    assertThat(objects.evidenceReferences(reservation.reference())).isZero();
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.RESERVED);
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(10);
  }

  @Test
  void referencedDataAndItsQuotaAreNeverAutomaticallyExpired() {
    var reservation = reserve(10);
    publish(reservation, brand);
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(1);
    clock.advance(Duration.ofDays(30));
    cleanup.runOnce();
    verify(deletion, never()).deleteQuarantined(reservation.reference());
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.REFERENCED);
  }

  @Test
  void cleanupFencesLatePublicationThenReconcilesADelayedPutBeforeReleasingQuota() {
    var reservation = reserve(10);
    providerObjects.put(reservation.reference(), new byte[] {1});
    clock.advance(Duration.ofSeconds(21));
    doAnswer(
            call -> {
              String reference = call.getArgument(0);
              if (reference.equals(reservation.reference())) {
                assertThat(objects.findById(reservation.id()).orElseThrow().getState())
                    .isIn(QuarantineObject.State.DELETING, QuarantineObject.State.TOMBSTONED);
                assertThatThrownBy(() -> publish(reservation, brand))
                    .isInstanceOf(RuntimeException.class);
              }
              providerObjects.remove(reference);
              return null;
            })
        .when(deletion)
        .deleteQuarantined(anyString());
    cleanup.runOnce();
    assertThat(providerObjects).doesNotContainKey(reservation.reference());
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(10);
    providerObjects.put(reservation.reference(), new byte[] {2}); // late, ambiguous provider PUT
    clock.advance(Duration.ofSeconds(11));
    cleanup.runOnce();
    assertThat(providerObjects).doesNotContainKey(reservation.reference());
    assertThat(objects.retainedBytes(workspace.getId())).isZero();
    assertThat(objects.findById(reservation.id()).orElseThrow().isQuotaReleased()).isTrue();
    assertThatThrownBy(() -> lifecycle.requireWritable(reservation.id()))
        .isInstanceOf(IllegalStateException.class);
    providerObjects.put(reservation.reference(), new byte[] {3});
    clock.advance(Duration.ofSeconds(11));
    cleanup.runOnce();
    assertThat(providerObjects).doesNotContainKey(reservation.reference());
  }

  @Test
  void providerFailureRetainsQuotaAndLeavesADurableBoundedRetry() {
    var reservation = reserve(10);
    clock.advance(Duration.ofSeconds(21));
    doThrow(new IllegalStateException("secret provider endpoint"))
        .when(deletion)
        .deleteQuarantined(reservation.reference());
    cleanup.runOnce();
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.DELETING);
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(10);
    cleanup.runOnce();
    verify(deletion).deleteQuarantined(reservation.reference());
    doNothing().when(deletion).deleteQuarantined(reservation.reference());
    clock.advance(Duration.ofSeconds(11));
    cleanup.runOnce();
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.TOMBSTONED);
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(10);
  }

  @Test
  void concurrentCleanupWorkersClaimOneGenerationOnlyOnce() throws Exception {
    var reservation = reserve(10);
    clock.advance(Duration.ofSeconds(21));
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return null;
            })
        .when(deletion)
        .deleteQuarantined(reservation.reference());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> cleanup.runOnce());
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      var b = executor.submit(() -> cleanup.runOnce());
      b.get(10, TimeUnit.SECONDS);
      release.countDown();
      a.get(10, TimeUnit.SECONDS);
    }
    verify(deletion).deleteQuarantined(reservation.reference());
  }

  @Test
  void activeObjectJobPreventsDeletionEvenWithoutAnEvidenceReference() {
    var reservation = reserve(10);
    jobs.enqueue(
        "PRIVATE_OBJECT_JOB",
        reservation.reference(),
        "private:" + reservation.id(),
        3,
        clock.instant());
    clock.advance(Duration.ofSeconds(21));
    cleanup.runOnce();
    verify(deletion, never()).deleteQuarantined(reservation.reference());
    assertThat(objects.findById(reservation.id()).orElseThrow().getState())
        .isEqualTo(QuarantineObject.State.RESERVED);
  }

  @Test
  void hourlyWriteBudgetIncludesCleanedUpAndFailedGenerations() {
    reserve(10);
    reserve(10);
    clock.advance(Duration.ofSeconds(21));
    cleanup.runOnce();
    clock.advance(Duration.ofSeconds(11));
    cleanup.runOnce();
    assertThat(objects.retainedObjects(workspace.getId())).isZero();
    reserve(10);
    assertThatThrownBy(() -> reserve(10)).isInstanceOf(EvidenceQuotaExceededException.class);
    assertThat(objects.writesAfter(workspace.getId(), clock.instant().minus(Duration.ofHours(1))))
        .isEqualTo(3);
  }

  @Test
  void oversizedReservationAndArchivedWorkspaceAreRejectedBeforeIo() {
    assertThatThrownBy(() -> reserve(10 * 1024 * 1024L + 1))
        .isInstanceOf(IllegalArgumentException.class);
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> workspaces.findById(workspace.getId()).orElseThrow().archive());
    assertThatThrownBy(() -> reserve(1)).isInstanceOf(IllegalStateException.class);
    assertThat(objects.retainedObjects(workspace.getId())).isZero();
    verifyNoInteractions(deletion);
  }

  @Test
  void historicalUnmanagedEvidenceCannotBypassTheWorkspaceQuota() {
    evidence.saveAndFlush(
        BrandEvidence.quarantinedFile(
            brand,
            "quarantine:quarantine/" + brand.getId() + "/" + "a".repeat(64) + ".pdf",
            "a".repeat(64),
            clock.instant(),
            "legacy.pdf",
            "application/pdf"));
    assertThat(objects.legacyBytes(workspace.getId())).isEqualTo(10 * 1024 * 1024);
    assertThat(objects.legacyObjects(workspace.getId())).isEqualTo(1);
    assertThatThrownBy(() -> reserve(1)).isInstanceOf(EvidenceQuotaExceededException.class);
    assertThat(objects.retainedObjects(workspace.getId())).isZero();
  }
}
