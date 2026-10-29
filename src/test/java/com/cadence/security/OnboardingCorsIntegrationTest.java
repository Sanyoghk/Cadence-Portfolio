package com.cadence.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "cadence.security.cors.allowed-origins=https://creator.example.com")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OnboardingCorsIntegrationTest {
  @Autowired MockMvc mvc;
  @MockitoBean JwtDecoder decoder;

  @Test
  void evidenceIdentityCorsIsExactAndNeverGrantsAuthority() throws Exception {
    String root = "/api/v1/";
    String id = "00000000-0000-0000-0000-000000000001";
    for (String path :
        new String[] {
          "brands/" + id + "/evidence",
          "brands/" + id + "/identities",
          "evidence/" + id + "/document-review",
          "evidence/" + id + "/url-review",
          "evidence/" + id + "/social-review",
          "evidence/" + id + "/ai-admission/history",
          "identities/" + id + "/evidence-provenance",
          "jobs/" + id
        }) {
      mvc.perform(
              options(root + path)
                  .header("Origin", "https://creator.example.com")
                  .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isOk())
          .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
      mvc.perform(
              get(root + path)
                  .header("Origin", "https://creator.example.com")
                  .header("Cookie", "__session=not-bearer"))
          .andExpect(status().isUnauthorized());
    }
    mvc.perform(
            options(root + "identities/" + id)
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk());
    mvc.perform(
            options(root + "brands/" + id + "/analysis-jobs")
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header(
                    "Access-Control-Request-Headers", "authorization,content-type,idempotency-key"))
        .andExpect(status().isOk());
    for (String path : new String[] {"jobs/" + id, "evidence/" + id + "/document-review"})
      mvc.perform(
              options(root + path)
                  .header("Origin", "https://creator.example.com")
                  .header("Access-Control-Request-Method", "PATCH"))
          .andExpect(status().isForbidden());
    mvc.perform(
            options(root + "identities/" + id)
                .header("Origin", "https://evil.example.com")
                .header("Access-Control-Request-Method", "PATCH"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(
            options(root + "identities/" + id)
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "DELETE"))
        .andExpect(status().isForbidden());
    for (String kind : new String[] {"document-review", "url-review", "social-review"})
      mvc.perform(
              options(root + "evidence/" + id + "/" + kind)
                  .header("Origin", "https://creator.example.com")
                  .header("Access-Control-Request-Method", "POST"))
          .andExpect(status().isForbidden());
  }

  @Test
  void exactOriginOwnerCanOnboardButReservedRoleCannot() throws Exception {
    var now = java.time.Instant.now();
    for (String role : new String[] {"OWNER", "EDITOR"}) {
      String token = "cors-" + role;
      org.mockito.Mockito.when(decoder.decode(token))
          .thenReturn(
              new org.springframework.security.oauth2.jwt.Jwt(
                  token,
                  now,
                  now.plusSeconds(300),
                  java.util.Map.of("alg", "RS256"),
                  java.util.Map.of(
                      "sub",
                      "cors-" + role + "-" + java.util.UUID.randomUUID(),
                      "cadence_role",
                      role,
                      "cadence_workspace_ids",
                      java.util.List.of())));
      mvc.perform(
              get("/api/v1/workspaces")
                  .header("Origin", "https://creator.example.com")
                  .header("Authorization", "Bearer " + token))
          .andExpect(status().is(role.equals("OWNER") ? 200 : 403))
          .andExpect(header().string("Access-Control-Allow-Origin", "https://creator.example.com"))
          .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
      mvc.perform(
              post("/api/v1/workspaces")
                  .header("Origin", "https://creator.example.com")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"Creator\",\"industry\":\"Design\"}"))
          .andExpect(status().is(role.equals("OWNER") ? 201 : 403));
    }
  }

  @Test
  void allowsExactPreflightWithoutGrantingAuthentication() throws Exception {
    mvc.perform(
            options("/api/v1/workspaces")
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "https://creator.example.com"))
        .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    mvc.perform(
            get("/api/v1/workspaces")
                .header("Origin", "https://creator.example.com")
                .header("Cookie", "__session=not-a-bearer-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("authentication_required"))
        .andExpect(header().string("Access-Control-Allow-Origin", "https://creator.example.com"));
  }

  @Test
  void rejectsForeignNullSuffixMethodsHeadersAndUnregisteredPaths() throws Exception {
    for (String origin :
        new String[] {
          "https://evil.example.com", "null", "https://creator.example.com.evil.test"
        }) {
      mvc.perform(
              options("/api/v1/workspaces")
                  .header("Origin", origin)
                  .header("Access-Control-Request-Method", "GET"))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
    mvc.perform(
            options("/api/v1/workspaces")
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "DELETE"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/v1/workspaces")
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-admin-role"))
        .andExpect(status().isForbidden());
    mvc.perform(
            options("/api/v1/reports/00000000-0000-0000-0000-000000000001/result")
                .header("Origin", "https://creator.example.com")
                .header("Access-Control-Request-Method", "GET"))
        .andExpect(status().isForbidden());
  }

  @Test
  void nonBrowserRequestsKeepBearerPolicyAndDiscoveryClosed() throws Exception {
    mvc.perform(get("/api/v1/workspaces"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(get("/.well-known/oauth-protected-resource"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"));
  }
}
