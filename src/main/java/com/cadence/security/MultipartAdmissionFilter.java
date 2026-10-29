package com.cadence.security;

import com.cadence.http.ApiProblems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bounds request bodies before DispatcherServlet allocates multipart bodies and byte clones. */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public final class MultipartAdmissionFilter extends OncePerRequestFilter {
  private final Semaphore uploads;
  private final int maxBodyBytes;

  public MultipartAdmissionFilter(int maxConcurrentUploads) {
    this(maxConcurrentUploads, 131072);
  }

  @Autowired
  public MultipartAdmissionFilter(
      @Value("${cadence.evidence.max-concurrent-uploads:2}") int maxConcurrentUploads,
      @Value("${cadence.evidence.max-non-multipart-bytes:131072}") int maxBodyBytes) {
    if (maxConcurrentUploads < 1 || maxConcurrentUploads > 8)
      throw new IllegalArgumentException("Concurrent evidence uploads must be between 1 and 8");
    if (maxBodyBytes < 131072 || maxBodyBytes > 1048576)
      throw new IllegalArgumentException(
          "Non-multipart body limit must be between 128 KiB and 1 MiB");
    uploads = new Semaphore(maxConcurrentUploads);
    this.maxBodyBytes = maxBodyBytes;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String contentType = request.getContentType();
    if (contentType == null
        || !contentType.strip().toLowerCase(Locale.ROOT).startsWith("multipart/")) {
      if (request.getContentLengthLong() > maxBodyBytes) {
        bodyTooLarge(response);
        return;
      }
      try {
        chain.doFilter(new BoundedBodyRequest(request, maxBodyBytes), response);
      } catch (BodyLimitExceededException exceeded) {
        if (response.isCommitted()) throw exceeded;
        response.resetBuffer();
        bodyTooLarge(response);
      }
      return;
    }
    // Match every multipart request, independent of method/path and URI decoding. Header access
    // does not parse parameters or read a body; rejected requests never reach multipart resolution.
    if (!uploads.tryAcquire()) {
      response.setHeader("Retry-After", "1");
      ApiProblems.write(
          response,
          ApiProblems.create(
              503,
              "upload_capacity_unavailable",
              "Upload capacity unavailable",
              "Evidence uploads are busy; try again later",
              true));
      return;
    }
    try {
      chain.doFilter(request, response);
    } finally {
      uploads.release();
    }
  }

  /** Fixed safe error; controller advice may translate a parser-wrapped instance to HTTP 413. */
  public static final class BodyLimitExceededException extends IOException {
    public BodyLimitExceededException() {
      super("Request body exceeds configured limit");
    }
  }

  private void bodyTooLarge(HttpServletResponse response) throws IOException {
    ApiProblems.write(response, ApiProblems.status(413));
  }

  private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
    private final int limit;
    private ServletInputStream input;
    private BufferedReader reader;
    private boolean inputRequested;

    private BoundedBodyRequest(HttpServletRequest request, int limit) {
      super(request);
      this.limit = limit;
    }

    private ServletInputStream boundedInput() throws IOException {
      if (input == null) input = new BoundedInput(super.getInputStream(), limit);
      return input;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (reader != null)
        throw new IllegalStateException("Request reader has already been obtained");
      inputRequested = true;
      return boundedInput();
    }

    @Override
    public BufferedReader getReader() throws IOException {
      if (inputRequested)
        throw new IllegalStateException("Request input stream has already been obtained");
      if (reader == null) {
        String charset = getCharacterEncoding();
        reader =
            new BufferedReader(
                charset == null
                    ? new InputStreamReader(boundedInput(), StandardCharsets.ISO_8859_1)
                    : new InputStreamReader(boundedInput(), charset));
      }
      return reader;
    }
  }

  private static final class BoundedInput extends ServletInputStream {
    private final ServletInputStream delegate;
    private final int limit;
    private int consumed;

    private BoundedInput(ServletInputStream delegate, int limit) {
      this.delegate = delegate;
      this.limit = limit;
    }

    @Override
    public int read() throws IOException {
      if (consumed > limit) throw new BodyLimitExceededException();
      int value = delegate.read();
      if (value < 0) return value;
      if (++consumed > limit) throw new BodyLimitExceededException();
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
      if (length == 0) return 0;
      if (consumed > limit) throw new BodyLimitExceededException();
      int read = delegate.read(bytes, offset, Math.min(length, limit - consumed + 1));
      if (read > 0 && (consumed += read) > limit) throw new BodyLimitExceededException();
      return read;
    }

    @Override
    public long skip(long bytes) throws IOException {
      if (bytes <= 0) return 0;
      byte[] buffer = new byte[(int) Math.min(bytes, 8192)];
      long skipped = 0;
      while (skipped < bytes) {
        int read = read(buffer, 0, (int) Math.min(buffer.length, bytes - skipped));
        if (read < 0) break;
        if (read == 0) {
          // Servlet streams used by MVC are blocking. Defensive no-progress handling also keeps
          // skip bounded if a nonstandard delegate returns zero without reaching EOF.
          if (read() < 0) break;
          skipped++;
          continue;
        }
        skipped += read;
      }
      return skipped;
    }

    @Override
    public int available() throws IOException {
      return Math.min(delegate.available(), Math.max(0, limit - consumed));
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
