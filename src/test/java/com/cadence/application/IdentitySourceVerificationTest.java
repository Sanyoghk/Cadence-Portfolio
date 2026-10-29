package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.DocumentReviewUnavailableException;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.services.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class IdentitySourceVerificationTest {
  @Test
  void overloadedCaptureRejectsThirdAndReleasesCapacityAfterFailure() throws Exception {
    var service =
        new IdentitySourceVerification(
            mock(PlatformTransactionManager.class),
            mock(EvidenceObjectStorage.class),
            Clock.systemUTC(),
            mock(com.cadence.application.ports.WebsiteMetadataExtractor.class));
    var entered = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    java.util.function.Supplier<List<EvidencePhysicalSource>> capture =
        () -> {
          entered.countDown();
          try {
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
          }
          throw new IllegalStateException("capture rejected");
        };
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> service.execute(capture, () -> "invalid"));
      var b = pool.submit(() -> service.execute(capture, () -> "invalid"));
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> service.execute(List::of, () -> "invalid"))
            .isInstanceOf(DocumentReviewUnavailableException.class);
      } finally {
        release.countDown();
      }
      for (var f : List.of(a, b))
        assertThatThrownBy(() -> f.get(10, TimeUnit.SECONDS))
            .hasCauseInstanceOf(IllegalStateException.class);
    }
    assertThat(service.execute(List::of, () -> "committed")).isEqualTo("committed");
  }

  @Test
  void deadlinePreventsCommitEvenWhenMetadataIsUnchanged() {
    var clock = mock(Clock.class);
    var now = Instant.parse("2026-10-14T00:00:00Z");
    when(clock.instant()).thenReturn(now, now.plusSeconds(30));
    var service =
        new IdentitySourceVerification(
            mock(PlatformTransactionManager.class),
            mock(EvidenceObjectStorage.class),
            clock,
            mock(com.cadence.application.ports.WebsiteMetadataExtractor.class));
    var commits = new java.util.concurrent.atomic.AtomicInteger();
    assertThatThrownBy(() -> service.execute(List::of, commits::incrementAndGet))
        .isInstanceOf(IllegalStateException.class);
    assertThat(commits).hasValue(0);
  }
}
