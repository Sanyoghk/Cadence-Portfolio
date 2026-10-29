package com.cadence.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** Public HTTP errors contain controlled descriptions, never exception messages or input values. */
public final class ApiProblems {
  private static final ObjectMapper JSON = new ObjectMapper();

  private ApiProblems() {}

  public record Violation(String field, String code) {}

  public static ProblemDetail create(
      int status, String code, String title, String detail, boolean retryable) {
    var problem = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(status), detail);
    // Spring 7 leaves type unset; keep the explicit URI required by our existing API contract.
    problem.setType(URI.create("about:blank"));
    problem.setTitle(title);
    problem.setInstance(URI.create("urn:uuid:" + UUID.randomUUID()));
    problem.setProperty("code", code);
    problem.setProperty("retryable", retryable);
    return problem;
  }

  public static ProblemDetail status(int status) {
    return switch (status) {
      case 400 ->
          create(
              400,
              "invalid_request",
              "Invalid request",
              "Request parameters or content are invalid",
              false);
      case 401 ->
          create(
              401,
              "authentication_required",
              "Authentication required",
              "A valid Bearer token is required",
              false);
      case 403 ->
          create(
              403,
              "access_denied",
              "Access denied",
              "You do not have permission to perform this operation",
              false);
      case 404 ->
          create(404, "resource_not_found", "Resource not found", "Resource not found", false);
      case 405 ->
          create(
              405,
              "method_not_allowed",
              "Method not allowed",
              "This HTTP method is not supported for this resource",
              false);
      case 406 ->
          create(
              406,
              "not_acceptable",
              "Response format unavailable",
              "The requested response format is not supported",
              false);
      case 409 ->
          create(
              409,
              "resource_conflict",
              "Resource conflict",
              "Resource state or version conflicts with this operation",
              false);
      case 413 ->
          create(
              413,
              "body_too_large",
              "Request body too large",
              "Request body exceeds configured limit",
              false);
      case 415 ->
          create(
              415,
              "unsupported_media_type",
              "Unsupported media type",
              "The request media type is not supported",
              false);
      case 429 ->
          create(
              429,
              "evidence_quota_exceeded",
              "Evidence quota exceeded",
              "Workspace evidence quota exceeded",
              true);
      case 503 ->
          create(
              503,
              "service_unavailable",
              "Service unavailable",
              "The operation is temporarily unavailable; try again later",
              true);
      default ->
          create(
              500,
              "internal_error",
              "Internal server error",
              "The request could not be completed",
              false);
    };
  }

  public static ProblemDetail validation(List<Violation> errors) {
    var problem =
        create(400, "validation_failed", "Invalid request", "Request validation failed", false);
    problem.setProperty("errors", List.copyOf(errors));
    return problem;
  }

  /** Filters and MVC use the same flat RFC 9457 representation. */
  public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType("application/problem+json");
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setHeader("Cache-Control", "no-store");
    var body = new LinkedHashMap<String, Object>();
    body.put("type", problem.getType());
    body.put("title", problem.getTitle());
    body.put("status", problem.getStatus());
    body.put("detail", problem.getDetail());
    body.put("instance", problem.getInstance());
    body.putAll(problem.getProperties());
    response.getWriter().write(JSON.writeValueAsString(body));
  }
}
