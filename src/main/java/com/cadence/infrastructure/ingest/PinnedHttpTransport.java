package com.cadence.infrastructure.ingest;

import static com.cadence.application.ports.UrlFetchFailure.Code.*;

import com.cadence.application.ports.UrlFetchFailure;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;

/** Minimal, non-reusing HTTP/1.1 GET transport. Ambiguous framing fails closed. */
final class PinnedHttpTransport {
  static final int MAX_BODY = 2 * 1024 * 1024;
  private static final int MAX_HEADERS = 32 * 1024;
  private final ScheduledExecutorService deadlines;
  private final java.util.function.Supplier<Socket> sockets;
  private final SSLSocketFactory tlsFactory;

  PinnedHttpTransport(ScheduledExecutorService deadlines) {
    this(
        deadlines,
        () -> new Socket(Proxy.NO_PROXY),
        (SSLSocketFactory) SSLSocketFactory.getDefault());
  }

  PinnedHttpTransport(
      ScheduledExecutorService deadlines,
      java.util.function.Supplier<Socket> sockets,
      SSLSocketFactory tlsFactory) {
    this.deadlines = deadlines;
    this.sockets = sockets;
    this.tlsFactory = tlsFactory;
  }

  record Response(int status, Map<String, String> headers, byte[] body) {}

  Response get(URI uri, InetAddress address, long deadline) throws IOException {
    PublicUrlPolicy.requirePublic(address);
    try (Socket socket = sockets.get()) {
      var cancellation =
          deadlines.schedule(() -> close(socket), remaining(deadline), TimeUnit.MILLISECONDS);
      try {
        int port = uri.getScheme().equals("https") ? 443 : 80;
        socket.connect(new InetSocketAddress(address, port), Math.min(3000, remaining(deadline)));
        socket.setSoTimeout(Math.min(5000, remaining(deadline)));
        if (uri.getScheme().equals("https")) {
          try (SSLSocket tls =
              (SSLSocket) tlsFactory.createSocket(socket, PublicUrlPolicy.host(uri), port, true)) {
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            String host = PublicUrlPolicy.host(uri);
            if (!host.contains(":") && !host.matches("[0-9.]+")) {
              parameters.setServerNames(List.of(new SNIHostName(host)));
            }
            tls.setSSLParameters(parameters);
            tls.setSoTimeout(Math.min(5000, remaining(deadline)));
            tls.startHandshake();
            return exchange(tls, uri);
          }
        }
        return exchange(socket, uri);
      } finally {
        cancellation.cancel(false);
      }
    }
  }

  static int remaining(long deadline) throws SocketTimeoutException {
    long millis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
    if (millis <= 0) throw new SocketTimeoutException();
    return (int) Math.min(millis, Integer.MAX_VALUE);
  }

  private static void close(Socket socket) {
    try {
      socket.close();
    } catch (IOException ignored) {
      /* Deadline cancellation. */
    }
  }

  private Response exchange(Socket socket, URI uri) throws IOException {
    String path = uri.getRawPath();
    if (path == null || path.isEmpty()) path = "/";
    if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();
    String request =
        "GET "
            + path
            + " HTTP/1.1\r\nHost: "
            + uri.getHost()
            + "\r\nUser-Agent: Cadence-Evidence/1.0\r\nAccept: text/html, text/plain\r\n"
            + "Accept-Encoding: identity\r\nConnection: close\r\n\r\n";
    socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().flush();
    return read(new BufferedInputStream(socket.getInputStream()));
  }

  static Response read(InputStream input) throws IOException {
    Budget budget = new Budget();
    String statusLine = line(input, budget);
    if (!statusLine.matches("HTTP/1\\.[01] [1-5][0-9]{2}( .*)?")) throw failure(INVALID_RESPONSE);
    int status = Integer.parseInt(statusLine.substring(9, 12));
    Map<String, String> headers = new HashMap<>();
    for (String line = line(input, budget); !line.isEmpty(); line = line(input, budget)) {
      int colon = line.indexOf(':');
      if (colon <= 0 || !line.substring(0, colon).matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"))
        throw failure(INVALID_RESPONSE);
      String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
      String value = line.substring(colon + 1).trim();
      // Repeated non-framing fields (e.g. Set-Cookie) are ignored, never replayed.
      if (headers.putIfAbsent(name, value) != null
          && Set.of(
                  "content-length",
                  "transfer-encoding",
                  "content-type",
                  "location",
                  "content-encoding")
              .contains(name)) throw failure(INVALID_RESPONSE);
    }
    if (status != 200) return new Response(status, headers, new byte[0]);
    String type =
        headers.getOrDefault("content-type", "").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    if (!Set.of("text/html", "text/plain").contains(type)) throw failure(UNSUPPORTED_CONTENT);
    if (!headers.getOrDefault("content-encoding", "identity").equalsIgnoreCase("identity"))
      throw failure(UNSUPPORTED_CONTENT);
    String length = headers.get("content-length"), transfer = headers.get("transfer-encoding");
    if (length != null && transfer != null) throw failure(INVALID_RESPONSE);
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    if (transfer != null) {
      if (!transfer.equalsIgnoreCase("chunked")) throw failure(INVALID_RESPONSE);
      for (int chunks = 0; ; chunks++) {
        if (chunks >= 8192) throw failure(INVALID_RESPONSE);
        String size = line(input, budget).split(";", 2)[0];
        long count = number(size, 16);
        if (count == 0) {
          // Trailers have no meaning for evidence; bound and discard them.
          while (!line(input, budget).isEmpty()) {}
          break;
        }
        copy(input, body, count);
        if (!line(input, budget).isEmpty()) throw failure(INVALID_RESPONSE);
      }
    } else if (length != null) {
      copy(input, body, number(length, 10));
    } else {
      byte[] buffer = new byte[8192];
      int count;
      while ((count = input.read(buffer)) != -1) {
        if (body.size() + count > MAX_BODY) throw failure(RESPONSE_TOO_LARGE);
        body.write(buffer, 0, count);
      }
    }
    if (body.size() == 0) throw failure(INVALID_RESPONSE);
    return new Response(status, headers, body.toByteArray());
  }

  private static long number(String value, int radix) {
    if (!value.matches(radix == 16 ? "[0-9a-fA-F]{1,16}" : "[0-9]{1,19}"))
      throw failure(INVALID_RESPONSE);
    try {
      return Long.parseLong(value, radix);
    } catch (NumberFormatException exception) {
      throw failure(RESPONSE_TOO_LARGE);
    }
  }

  private static void copy(InputStream input, ByteArrayOutputStream output, long count)
      throws IOException {
    if (count > MAX_BODY - output.size()) throw failure(RESPONSE_TOO_LARGE);
    byte[] buffer = new byte[8192];
    while (count > 0) {
      int read = input.read(buffer, 0, (int) Math.min(count, buffer.length));
      if (read == -1) throw failure(INVALID_RESPONSE);
      output.write(buffer, 0, read);
      count -= read;
    }
  }

  private static String line(InputStream input, Budget budget) throws IOException {
    StringBuilder result = new StringBuilder();
    while (true) {
      int c = input.read();
      if (++budget.used > MAX_HEADERS || result.length() > 8192 || c == -1)
        throw failure(INVALID_RESPONSE);
      if (c == '\r') {
        if (input.read() != '\n' || ++budget.used > MAX_HEADERS) throw failure(INVALID_RESPONSE);
        return result.toString();
      }
      if (c < 32 && c != '\t' || c == 127) throw failure(INVALID_RESPONSE);
      result.append((char) c);
    }
  }

  private static UrlFetchFailure failure(com.cadence.application.ports.UrlFetchFailure.Code code) {
    return new UrlFetchFailure(code);
  }

  private static final class Budget {
    int used;
  }
}
