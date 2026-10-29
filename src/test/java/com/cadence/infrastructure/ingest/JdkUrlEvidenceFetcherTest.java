package com.cadence.infrastructure.ingest;

import static com.cadence.application.ports.UrlFetchFailure.Code.*;
import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.UrlFetchFailure;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdkUrlEvidenceFetcherTest {
  private static InetAddress ip(String value) throws Exception {
    return InetAddress.getByName(value);
  }

  private static PinnedHttpTransport.Response ok() {
    return new PinnedHttpTransport.Response(
        200, Map.of("content-type", "text/html"), "<p>Public</p>".getBytes());
  }

  @Test
  void fetchesPublicSourceAndPinsTheValidatedAddressWithoutSecondResolution() throws Exception {
    AtomicInteger resolutions = new AtomicInteger();
    try (var fixture =
        fixture(
            host ->
                new InetAddress[] {
                  ip(resolutions.incrementAndGet() == 1 ? "93.184.216.34" : "127.0.0.1")
                },
            (uri, address, deadline) -> {
              assertThat(address.getHostAddress()).isEqualTo("93.184.216.34");
              assertThat(uri.getHost()).isEqualTo("example.com");
              return ok();
            })) {
      assertThat(fixture.fetcher.fetch("https://example.com/about").mediaType())
          .isEqualTo("text/html");
      assertThat(resolutions.get()).isEqualTo(1);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "127.0.0.1",
        "127.1",
        "2130706433",
        "::1",
        "::",
        "0.0.0.0",
        "0.1.2.3",
        "10.1.2.3",
        "172.16.0.1",
        "192.168.1.1",
        "169.254.1.2",
        "169.254.169.254",
        "100.100.100.200",
        "168.63.129.16",
        "224.0.0.1",
        "255.255.255.255",
        "fc00::1",
        "fd00:ec2::254",
        "fe80::1",
        "ff02::1",
        "::ffff:127.0.0.1",
        "64:ff9b::a00:1",
        "2002:7f00:1::",
        "2001:db8::1",
        "198.18.0.1"
      })
  void deniesUnsafeResolvedAddressesBeforeTransport(String address) throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip(address)},
            (uri, target, deadline) -> {
              throw new AssertionError("Must not connect");
            })) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://public-name.example/"))
          .isInstanceOf(UrlFetchFailure.class)
          .hasMessage("URL fetch failed: UNSAFE_TARGET");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost/",
        "http://x.localhost/",
        "http://metadata.google.internal/",
        "file:///etc/passwd",
        "ftp://example.com/",
        "http://user:secret@example.com/",
        "http://example.com:8080/",
        "http://[fe80::1%25eth0]/",
        "http://example.com./",
        "http://example.com/#secret",
        "not a URL"
      })
  void rejectsInvalidUrlsBeforeDns(String url) throws Exception {
    try (var fixture =
        fixture(
            host -> {
              throw new AssertionError("Must not resolve");
            },
            (uri, address, deadline) -> ok())) {
      assertThatThrownBy(() -> fixture.fetcher.fetch(url)).isInstanceOf(UrlFetchFailure.class);
    }
  }

  @Test
  void deniesMixedPublicPrivateDnsAnswers() throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34"), ip("10.0.0.1")},
            (uri, address, deadline) -> {
              throw new AssertionError();
            })) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessageContaining("UNSAFE_TARGET");
    }
  }

  @Test
  void revalidatesDnsOnSameHostRedirectAndBlocksRebinding() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    try (var fixture =
        fixture(
            host ->
                new InetAddress[] {ip(calls.incrementAndGet() == 1 ? "93.184.216.34" : "10.0.0.1")},
            (uri, address, deadline) ->
                new PinnedHttpTransport.Response(
                    302, Map.of("location", "/internal"), new byte[0]))) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessageContaining("UNSAFE_TARGET");
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @Test
  void rejectsRedirectToPrivateAddress() throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip(host.equals("example.com") ? "93.184.216.34" : host)},
            (uri, address, deadline) ->
                new PinnedHttpTransport.Response(
                    302, Map.of("location", "http://169.254.169.254/latest"), new byte[0]))) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("http://example.com/"))
          .hasMessageContaining("UNSAFE_TARGET");
    }
  }

  @Test
  void followsBoundedPublicRedirect() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) ->
                calls.incrementAndGet() == 1
                    ? new PinnedHttpTransport.Response(
                        302, Map.of("location", "/about", "set-cookie", "secret=1"), new byte[0])
                    : ok())) {
      assertThat(fixture.fetcher.fetch("https://example.com/").body()).isNotEmpty();
      assertThat(calls.get()).isEqualTo(2);
    }
  }

  @Test
  void limitsRedirects() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) -> {
              calls.incrementAndGet();
              return new PinnedHttpTransport.Response(
                  302, Map.of("location", "/again"), new byte[0]);
            })) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessageContaining("TOO_MANY_REDIRECTS");
      assertThat(calls.get()).isEqualTo(4);
    }
  }

  @Test
  void completedDnsFuturesCanQueueTheNextRedirectBeforeWorkersReturnToHandoff() throws Exception {
    var completedWorkers = new CountDownLatch(2);
    var queuedRedirect = new CountDownLatch(1);
    var releaseWorkers = new CountDownLatch(1);
    var production = JdkUrlEvidenceFetcher.dnsExecutor();
    // Reuse the production queue and bounds, but deliberately delay worker reuse after completing
    // each of the first two futures. A SynchronousQueue deterministically rejects redirect three.
    var controlled =
        new ThreadPoolExecutor(
            production.getCorePoolSize(),
            production.getMaximumPoolSize(),
            production.getKeepAliveTime(TimeUnit.SECONDS),
            TimeUnit.SECONDS,
            production.getQueue(),
            production.getThreadFactory(),
            production.getRejectedExecutionHandler()) {
          @Override
          protected void afterExecute(Runnable task, Throwable failure) {
            completedWorkers.countDown();
            try {
              if (!releaseWorkers.await(10, TimeUnit.SECONDS))
                throw new AssertionError("DNS worker release timed out");
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
            }
          }

          @Override
          public void execute(Runnable task) {
            super.execute(task);
            if (getQueue().contains(task)) queuedRedirect.countDown();
          }
        };
    production.shutdown();
    var calls = new AtomicInteger();
    try (var fixture =
            new Fixture(
                new JdkUrlEvidenceFetcher(
                    host -> new InetAddress[] {ip("93.184.216.34")},
                    (uri, address, deadline) -> {
                      calls.incrementAndGet();
                      return new PinnedHttpTransport.Response(
                          302, Map.of("location", "/again"), new byte[0]);
                    },
                    controlled));
        var caller = Executors.newSingleThreadExecutor()) {
      var result =
          caller.submit(
              () -> {
                assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
                    .hasMessageContaining("TOO_MANY_REDIRECTS");
              });
      try {
        assertThat(completedWorkers.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(queuedRedirect.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(controlled.getQueue()).hasSize(1);
        assertThat(controlled.getQueue().remainingCapacity()).isEqualTo(1);
      } finally {
        releaseWorkers.countDown();
      }
      result.get(10, TimeUnit.SECONDS);
      assertThat(calls).hasValue(4);
    } finally {
      releaseWorkers.countDown();
      controlled.shutdownNow();
    }
  }

  @Test
  void dnsExecutorRetainsFixedThreadAndQueueBoundsAndRejectsSaturation() throws Exception {
    var dns = JdkUrlEvidenceFetcher.dnsExecutor();
    var running = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    try {
      Callable<Void> blocked =
          () -> {
            running.countDown();
            if (!release.await(10, TimeUnit.SECONDS))
              throw new AssertionError("DNS saturation fixture timed out");
            return null;
          };
      dns.submit(blocked);
      dns.submit(blocked);
      assertThat(running.await(2, TimeUnit.SECONDS)).isTrue();
      dns.submit(blocked);
      dns.submit(blocked);
      assertThat(dns.getMaximumPoolSize()).isEqualTo(2);
      assertThat(dns.getQueue()).hasSize(2);
      assertThatThrownBy(() -> dns.submit(blocked)).isInstanceOf(RejectedExecutionException.class);
    } finally {
      release.countDown();
      dns.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {408, 429, 500, 503})
  void classifiesTemporaryHttpFailures(int status) throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) ->
                new PinnedHttpTransport.Response(status, Map.of(), new byte[0]))) {
      assertThatExceptionOfType(UrlFetchFailure.class)
          .isThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .satisfies(failure -> assertThat(failure.retryable()).isTrue());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 204})
  void classifiesPermanentHttpFailures(int status) throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) ->
                new PinnedHttpTransport.Response(status, Map.of(), new byte[0]))) {
      assertThatExceptionOfType(UrlFetchFailure.class)
          .isThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .satisfies(failure -> assertThat(failure.retryable()).isFalse());
    }
  }

  @Test
  void sanitizesTimeoutAndTlsErrors() throws Exception {
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) -> {
              throw new SocketTimeoutException("internal 10.0.0.1 secret");
            })) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessage("URL fetch failed: TIMEOUT")
          .hasNoCause();
    }
    try (var fixture =
        fixture(
            host -> new InetAddress[] {ip("93.184.216.34")},
            (uri, address, deadline) -> {
              throw new SSLHandshakeException("secret");
            })) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessage("URL fetch failed: TLS_REJECTED")
          .hasNoCause();
    }
  }

  @Test
  void boundsDnsWait() throws Exception {
    try (var fixture =
        fixture(
            host -> {
              Thread.sleep(10000);
              return new InetAddress[0];
            },
            (uri, address, deadline) -> ok())) {
      assertThatThrownBy(() -> fixture.fetcher.fetch("https://example.com/"))
          .hasMessageContaining("TIMEOUT");
    }
  }

  private Fixture fixture(
      JdkUrlEvidenceFetcher.Resolver resolver, JdkUrlEvidenceFetcher.Transport transport) {
    return new Fixture(new JdkUrlEvidenceFetcher(resolver, transport));
  }

  private record Fixture(JdkUrlEvidenceFetcher fetcher) implements AutoCloseable {
    @Override
    public void close() {
      fetcher.close();
    }
  }
}
