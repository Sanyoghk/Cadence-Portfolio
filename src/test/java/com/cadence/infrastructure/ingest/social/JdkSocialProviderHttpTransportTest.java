package com.cadence.infrastructure.ingest.social;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

class JdkSocialProviderHttpTransportTest {
  @Test
  void boundedSubscriberCancelsBeforeAllocatingOversizedBody() {
    var subscriber = new JdkSocialProviderHttpTransport.BoundedSubscriber();
    var subscription = mock(Flow.Subscription.class);
    subscriber.onSubscribe(subscription);
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65537])));
    verify(subscription).cancel();
    assertThatThrownBy(() -> subscriber.getBody().toCompletableFuture().join())
        .hasCauseInstanceOf(SocialEvidenceUnavailableException.class);
  }

  @Test
  void subscriberHandlesFragmentedBodiesAndNeverExposesProviderErrors() {
    var subscriber = new JdkSocialProviderHttpTransport.BoundedSubscriber();
    subscriber.onSubscribe(mock(Flow.Subscription.class));
    subscriber.onNext(List.of(ByteBuffer.wrap(new byte[] {1, 2}), ByteBuffer.wrap(new byte[] {3})));
    subscriber.onComplete();
    assertThat(subscriber.getBody().toCompletableFuture().join()).containsExactly(1, 2, 3);
    var failed = new JdkSocialProviderHttpTransport.BoundedSubscriber();
    failed.onError(new IllegalStateException("Bearer secret"));
    assertThatThrownBy(() -> failed.getBody().toCompletableFuture().join())
        .hasCauseInstanceOf(SocialEvidenceUnavailableException.class);
  }

  @Test
  void forbidsPrivateTargetsHttpUserinfoUrlTokensAndUnknownHostsBeforeNetwork() {
    try (var transport = new JdkSocialProviderHttpTransport()) {
      for (String uri :
          List.of(
              "http://graph.facebook.com/me?fields=id",
              "https://127.0.0.1/me?fields=id",
              "https://evil.example/me?fields=id",
              "https://user:password@graph.facebook.com/me?fields=id",
              "https://graph.facebook.com/me?access_token=secret",
              "https://graph.facebook.com:8443/me?fields=id"))
        assertThatThrownBy(
                () ->
                    transport.request(
                        URI.create(uri), "fixture-token", null, Duration.ofSeconds(1)))
            .isInstanceOf(SocialEvidenceUnavailableException.class)
            .hasNoCause();
    }
  }

  @Test
  void tokenPaddingCannotBypass4096ByteHeaderBoundaryBeforeNetwork() {
    var client = mock(HttpClient.class);
    try (var transport = new JdkSocialProviderHttpTransport(client)) {
      for (String token : List.of("a".repeat(4095) + "==", "fixture-token" + "=".repeat(5000))) {
        assertThatThrownBy(
                () ->
                    transport.request(
                        URI.create("https://graph.facebook.com/v25.0/1?fields=id"),
                        token,
                        null,
                        Duration.ofSeconds(1)))
            .isInstanceOf(SocialEvidenceUnavailableException.class)
            .hasNoCause()
            .hasMessageNotContaining(token);
      }
      verifyNoInteractions(client);
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void exactTokenLengthBoundaryIsSentOnlyAsBearerHeader() {
    var client = mock(HttpClient.class);
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.headers())
        .thenReturn(
            HttpHeaders.of(
                Map.of("Content-Type", List.of("application/json")), (name, value) -> true));
    when(response.body()).thenReturn(new byte[] {1});
    when(client.<byte[]>sendAsync(any(HttpRequest.class), any()))
        .thenReturn(CompletableFuture.completedFuture(response));
    String token = "a".repeat(4094) + "==";
    try (var transport = new JdkSocialProviderHttpTransport(client)) {
      assertThat(
              transport.request(
                  URI.create("https://graph.facebook.com/v25.0/1?fields=id"),
                  token,
                  null,
                  Duration.ofSeconds(1)))
          .containsExactly(1);
      verify(client)
          .<byte[]>sendAsync(
              argThat(
                  request ->
                      request
                              .headers()
                              .firstValue("Authorization")
                              .orElse("")
                              .equals("Bearer " + token)
                          && !request.uri().toString().contains(token)),
              any());
    }
  }

  @Test
  void effectiveWireLoggingIsRejectedBeforeAnyHttpRequest() {
    String property = "jdk.httpclient.HttpClient.log";
    String previous = System.getProperty(property);
    var client = mock(HttpClient.class);
    try (var transport = new JdkSocialProviderHttpTransport(client)) {
      System.setProperty(property, "headers");
      assertThatThrownBy(
              () ->
                  transport.request(
                      URI.create("https://graph.facebook.com/v25.0/1?fields=id"),
                      "fixture-token",
                      null,
                      Duration.ofSeconds(1)))
          .isInstanceOf(SocialEvidenceUnavailableException.class)
          .hasNoCause();
      verifyNoInteractions(client);
    } finally {
      if (previous == null) System.clearProperty(property);
      else System.setProperty(property, previous);
    }
  }
}
