package com.cadence.infrastructure.storage;

import static com.cadence.application.ports.EvidenceRenderFailure.Code.*;

import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.EvidenceRenderFailure;
import com.cadence.application.ports.EvidenceRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;

/** Deadline-bound, pinned private HTTP client for two fixed byte operations. */
public final class RendererEvidenceAdapter implements EvidenceRenderer, AutoCloseable {
  @FunctionalInterface
  interface Resolver {
    InetAddress[] resolve(String host) throws Exception;
  }

  private final RendererProperties properties;
  private final List<ClamdPrivateAddressPolicy.Cidr> allowed;
  private final Resolver resolver;
  private final Semaphore capacity = new Semaphore(1);
  private final ExecutorService dns =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new SynchronousQueue<>(),
          Thread.ofPlatform().daemon().name("renderer-dns-", 0).factory(),
          new ThreadPoolExecutor.AbortPolicy());

  public RendererEvidenceAdapter(RendererProperties properties) {
    this(properties, InetAddress::getAllByName);
  }

  RendererEvidenceAdapter(RendererProperties properties, Resolver resolver) {
    this.properties = Objects.requireNonNull(properties);
    this.allowed =
        properties.allowedCidrs().stream().map(ClamdPrivateAddressPolicy::parse).toList();
    this.resolver = Objects.requireNonNull(resolver);
  }

  @Override
  public Screenshot screenshot(byte[] utf8Html) {
    requireBytes(utf8Html, MAX_HTML_BYTES);
    decode(utf8Html, INVALID_CONTENT);
    byte[] result =
        request(
            "/html/screenshot",
            "text/html; charset=utf-8",
            "image/png",
            utf8Html,
            MAX_SCREENSHOT_BYTES);
    validatePng(result);
    return new Screenshot(result, 1280, 720, "isolated-v1");
  }

  private static void validatePng(byte[] png) {
    byte[] signature = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    if (png.length < 45 || !Arrays.equals(Arrays.copyOf(png, 8), signature))
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    int offset = 8;
    boolean header = false, data = false;
    int bytesPerPixel = 0;
    var compressed = new ByteArrayOutputStream();
    while (offset < png.length) {
      if (png.length - offset < 12) throw new EvidenceRenderFailure(INVALID_RESPONSE);
      int size = ByteBuffer.wrap(png, offset, 4).getInt();
      if (size < 0 || size > png.length - offset - 12)
        throw new EvidenceRenderFailure(INVALID_RESPONSE);
      String type = new String(png, offset + 4, 4, StandardCharsets.US_ASCII);
      var crc = new java.util.zip.CRC32();
      crc.update(png, offset + 4, size + 4);
      if ((int) crc.getValue() != ByteBuffer.wrap(png, offset + 8 + size, 4).getInt())
        throw new EvidenceRenderFailure(INVALID_RESPONSE);
      if (!header) {
        if (!type.equals("IHDR")
            || size != 13
            || ByteBuffer.wrap(png, offset + 8, 4).getInt() != 1280
            || ByteBuffer.wrap(png, offset + 12, 4).getInt() != 720
            || png[offset + 16] != 8
            || (png[offset + 17] != 2 && png[offset + 17] != 6)
            || png[offset + 18] != 0
            || png[offset + 19] != 0
            || png[offset + 20] != 0) throw new EvidenceRenderFailure(INVALID_RESPONSE);
        header = true;
        bytesPerPixel = png[offset + 17] == 2 ? 3 : 4;
      } else if (type.equals("IDAT")) {
        if (size == 0) throw new EvidenceRenderFailure(INVALID_RESPONSE);
        compressed.write(png, offset + 8, size);
        data = true;
      } else if (type.equals("IEND")) {
        if (size != 0 || !data || offset + 12 != png.length)
          throw new EvidenceRenderFailure(INVALID_RESPONSE);
        validatePixels(compressed.toByteArray(), bytesPerPixel);
        return;
      } else if (!(type.equals("sRGB") && size == 1 && !data))
        throw new EvidenceRenderFailure(INVALID_RESPONSE);
      offset += size + 12;
    }
    throw new EvidenceRenderFailure(INVALID_RESPONSE);
  }

  private static void validatePixels(byte[] compressed, int bytesPerPixel) {
    var inflater = new java.util.zip.Inflater();
    try {
      inflater.setInput(compressed);
      byte[] row = new byte[1 + 1280 * bytesPerPixel];
      for (int index = 0; index < 720; index++) {
        int count = 0;
        while (count < row.length) {
          int produced = inflater.inflate(row, count, row.length - count);
          if (produced == 0) throw new EvidenceRenderFailure(INVALID_RESPONSE);
          count += produced;
        }
        if (Byte.toUnsignedInt(row[0]) > 4) throw new EvidenceRenderFailure(INVALID_RESPONSE);
      }
      if (inflater.inflate(new byte[1]) != 0
          || !inflater.finished()
          || inflater.getRemaining() != 0) throw new EvidenceRenderFailure(INVALID_RESPONSE);
    } catch (java.util.zip.DataFormatException malformed) {
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    } finally {
      inflater.end();
    }
  }

  @Override
  public DocumentText extractPdf(byte[] pdf) {
    requireBytes(pdf, EvidenceObjectStorage.MAX_QUARANTINE_BYTES);
    if (pdf.length < 5
        || !Arrays.equals(Arrays.copyOf(pdf, 5), "%PDF-".getBytes(StandardCharsets.US_ASCII)))
      throw new EvidenceRenderFailure(INVALID_CONTENT);
    String text =
        decode(
            request("/pdf/text", "application/pdf", "text/plain; charset=utf-8", pdf, 64 * 1024),
            INVALID_RESPONSE);
    if (text.length() > MAX_DOCUMENT_TEXT || text.codePoints().anyMatch(Character::isISOControl))
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    return new DocumentText(text, "isolated-v1");
  }

  private byte[] request(
      String path, String requestMedia, String responseMedia, byte[] content, int maxBytes) {
    if (!capacity.tryAcquire()) throw new EvidenceRenderFailure(UNAVAILABLE);
    long deadline = System.nanoTime() + properties.deadline().toNanos();
    try {
      byte[] body = content.clone();
      InetAddress address = resolve(deadline);
      try (SocketChannel channel = SocketChannel.open();
          Selector selector = Selector.open()) {
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_CONNECT);
        channel.connect(new InetSocketAddress(address, properties.port()));
        while (!channel.finishConnect())
          await(channel, selector, SelectionKey.OP_CONNECT, deadline);
        String headers =
            "POST "
                + path
                + " HTTP/1.1\r\nHost: renderer\r\nAuthorization: Bearer "
                + properties.token()
                + "\r\nContent-Type: "
                + requestMedia
                + "\r\nContent-Length: "
                + body.length
                + "\r\nConnection: close\r\n\r\n";
        write(
            channel,
            selector,
            ByteBuffer.wrap(headers.getBytes(StandardCharsets.US_ASCII)),
            deadline);
        write(channel, selector, ByteBuffer.wrap(body), deadline);
        return read(channel, selector, responseMedia, maxBytes, deadline);
      }
    } catch (EvidenceRenderFailure exception) {
      throw exception;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new EvidenceRenderFailure(UNAVAILABLE);
    } catch (IOException
        | ExecutionException
        | TimeoutException
        | RejectedExecutionException exception) {
      throw new EvidenceRenderFailure(UNAVAILABLE);
    } catch (IllegalArgumentException exception) {
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    } finally {
      capacity.release();
    }
  }

  private InetAddress resolve(long deadline)
      throws InterruptedException, ExecutionException, TimeoutException, IOException {
    Future<InetAddress[]> future = dns.submit(() -> resolver.resolve(properties.host()));
    try {
      InetAddress[] addresses =
          future.get(Math.min(remaining(deadline), 3000), TimeUnit.MILLISECONDS);
      ClamdPrivateAddressPolicy.requireAllowed(addresses, allowed);
      return addresses[0];
    } finally {
      future.cancel(true);
    }
  }

  private static byte[] read(
      SocketChannel channel, Selector selector, String media, int maxBytes, long deadline)
      throws IOException {
    var headers = new ByteArrayOutputStream();
    var body = new ByteArrayOutputStream();
    ByteBuffer buffer = ByteBuffer.allocate(4096);
    int length = -1;
    boolean completeHeaders = false;
    for (; ; ) {
      time(deadline);
      int count = channel.read(buffer);
      if (count == -1) {
        if (!completeHeaders || body.size() != length)
          throw new EvidenceRenderFailure(INVALID_RESPONSE);
        return body.toByteArray();
      }
      if (count == 0) {
        await(channel, selector, SelectionKey.OP_READ, deadline);
        continue;
      }
      buffer.flip();
      while (buffer.hasRemaining()) {
        int value = Byte.toUnsignedInt(buffer.get());
        if (!completeHeaders) {
          if (headers.size() >= 8192 || (value < 32 && value != 13 && value != 10) || value > 126)
            throw new EvidenceRenderFailure(INVALID_RESPONSE);
          headers.write(value);
          byte[] raw = headers.toByteArray();
          int n = raw.length;
          if (n >= 4
              && raw[n - 4] == 13
              && raw[n - 3] == 10
              && raw[n - 2] == 13
              && raw[n - 1] == 10) {
            length = responseLength(headers.toString(StandardCharsets.US_ASCII), media, maxBytes);
            completeHeaders = true;
          }
        } else {
          if (body.size() >= length) throw new EvidenceRenderFailure(INVALID_RESPONSE);
          body.write(value);
        }
      }
      buffer.clear();
    }
  }

  private static int responseLength(String response, String media, int maxBytes) {
    String[] lines = response.split("\r\n", -1);
    if (lines.length < 4 || !lines[0].matches("HTTP/1\\.[01] [0-9]{3} [A-Za-z ]{0,64}"))
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    int status = Integer.parseInt(lines[0].substring(9, 12));
    if (status == 503 || status == 429) throw new EvidenceRenderFailure(UNAVAILABLE);
    if (status != 200) throw new EvidenceRenderFailure(INVALID_RESPONSE);
    Map<String, String> values = new HashMap<>();
    for (int i = 1; i < lines.length - 2; i++) {
      int colon = lines[i].indexOf(':');
      if (colon <= 0 || !lines[i].substring(0, colon).matches("[A-Za-z0-9-]+"))
        throw new EvidenceRenderFailure(INVALID_RESPONSE);
      String key = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
      String value = lines[i].substring(colon + 1).trim();
      if (values.putIfAbsent(key, value) != null) throw new EvidenceRenderFailure(INVALID_RESPONSE);
    }
    String size = values.get("content-length");
    if (size == null
        || !size.matches("[0-9]{1,8}")
        || values.containsKey("transfer-encoding")
        || values.containsKey("content-encoding")
        || !media.equals(values.get("content-type"))
        || !"close".equals(values.get("connection"))
        || !"isolated-v1".equals(values.get("x-cadence-renderer-version")))
      throw new EvidenceRenderFailure(INVALID_RESPONSE);
    int length = Integer.parseInt(size);
    if (length > maxBytes) throw new EvidenceRenderFailure(INVALID_RESPONSE);
    return length;
  }

  private static void write(
      SocketChannel channel, Selector selector, ByteBuffer buffer, long deadline)
      throws IOException {
    while (buffer.hasRemaining()) {
      time(deadline);
      if (channel.write(buffer) == 0) await(channel, selector, SelectionKey.OP_WRITE, deadline);
    }
  }

  private static void await(SocketChannel channel, Selector selector, int operation, long deadline)
      throws IOException {
    channel.keyFor(selector).interestOps(operation);
    selector.select(remaining(deadline));
    selector.selectedKeys().clear();
    if (Thread.currentThread().isInterrupted()) throw new IOException("Renderer interrupted");
    time(deadline);
  }

  private static long remaining(long deadline) throws SocketTimeoutException {
    time(deadline);
    return Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()));
  }

  private static void time(long deadline) throws SocketTimeoutException {
    if (System.nanoTime() >= deadline)
      throw new SocketTimeoutException("Renderer deadline exceeded");
  }

  private static String decode(
      byte[] bytes, com.cadence.application.ports.EvidenceRenderFailure.Code code) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException exception) {
      throw new EvidenceRenderFailure(code);
    }
  }

  private static void requireBytes(byte[] bytes, int max) {
    if (bytes == null || bytes.length == 0 || bytes.length > max)
      throw new EvidenceRenderFailure(INVALID_CONTENT);
  }

  @Override
  public void close() {
    dns.shutdownNow();
  }
}
