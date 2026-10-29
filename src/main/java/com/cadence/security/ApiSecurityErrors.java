package com.cadence.security;

import com.cadence.http.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

/** Fixed RFC 6750 challenges never reflect decoder diagnostics or request-derived metadata URLs. */
public final class ApiSecurityErrors
    implements AuthenticationEntryPoint, AccessDeniedHandler, RequestRejectedHandler {
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    int status = 401;
    String challenge = "Bearer";
    if (exception instanceof OAuth2AuthenticationException oauth) {
      boolean invalidRequest = "invalid_request".equals(oauth.getError().getErrorCode());
      status = invalidRequest ? 400 : 401;
      challenge =
          invalidRequest ? "Bearer error=\"invalid_request\"" : "Bearer error=\"invalid_token\"";
    }
    // No protected-resource discovery endpoint is part of Cadence's reviewed API contract.
    response.setHeader("WWW-Authenticate", challenge);
    ApiProblems.write(response, ApiProblems.status(status));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
      throws IOException {
    ApiProblems.write(response, ApiProblems.status(403));
  }

  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, RequestRejectedException exception)
      throws IOException {
    ApiProblems.write(response, ApiProblems.status(400));
  }
}
