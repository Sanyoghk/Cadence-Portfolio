package com.cadence.infrastructure.ingest;

import static com.cadence.application.ports.UrlFetchFailure.Code.*;

import com.cadence.application.ports.UrlEvidenceFetcher;
import com.cadence.application.ports.UrlFetchFailure;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.*;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.*;
import javax.net.ssl.SSLException;
import org.springframework.stereotype.Component;

@Component
public final class JdkUrlEvidenceFetcher implements UrlEvidenceFetcher {
  @FunctionalInterface
  interface Resolver {
    InetAddress[] resolve(String host) throws Exception;
  }

  @FunctionalInterface
  interface Transport {
    PinnedHttpTransport.Response get(URI uri, InetAddress ip, long deadline) throws IOException;
  }

  private final ThreadPoolExecutor dns;

  static ThreadPoolExecutor dnsExecutor() {
    return new ThreadPoolExecutor(
        2,
        2,
        0,
        TimeUnit.SECONDS,
        // Future completion precedes the worker's return to getTask(). Two bounded slots let
        // admitted fetches submit their next redirect without requiring an idle handoff waiter.
        new ArrayBlockingQueue<>(2),
        Thread.ofPlatform().daemon().name("evidence-dns-", 0).factory(),
        new ThreadPoolExecutor.AbortPolicy());
  }

  private final ScheduledExecutorService deadlines = deadlineExecutor();

  private static ScheduledExecutorService deadlineExecutor() {
    var executor =
        new ScheduledThreadPoolExecutor(
            1, Thread.ofPlatform().daemon().name("evidence-deadline-", 0).factory());
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private final Resolver resolver;
  private final Transport transport;
  private final Semaphore capacity = new Semaphore(2);

  public JdkUrlEvidenceFetcher() {
    this.dns = dnsExecutor();
    this.resolver = InetAddress::getAllByName;
    this.transport = new PinnedHttpTransport(deadlines)::get;
  }

  JdkUrlEvidenceFetcher(Resolver resolver, Transport transport) {
    this(resolver, transport, dnsExecutor());
  }

  JdkUrlEvidenceFetcher(Resolver resolver, Transport transport, ThreadPoolExecutor dns) {
    this.dns = dns;
    this.resolver = resolver;
    this.transport = transport;
  }

  @Override
  public FetchedUrl fetch(String source) {
    if (!capacity.tryAcquire()) throw new UrlFetchFailure(UNAVAILABLE);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
    try {
      URI uri = PublicUrlPolicy.parse(source);
      for (int redirects = 0; ; redirects++) {
        InetAddress[] addresses = resolve(PublicUrlPolicy.host(uri), deadline);
        if (addresses.length == 0 || addresses.length > 32)
          throw new UrlFetchFailure(UNSAFE_TARGET);
        for (InetAddress address : addresses) PublicUrlPolicy.requirePublic(address);
        var response = transport.get(uri, addresses[0], deadline);
        if (Set.of(301, 302, 303, 307, 308).contains(response.status())) {
          if (redirects >= 3) throw new UrlFetchFailure(TOO_MANY_REDIRECTS);
          String location = response.headers().get("location");
          if (location == null) throw new UrlFetchFailure(INVALID_RESPONSE);
          URI next;
          try {
            next = PublicUrlPolicy.parse(uri.resolve(location).toASCIIString());
          } catch (IllegalArgumentException exception) {
            throw new UrlFetchFailure(UNSAFE_TARGET);
          }
          if (uri.getScheme().equals("https") && next.getScheme().equals("http"))
            throw new UrlFetchFailure(UNSAFE_TARGET);
          uri = next;
          continue;
        }
        if (response.status() == 408 || response.status() == 429 || response.status() >= 500)
          throw new UrlFetchFailure(UNAVAILABLE);
        if (response.status() != 200) throw new UrlFetchFailure(REMOTE_REJECTED);
        return new FetchedUrl(
            response.body(),
            response
                .headers()
                .get("content-type")
                .split(";", 2)[0]
                .trim()
                .toLowerCase(Locale.ROOT));
      }
    } catch (SSLException exception) {
      throw new UrlFetchFailure(TLS_REJECTED);
    } catch (SocketTimeoutException exception) {
      throw new UrlFetchFailure(TIMEOUT);
    } catch (IOException exception) {
      throw new UrlFetchFailure(System.nanoTime() >= deadline ? TIMEOUT : UNAVAILABLE);
    } finally {
      capacity.release();
    }
  }

  private InetAddress[] resolve(String host, long deadline) throws SocketTimeoutException {
    Future<InetAddress[]> result;
    try {
      result = dns.submit(() -> resolver.resolve(host));
    } catch (RejectedExecutionException exception) {
      throw new UrlFetchFailure(UNAVAILABLE);
    }
    try {
      return result.get(
          Math.min(3000, PinnedHttpTransport.remaining(deadline)), TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      throw new UrlFetchFailure(TIMEOUT);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new UrlFetchFailure(UNAVAILABLE);
    } catch (ExecutionException exception) {
      throw new UrlFetchFailure(UNAVAILABLE);
    } finally {
      result.cancel(true);
      // Timed-out queued tasks must not retain admission slots while native DNS is still blocked.
      dns.purge();
    }
  }

  @PreDestroy
  public void close() {
    dns.shutdownNow();
    deadlines.shutdownNow();
  }
}
