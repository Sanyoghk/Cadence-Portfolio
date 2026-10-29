package com.cadence.security;

import com.cadence.http.ApiProblems;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Before authentication: bound concurrent JWT/DB work and assign a server-owned request ID. */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 1)
public final class ApiRequestBoundary extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(ApiRequestBoundary.class);
  private final Semaphore capacity;
  private final ApiRequestTelemetry telemetry;
  private long windowStart = System.nanoTime();
  private int admitted;
  private int logged;

  private synchronized boolean admitRate() {
    resetWindow();
    if (admitted >= 6000) return false;
    admitted++;
    return true;
  }

  private synchronized boolean logRejection() {
    resetWindow();
    if (logged >= 100) return false;
    logged++;
    return true;
  }

  private void resetWindow() {
    long now = System.nanoTime();
    if (now - windowStart >= java.util.concurrent.TimeUnit.MINUTES.toNanos(1)) {
      windowStart = now;
      admitted = 0;
      logged = 0;
    }
  }

  public ApiRequestBoundary(
      @Value("${cadence.http.max-concurrent:16}") int limit, ApiRequestTelemetry telemetry) {
    if (limit < 1 || limit > 32) throw new IllegalArgumentException("Invalid HTTP concurrency");
    capacity = new Semaphore(limit);
    this.telemetry = telemetry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String previous = MDC.get("requestId");
    String id = UUID.randomUUID().toString();
    MDC.put("requestId", id);
    response.setHeader("X-Request-ID", id);
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("X-Frame-Options", "DENY");
    response.setHeader(
        "Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
    response.setHeader("Cache-Control", "no-store");
    boolean acquired = false;
    try {
      // Only exact, side-effect-free liveness bypasses application admission.
      if (request.getMethod().equals("GET") && request.getRequestURI().equals("/api/v1/health")) {
        chain.doFilter(request, response);
      } else if (admitRate() && (acquired = capacity.tryAcquire())) {
        chain.doFilter(request, response);
      } else {
        telemetry.busy();
        response.setHeader("Retry-After", "1");
        ApiProblems.write(response, ApiProblems.status(503));
      }
    } catch (IOException | ServletException | RuntimeException failure) {
      if (!response.isCommitted()) response.setStatus(500);
      throw failure;
    } finally {
      if (acquired) capacity.release();
      int status = response.getStatus();
      telemetry.completed(status);
      // No URL/query/header, principal, exception, body or provider data. IDs are generated above.
      if (status >= 400 && logRejection())
        LOG.info("http_rejected request={} status={}", id, status);
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }
}
