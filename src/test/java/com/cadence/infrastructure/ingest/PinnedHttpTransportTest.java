package com.cadence.infrastructure.ingest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.UrlFetchFailure;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PinnedHttpTransportTest {
  @Test
  void pinsTcpAndVerifiesOriginalTlsHostnameWithoutSendingCredentials() throws Exception {
    java.net.Socket tcp = mock(java.net.Socket.class);
    javax.net.ssl.SSLSocket tls = mock(javax.net.ssl.SSLSocket.class);
    javax.net.ssl.SSLSocketFactory factory = mock(javax.net.ssl.SSLSocketFactory.class);
    var parameters = new javax.net.ssl.SSLParameters();
    when(tls.getSSLParameters()).thenReturn(parameters);
    when(factory.createSocket(tcp, "example.com", 443, true)).thenReturn(tls);
    when(tls.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 2\r\nSet-Cookie: secret=1\r\n\r\nhi"
                    .getBytes()));
    ByteArrayOutputStream sent = new ByteArrayOutputStream();
    when(tls.getOutputStream()).thenReturn(sent);
    try (var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
      var transport = new PinnedHttpTransport(timer, () -> tcp, factory);
      var address = java.net.InetAddress.getByName("93.184.216.34");
      assertThat(
              transport
                  .get(
                      java.net.URI.create("https://example.com/path"),
                      address,
                      System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10))
                  .body())
          .hasSize(2);
      verify(tcp).connect(eq(new java.net.InetSocketAddress(address, 443)), eq(3000));
      assertThat(parameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
      assertThat(parameters.getServerNames())
          .containsExactly(new javax.net.ssl.SNIHostName("example.com"));
      verify(tls).startHandshake();
      verify(tls).setSoTimeout(5000);
      assertThat(sent.toString())
          .contains("Host: example.com\r\n")
          .doesNotContain("Authorization:", "Cookie:", "secret", "Referer:");
      verify(tls).close();
      verify(tcp).close();
    }
  }

  @Test
  void deadlineClosesSlowSocketEvenBeforeReadTimeout() throws Exception {
    java.net.Socket tcp = mock(java.net.Socket.class);
    var closed = new java.util.concurrent.CountDownLatch(1);
    doAnswer(
            invocation -> {
              closed.countDown();
              return null;
            })
        .when(tcp)
        .close();
    when(tcp.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    when(tcp.getInputStream())
        .thenReturn(
            new InputStream() {
              @Override
              public int read() throws IOException {
                try {
                  if (!closed.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    throw new AssertionError("Deadline did not close socket");
                } catch (InterruptedException exception) {
                  throw new IOException();
                }
                throw new java.net.SocketException("Socket closed");
              }
            });
    try (var timer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor()) {
      var transport =
          new PinnedHttpTransport(timer, () -> tcp, mock(javax.net.ssl.SSLSocketFactory.class));
      assertThatThrownBy(
              () ->
                  transport.get(
                      java.net.URI.create("http://example.com/"),
                      java.net.InetAddress.getByName("93.184.216.34"),
                      System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(100)))
          .isInstanceOf(IOException.class);
      assertThat(closed.getCount()).isZero();
    }
  }

  private PinnedHttpTransport.Response read(String wire) throws IOException {
    return PinnedHttpTransport.read(
        new ByteArrayInputStream(wire.getBytes(StandardCharsets.ISO_8859_1)));
  }

  @Test
  void acceptsLengthChunkedAndEofDelimitedText() throws Exception {
    assertThat(
            read("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: 5\r\n\r\nhello")
                .body())
        .isEqualTo("hello".getBytes());
    assertThat(
            read("HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nTransfer-Encoding: chunked\r\n\r\n2\r\nhi\r\n0\r\n\r\n")
                .body())
        .isEqualTo("hi".getBytes());
    assertThat(read("HTTP/1.0 200 OK\r\nContent-Type: text/plain\r\n\r\nhello").body()).hasSize(5);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "application/pdf",
        "application/octet-stream",
        "image/png",
        "application/xhtml+xml",
        ""
      })
  void rejectsUnsupportedContent(String type) {
    assertThatThrownBy(() -> read("HTTP/1.1 200 OK\r\nContent-Type: " + type + "\r\n\r\nx"))
        .hasMessageContaining("UNSUPPORTED_CONTENT");
  }

  @Test
  void rejectsOversizedLengthChunkedAndUnframedBodies() {
    String prefix = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n";
    assertThatThrownBy(() -> read(prefix + "Content-Length: 2097153\r\n\r\n"))
        .hasMessageContaining("RESPONSE_TOO_LARGE");
    assertThatThrownBy(() -> read(prefix + "Transfer-Encoding: chunked\r\n\r\n200001\r\n"))
        .hasMessageContaining("RESPONSE_TOO_LARGE");
    assertThatThrownBy(() -> read(prefix + "\r\n" + "x".repeat(2097153)))
        .hasMessageContaining("RESPONSE_TOO_LARGE");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Content-Length: 1\r\nContent-Length: 2",
        "Content-Length: 1\r\nTransfer-Encoding: chunked",
        "Content-Length: -1",
        "Transfer-Encoding: gzip",
        " Bad: folded",
        "Content-Length: 50"
      })
  void rejectsAmbiguousOrTruncatedResponses(String headers) {
    assertThatThrownBy(
            () -> read("HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n" + headers + "\r\n\r\nx"))
        .isInstanceOf(UrlFetchFailure.class);
  }

  @Test
  void rejectsCompressionAndOversizedHeaders() {
    assertThatThrownBy(
            () ->
                read(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Encoding: gzip\r\n\r\nx"))
        .hasMessageContaining("UNSUPPORTED_CONTENT");
    assertThatThrownBy(() -> read("HTTP/1.1 200 OK\r\nX-Huge: " + "x".repeat(33000)))
        .hasMessageContaining("INVALID_RESPONSE");
  }
}
