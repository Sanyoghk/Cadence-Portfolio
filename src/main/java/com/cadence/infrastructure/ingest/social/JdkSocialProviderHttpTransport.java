package com.cadence.infrastructure.ingest.social;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

public final class JdkSocialProviderHttpTransport
    implements SocialProviderHttpTransport, AutoCloseable {
  static final int MAX_RESPONSE_BYTES = 65_536;
  private final HttpClient client;

  public JdkSocialProviderHttpTransport() {
    this(
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build());
  }

  JdkSocialProviderHttpTransport(HttpClient client) {
    this.client = java.util.Objects.requireNonNull(client);
  }

  @Override
  public byte[] request(URI uri, String bearer, String postJson, Duration remaining) {
    if (uri == null
        || !"https".equals(uri.getScheme())
        || uri.getPort() != -1
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || !Set.of("graph.facebook.com", "open.tiktokapis.com").contains(uri.getHost())
        || uri.getRawQuery() == null
        || uri.getRawQuery().contains("access_token")
        || bearer == null
        || bearer.length() > 4096
        || !bearer.matches("[A-Za-z0-9._~+/-]{10,4096}=*")
        || remaining == null
        || remaining.isNegative()
        || remaining.isZero()
        || remaining.compareTo(Duration.ofSeconds(30)) > 0)
      throw new SocialEvidenceUnavailableException();
    try {
      SocialHttpRuntimePolicy.require(true);
    } catch (IllegalArgumentException invalidRuntime) {
      throw new SocialEvidenceUnavailableException();
    }
    var builder =
        HttpRequest.newBuilder(uri)
            .timeout(remaining)
            .header("Authorization", "Bearer " + bearer)
            .header("Accept", "application/json")
            .header("Accept-Encoding", "identity");
    if (postJson == null) builder.GET();
    else
      builder
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(postJson));
    var future = client.sendAsync(builder.build(), response -> new BoundedSubscriber());
    try {
      var response = future.get(remaining.toNanos(), TimeUnit.NANOSECONDS);
      if (response.statusCode() == 429) throw new EvidenceQuotaExceededException();
      if (response.statusCode() != 200
          || !response
              .headers()
              .firstValue("Content-Type")
              .orElse("")
              .split(";", 2)[0]
              .strip()
              .equalsIgnoreCase("application/json")) throw new SocialEvidenceUnavailableException();
      return response.body();
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new SocialEvidenceUnavailableException();
    } catch (ExecutionException | TimeoutException failure) {
      throw new SocialEvidenceUnavailableException();
    } finally {
      future.cancel(true);
    }
  }

  @Override
  public void close() {
    client.close();
  }

  static final class BoundedSubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private Flow.Subscription subscription;

    @Override
    public CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(Flow.Subscription value) {
      if (subscription != null) {
        value.cancel();
        return;
      }
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
      for (ByteBuffer buffer : buffers) {
        if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
          subscription.cancel();
          result.completeExceptionally(new SocialEvidenceUnavailableException());
          return;
        }
        byte[] chunk = new byte[buffer.remaining()];
        buffer.get(chunk);
        bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable failure) {
      result.completeExceptionally(new SocialEvidenceUnavailableException());
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }
}
