package com.cadence.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.web.firewall.RequestRejectedException;

class ApiSecurityErrorsTest {
  private final ApiSecurityErrors handler = new ApiSecurityErrors();
  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @Test
  void missingTokenHasBearerChallengeAndCommon401() throws Exception {
    var response = new MockHttpServletResponse();
    handler.commence(request, response, new InsufficientAuthenticationException("secret-token"));
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    check(response, 401, "authentication_required");
  }

  @Test
  void invalidTokenAndMalformedBearerDoNotLeakDiagnosticsInChallengeOrBody() throws Exception {
    for (String code : new String[] {"invalid_token", "invalid_request"}) {
      var response = new MockHttpServletResponse();
      handler.commence(
          request,
          response,
          new OAuth2AuthenticationException(
              new BearerTokenError(
                  code,
                  code.equals("invalid_token") ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST,
                  "secret-token issuer.internal",
                  "https://issuer.internal/secret-token")));
      assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"" + code + "\"");
      check(
          response,
          code.equals("invalid_token") ? 401 : 400,
          code.equals("invalid_token") ? "authentication_required" : "invalid_request");
    }
  }

  @Test
  void challengeNeverReflectsUntrustedRequestHostOrMetadataLocation() throws Exception {
    request.setServerName("attacker.example");
    request.setContextPath("/untrusted");
    request.addHeader("Forwarded", "host=internal.example;proto=http");
    var response = new MockHttpServletResponse();
    handler.commence(request, response, new InsufficientAuthenticationException("secret-token"));
    assertThat(response.getHeaders("WWW-Authenticate")).containsExactly("Bearer");
    assertThat(response.getContentAsString())
        .doesNotContain("attacker.example", "internal.example", "untrusted", "resource_metadata");
    check(response, 401, "authentication_required");
  }

  @Test
  void denialAndFirewallRejectionHaveCommonSanitizedErrors() throws Exception {
    var denied = new MockHttpServletResponse();
    handler.handle(request, denied, new AccessDeniedException("secret-token"));
    check(denied, 403, "access_denied");
    var rejected = new MockHttpServletResponse();
    handler.handle(request, rejected, new RequestRejectedException("secret-token"));
    check(rejected, 400, "invalid_request");
  }

  private void check(MockHttpServletResponse response, int status, String code) throws Exception {
    assertThat(response.getStatus()).isEqualTo(status);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    var body = new ObjectMapper().readTree(response.getContentAsString());
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("code").asText()).isEqualTo(code);
    assertThat(body.path("instance").asText()).startsWith("urn:uuid:");
    assertThat(response.getContentAsString()).doesNotContain("secret-token", "issuer.internal");
  }
}
