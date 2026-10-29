package com.cadence.security;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.slf4j.*;
import org.springframework.mock.web.*;

class ApiRequestBoundaryTest {
  @Test
  void anonymousBurstCannotTriggerUnlimitedAuthenticationAndDenialLogging() throws Exception {
    var metrics = new ApiRequestTelemetry();
    var filter = new ApiRequestBoundary(1, metrics);
    for (int i = 0; i < 6000; i++)
      filter.doFilter(
          new MockHttpServletRequest("GET", "/protected"),
          new MockHttpServletResponse(),
          (a, b) -> {});
    var rejected = new MockHttpServletResponse();
    filter.doFilter(
        new MockHttpServletRequest("GET", "/protected"),
        rejected,
        (a, b) -> {
          throw new AssertionError("authentication must not run");
        });
    assertThat(rejected.getStatus()).isEqualTo(503);
    filter.doFilter(
        new MockHttpServletRequest("GET", "/api/v1/health"),
        new MockHttpServletResponse(),
        (a, b) -> {});
  }

  @Test
  void rateIsAccountBoundedAndDoesNotResetOnRejectedRequest() {
    var time = new AtomicLong(0);
    var limits = new ApiRateLimit(2, 1, 2, time::get);
    var id = UUID.randomUUID();
    assertThat(limits.allow(id, true)).isTrue();
    assertThat(limits.allow(id, true)).isTrue();
    assertThat(limits.allow(id, true)).isFalse();
    assertThat(limits.allow(id, false)).isTrue();
    assertThat(limits.allow(id, false)).isFalse();
    time.set(TimeUnit.SECONDS.toNanos(60) - 1);
    assertThat(limits.allow(id, true)).isFalse();
    time.incrementAndGet();
    assertThat(limits.allow(id, true)).isTrue();
  }

  @Test
  void accountMapFailsClosedAndOnlyEvictsExpiredWindows() {
    var time = new AtomicLong();
    var limits = new ApiRateLimit(1, 1, 1, time::get);
    var first = UUID.randomUUID();
    assertThat(limits.allow(first, true)).isTrue();
    assertThat(limits.allow(UUID.randomUUID(), false)).isFalse();
    assertThat(limits.allow(first, false)).isTrue();
    time.set(TimeUnit.MINUTES.toNanos(1));
    assertThat(limits.allow(UUID.randomUUID(), false)).isTrue();
  }

  @Test
  void concurrentRequestsCannotExceedLimit() throws Exception {
    var limits = new ApiRateLimit(10, 10, 1, () -> 0);
    var id = UUID.randomUUID();
    try (var executor = Executors.newFixedThreadPool(8)) {
      var work =
          java.util.stream.IntStream.range(0, 100)
              .mapToObj(i -> (Callable<Boolean>) () -> limits.allow(id, false))
              .toList();
      int passed = 0;
      for (var r : executor.invokeAll(work)) if (r.get()) passed++;
      assertThat(passed).isEqualTo(10);
    }
  }

  @Test
  void correlationIsServerOwnedAndLogsContainNoInput() throws Exception {
    var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ApiRequestBoundary.class);
    var events =
        new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
    events.start();
    logger.addAppender(events);
    MDC.put("requestId", "outer");
    try {
      var request = new MockHttpServletRequest("POST", "/private-secret-path");
      request.addHeader("X-Request-ID", "forged-secret\r\nlog");
      request.addHeader("Authorization", "Bearer private-token");
      request.setQueryString("password=private-password");
      request.setContent("private-prompt".getBytes());
      var response = new MockHttpServletResponse();
      var metrics = new ApiRequestTelemetry();
      new ApiRequestBoundary(1, metrics)
          .doFilter(
              request,
              response,
              (req, res) -> {
                assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
                response.setStatus(403);
              });
      UUID.fromString(response.getHeader("X-Request-ID"));
      assertThat(MDC.get("requestId")).isEqualTo("outer");
      assertThat(metrics.getClientErrors()).isEqualTo(1);
      assertThat(events.list).hasSize(1);
      assertThat(events.list.getFirst().getFormattedMessage())
          .contains("status=403")
          .doesNotContain("private", "forged", "Bearer");
      assertThat(events.list.getFirst().getThrowableProxy()).isNull();
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    } finally {
      logger.detachAppender(events);
      events.stop();
      MDC.remove("requestId");
    }
  }

  @Test
  void overloadRejectsBeforeWorkLivenessStillWorksAndPermitRecovers() throws Exception {
    var metrics = new ApiRequestTelemetry();
    var filter = new ApiRequestBoundary(1, metrics);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var first =
          executor.submit(
              () -> {
                filter.doFilter(
                    new MockHttpServletRequest("GET", "/work"),
                    new MockHttpServletResponse(),
                    (req, res) -> {
                      entered.countDown();
                      try {
                        if (!release.await(5, TimeUnit.SECONDS))
                          throw new AssertionError("release timeout");
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(e);
                      }
                    });
                return null;
              });
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        var busy = new MockHttpServletResponse();
        filter.doFilter(
            new MockHttpServletRequest("GET", "/work"),
            busy,
            (a, b) -> {
              throw new AssertionError("not admitted");
            });
        assertThat(busy.getStatus()).isEqualTo(503);
        assertThat(busy.getHeader("Retry-After")).isEqualTo("1");
        var live = new AtomicBoolean();
        filter.doFilter(
            new MockHttpServletRequest("GET", "/api/v1/health"),
            new MockHttpServletResponse(),
            (a, b) -> live.set(true));
        assertThat(live).isTrue();
      } finally {
        release.countDown();
      }
      first.get(5, TimeUnit.SECONDS);
    }
    filter.doFilter(
        new MockHttpServletRequest("GET", "/work"), new MockHttpServletResponse(), (a, b) -> {});
    assertThat(metrics.getCapacityRejected()).isEqualTo(1);
  }

  @Test
  void exceptionRestoresCorrelationAndReleasesCapacity() throws Exception {
    var filter = new ApiRequestBoundary(1, new ApiRequestTelemetry());
    assertThatThrownBy(
            () ->
                filter.doFilter(
                    new MockHttpServletRequest(),
                    new MockHttpServletResponse(),
                    (a, b) -> {
                      throw new java.io.IOException("private");
                    }))
        .isInstanceOf(java.io.IOException.class);
    assertThat(MDC.get("requestId")).isNull();
    filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (a, b) -> {});
  }

  @Test
  void configurationBoundsAreEnforced() {
    assertThatThrownBy(() -> new ApiRateLimit(0, 1, 1, () -> 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApiRateLimit(1, 1001, 1, () -> 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApiRateLimit(1, 1, 10001, () -> 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ApiRequestBoundary(33, new ApiRequestTelemetry()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
