package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.domain.UserRole;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "cadence.http.reads-per-minute=2",
      "cadence.http.writes-per-minute=1",
      "spring.jmx.enabled=true"
    })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendObservabilityIntegrationTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;
  @Autowired javax.management.MBeanServer metricsServer;

  @Test
  void explicitLocalJmxExportsOnlyFixedApiCountersAndNoPublicMetricsRoute() throws Exception {
    var name = new javax.management.ObjectName("com.cadence:type=ApiRequestMetrics");
    assertThat(metricsServer.isRegistered(name)).isTrue();
    assertThat(
            metricsServer.isRegistered(
                new javax.management.ObjectName("com.cadence:type=AiGenerationMetrics")))
        .isTrue();
    assertThat(
            java.util.Arrays.stream(metricsServer.getMBeanInfo(name).getAttributes())
                .map(javax.management.MBeanAttributeInfo::getName))
        .containsExactlyInAnyOrder(
            "Successes",
            "Redirects",
            "ClientErrors",
            "ServerErrors",
            "RateLimited",
            "CapacityRejected");
    long before = (Long) metricsServer.getAttribute(name, "Successes");
    mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    assertThat(metricsServer.getAttribute(name, "Successes")).isEqualTo(before + 1);
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
  }

  @Test
  void healthAndCoreReadinessArePublicButProbeSubpathsAndMethodsStayProtected() throws Exception {
    mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/health/ready"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"READY\"}"));
    verifyNoInteractions(decoder);
    for (var path :
        List.of(
            "/api/v1/health/details",
            "/actuator/env",
            "/actuator/metrics",
            "/api/v1/security-audit")) mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/health/ready")).andExpect(status().isUnauthorized());
  }

  @Test
  void anonymousFailureHasFixedSecurityHeadersAndNewCorrelation() throws Exception {
    var r =
        mvc.perform(get("/api/v1/workspaces").header("X-Request-ID", "forged"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(
                header()
                    .string(
                        "Content-Security-Policy",
                        "default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    UUID.fromString(r.getResponse().getHeader("X-Request-ID"));
  }

  @Test
  void rateCannotBeBypassedBySessionOrTenantSwitchAndDoesNotAffectOtherAccounts() throws Exception {
    var id = UUID.randomUUID();
    var owner =
        UsernamePasswordAuthenticationToken.authenticated(
            new CadencePrincipal(id, "not-logged", UserRole.OWNER, Set.of()),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    for (int i = 0; i < 2; i++)
      mvc.perform(get("/api/v1/workspaces").with(authentication(owner))).andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/workspaces")
                .with(authentication(owner))
                .header("X-Forwarded-For", "another-client")
                .header("X-Request-ID", "different"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(jsonPath("$.code").value("request_rate_exceeded"));
    var other =
        UsernamePasswordAuthenticationToken.authenticated(
            new CadencePrincipal(UUID.randomUUID(), "other", UserRole.OWNER, Set.of()),
            null,
            owner.getAuthorities());
    mvc.perform(get("/api/v1/workspaces").with(authentication(other))).andExpect(status().isOk());
  }
}
