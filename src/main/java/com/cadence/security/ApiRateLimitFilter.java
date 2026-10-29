package com.cadence.security;

import com.cadence.http.ApiProblems;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Set;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 2)
public final class ApiRateLimitFilter extends OncePerRequestFilter {
  private final ApiRateLimit limits;
  private final ApiRequestTelemetry telemetry;

  public ApiRateLimitFilter(ApiRateLimit limits, ApiRequestTelemetry telemetry) {
    this.limits = limits;
    this.telemetry = telemetry;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof CadencePrincipal actor
        && !limits.allow(
            actor.userId(), Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod()))) {
      telemetry.limited();
      response.setHeader("Retry-After", "60");
      ApiProblems.write(
          response,
          ApiProblems.create(
              429,
              "request_rate_exceeded",
              "Request rate exceeded",
              "Account request rate exceeded; try again later",
              true));
      return;
    }
    chain.doFilter(request, response);
  }
}
