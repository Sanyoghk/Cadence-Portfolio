package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RendererEvidenceAdapterTest {
  private static final String TOKEN =
      java.util.UUID.randomUUID().toString().replace("-", "")
          + java.util.UUID.randomUUID().toString().replace("-", "");
  private static final byte[] PDF = "%PDF-fixture".getBytes(StandardCharsets.US_ASCII);

  @Test
  void fixedOperationsPreserveExactBytesAndValidateResultProvenance() throws Exception {
    byte[] html = "<h1>Website</h1>".getBytes(StandardCharsets.UTF_8);
    try (Server server =
            new Server(
                socket -> {
                  assertRequest(socket, "/html/screenshot", html);
                  reply(socket, png(), "image/png", "");
                });
        var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
      var result = adapter.screenshot(html);
      assertThat(result.width()).isEqualTo(1280);
      assertThat(result.height()).isEqualTo(720);
      assertThat(result.version()).isEqualTo("isolated-v1");
      byte[] copy = result.bytes();
      copy[0] = 0;
      assertThat(result.bytes()[0]).isEqualTo((byte) 137);
      server.verify();
    }
    try (Server server =
            new Server(
                socket -> {
                  assertRequest(socket, "/pdf/text", PDF);
                  reply(
                      socket,
                      "Document text".getBytes(StandardCharsets.UTF_8),
                      "text/plain; charset=utf-8",
                      "");
                });
        var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
      assertThat(adapter.extractPdf(PDF))
          .isEqualTo(new EvidenceRenderer.DocumentText("Document text", "isolated-v1"));
      server.verify();
    }
  }

  @Test
  void rejectsInvalidInputBeforeAnyNetworkRequest() {
    try (var adapter = adapter(1, Duration.ofSeconds(1))) {
      for (byte[] content :
          new byte[][] {
            null,
            new byte[0],
            new byte[EvidenceRenderer.MAX_HTML_BYTES + 1],
            new byte[] {(byte) 255}
          })
        assertThatThrownBy(() -> adapter.screenshot(content))
            .isInstanceOf(EvidenceRenderFailure.class);
      assertThatThrownBy(() -> adapter.extractPdf(new byte[] {1}))
          .isInstanceOf(EvidenceRenderFailure.class);
    }
  }

  @Test
  void rejectsWrongDimensionsMalformedPngAndMalformedDocumentText() throws Exception {
    byte[] wrong = png();
    ByteBuffer.wrap(wrong, 16, 4).putInt(1000000);
    for (byte[] response : List.of(new byte[] {1}, wrong)) {
      try (Server server =
              new Server(
                  socket -> {
                    assertRequest(socket, "/html/screenshot", new byte[] {65});
                    reply(socket, response, "image/png", "");
                  });
          var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
        assertThatThrownBy(() -> adapter.screenshot(new byte[] {65}))
            .isInstanceOf(EvidenceRenderFailure.class);
        server.verify();
      }
    }
    for (byte[] response :
        List.of(
            new byte[] {(byte) 255},
            "sensitive\ntext".getBytes(StandardCharsets.UTF_8),
            "x".repeat(16001).getBytes(StandardCharsets.UTF_8))) {
      try (Server server =
              new Server(
                  socket -> {
                    assertRequest(socket, "/pdf/text", PDF);
                    reply(socket, response, "text/plain; charset=utf-8", "");
                  });
          var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
        assertThatThrownBy(() -> adapter.extractPdf(PDF))
            .isInstanceOf(EvidenceRenderFailure.class)
            .hasMessage("Evidence rendering failed");
        server.verify();
      }
    }
  }

  @Test
  void rejectsMalformedTruncatedOversizedTrailingAndInvalidFilterPngStreams() throws Exception {
    int rowBytes = 1 + 1280 * 3;
    byte[] invalidFilter = new byte[rowBytes * 720];
    invalidFilter[0] = 5;
    for (byte[] compressed :
        List.of(
            new byte[] {1},
            deflate(new byte[rowBytes * 719]),
            deflate(new byte[rowBytes * 721]),
            deflate(invalidFilter),
            concat(deflate(new byte[rowBytes * 720]), new byte[] {1}))) {
      byte[] response = pngWithData(compressed);
      try (Server server =
              new Server(
                  socket -> {
                    assertRequest(socket, "/html/screenshot", new byte[] {65});
                    reply(socket, response, "image/png", "");
                  });
          var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
        assertThatThrownBy(() -> adapter.screenshot(new byte[] {65}))
            .isInstanceOf(EvidenceRenderFailure.class);
        server.verify();
      }
    }
  }

  @Test
  void rejectsAmbiguousFramingMissingVersionRedirectsAndOversizedResponse() throws Exception {
    List<String> responses =
        List.of(
            "HTTP/1.1 302 Found\r\nLocation: http://127.0.0.1/\r\nContent-Length: 0\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Length: 1\r\n\r\nx",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\nx",
            "HTTP/1.1 200 OK\r\nContent-Length: 99999999\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\nX-Cadence-Renderer-Version: isolated-v1\r\n\r\n",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\n\r\nx",
            "HTTP/1.1 200 OK\r\nContent-Length: 2\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\nX-Cadence-Renderer-Version: isolated-v1\r\n\r\nx",
            "HTTP/1.1 200 OK\r\nContent-Length: 1\r\nContent-Type: text/plain; charset=utf-8\r\nConnection: close\r\nX-Cadence-Renderer-Version: isolated-v1\r\n\r\nxx",
            "HTTP/1.1 200 OK\r\nX-Oversized: " + "x".repeat(8192));
    for (String response : responses) {
      try (Server server =
              new Server(
                  socket -> {
                    assertRequest(socket, "/pdf/text", PDF);
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                  });
          var adapter = adapter(server.port(), Duration.ofSeconds(2))) {
        assertThatThrownBy(() -> adapter.extractPdf(PDF)).isInstanceOf(EvidenceRenderFailure.class);
        server.verify();
      }
    }
  }

  @Test
  void hardDeadlineCoversSlowResponseAndNoEof() throws Exception {
    try (Server server =
            new Server(
                socket -> {
                  assertRequest(socket, "/pdf/text", PDF);
                  reply(socket, new byte[] {65}, "text/plain; charset=utf-8", "");
                  Thread.sleep(1000);
                });
        var adapter = adapter(server.port(), Duration.ofMillis(150))) {
      long start = System.nanoTime();
      assertThatThrownBy(() -> adapter.extractPdf(PDF)).isInstanceOf(EvidenceRenderFailure.class);
      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }
  }

  @Test
  void deniesPublicDnsAndMasksConfigurationSecrets() throws Exception {
    try (var adapter =
        new RendererEvidenceAdapter(
            properties(1, Duration.ofSeconds(1)),
            host ->
                new InetAddress[] {
                  InetAddress.getByName("127.0.0.1"), InetAddress.getByName("169.254.169.254")
                })) {
      assertThatThrownBy(() -> adapter.extractPdf(PDF)).isInstanceOf(EvidenceRenderFailure.class);
    }
    assertThat(properties(1, Duration.ofSeconds(1)).toString()).doesNotContain(TOKEN);
    var context =
        new ApplicationContextRunner()
            .withUserConfiguration(RendererConfiguration.class)
            .withPropertyValues(
                "spring.profiles.active=renderer",
                "cadence.evidence.renderer.host=renderer",
                "cadence.evidence.renderer.allowed-cidrs=10.0.0.0/8",
                "cadence.evidence.renderer.token=" + TOKEN,
                "cadence.evidence.renderer.private-channel-acknowledged=true");
    context.run(
        value -> assertThat(value).hasNotFailed().hasSingleBean(RendererEvidenceAdapter.class));
    context
        .withPropertyValues("cadence.evidence.renderer.token=short")
        .run(value -> assertThat(value).hasFailed());
  }

  private static RendererEvidenceAdapter adapter(int port, Duration deadline) {
    return new RendererEvidenceAdapter(properties(port, deadline));
  }

  private static RendererProperties properties(int port, Duration deadline) {
    return new RendererProperties("127.0.0.1", port, List.of("127.0.0.0/8"), TOKEN, deadline, true);
  }

  private static byte[] png() throws IOException {
    var bytes = new ByteArrayOutputStream();
    javax.imageio.ImageIO.write(
        new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_RGB),
        "png",
        bytes);
    return bytes.toByteArray();
  }

  private static byte[] deflate(byte[] pixels) {
    var deflater = new java.util.zip.Deflater();
    try {
      deflater.setInput(pixels);
      deflater.finish();
      var result = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      while (!deflater.finished()) result.write(buffer, 0, deflater.deflate(buffer));
      return result.toByteArray();
    } finally {
      deflater.end();
    }
  }

  private static byte[] concat(byte[] first, byte[] second) {
    var result = new ByteArrayOutputStream();
    result.writeBytes(first);
    result.writeBytes(second);
    return result.toByteArray();
  }

  private static byte[] pngWithData(byte[] compressed) {
    var result = new ByteArrayOutputStream();
    result.writeBytes(new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    byte[] header =
        ByteBuffer.allocate(13).putInt(1280).putInt(720).put(new byte[] {8, 2, 0, 0, 0}).array();
    for (var chunk :
        List.of(
            new Object[] {"IHDR", header},
            new Object[] {"IDAT", compressed},
            new Object[] {"IEND", new byte[0]})) {
      byte[] type = ((String) chunk[0]).getBytes(StandardCharsets.US_ASCII);
      byte[] data = (byte[]) chunk[1];
      result.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
      result.writeBytes(type);
      result.writeBytes(data);
      var crc = new java.util.zip.CRC32();
      crc.update(type);
      crc.update(data);
      result.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
    return result.toByteArray();
  }

  private static void assertRequest(Socket socket, String path, byte[] expected)
      throws IOException {
    ByteArrayOutputStream header = new ByteArrayOutputStream();
    while (!header.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
      int value = socket.getInputStream().read();
      if (value == -1 || header.size() > 8192) throw new IOException("Invalid test request");
      header.write(value);
    }
    String text = header.toString(StandardCharsets.US_ASCII);
    assertThat(text)
        .startsWith("POST " + path + " HTTP/1.1\r\n")
        .contains("Content-Length: " + expected.length + "\r\n");
    assertThat(socket.getInputStream().readNBytes(expected.length)).isEqualTo(expected);
  }

  private static void reply(Socket socket, byte[] body, String media, String extra)
      throws IOException {
    socket
        .getOutputStream()
        .write(
            ("HTTP/1.1 200 OK\r\nContent-Length: "
                    + body.length
                    + "\r\nContent-Type: "
                    + media
                    + "\r\nConnection: close\r\nX-Cadence-Renderer-Version: isolated-v1\r\n"
                    + extra
                    + "\r\n")
                .getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().write(body);
  }

  @FunctionalInterface
  private interface Handler {
    void run(Socket socket) throws Exception;
  }

  private static final class Server implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().factory());
    private final Future<?> task;

    Server(Handler handler) throws IOException {
      server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
      task =
          executor.submit(
              () -> {
                try (Socket socket = server.accept()) {
                  socket.setSoTimeout(3000);
                  handler.run(socket);
                } catch (Exception failure) {
                  throw new CompletionException(failure);
                }
              });
    }

    int port() {
      return server.getLocalPort();
    }

    void verify() throws Exception {
      task.get(4, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws IOException {
      server.close();
      task.cancel(true);
      executor.shutdownNow();
    }
  }
}
