package com.cadence.security;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cadence.application.repositories.BrandEvidenceRepository;
import com.cadence.application.repositories.BrandRepository;
import com.cadence.application.repositories.CadenceUserRepository;
import com.cadence.application.repositories.WorkspaceRepository;
import com.cadence.domain.Brand;
import com.cadence.domain.CadenceUser;
import com.cadence.domain.UserRole;
import com.cadence.domain.Workspace;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {
  @Test
  void defaultConfigurationDeniesCrossOriginWithoutGrantingCookieAuthentication() throws Exception {
    mvc.perform(get("/api/v1/workspaces").header("Origin", "https://unconfigured.example.com"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    mvc.perform(get("/api/v1/workspaces").header("Cookie", "__session=not-bearer"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsOversizedWorkspaceBootstrapClaimBeforeProvisioning() throws Exception {
    var ids =
        java.util.stream.IntStream.range(0, 11)
            .mapToObj(ignored -> java.util.UUID.randomUUID().toString())
            .toList();
    when(jwtDecoder.decode("overassigned"))
        .thenReturn(
            new Jwt(
                "overassigned",
                NOW,
                NOW.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                    "sub", "overassigned", "cadence_role", "OWNER", "cadence_workspace_ids", ids)));
    mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer overassigned"))
        .andExpect(status().isUnauthorized());
    org.assertj.core.api.Assertions.assertThat(users.findByExternalSubject("overassigned"))
        .isEmpty();
  }

  private static final Instant NOW = Instant.parse("2026-09-03T12:00:00Z");

  @Autowired private MockMvc mvc;
  @Autowired private CadenceUserRepository users;
  @Autowired private WorkspaceRepository workspaces;
  @Autowired private BrandRepository brands;
  @Autowired private BrandEvidenceRepository evidence;
  @MockitoBean private JwtDecoder jwtDecoder;

  private Workspace firstWorkspace;
  private Workspace secondWorkspace;
  private Brand firstBrand;

  @Test
  void allDocumentedProtectedOperationsRejectMissingBearerWithTheSameContract() throws Exception {
    var paths = com.cadence.presentation.OpenApiContractAssertions.DOCUMENT.path("paths").fields();
    while (paths.hasNext()) {
      var path = paths.next();
      if (Set.of("/api/v1/health", "/api/v1/health/ready").contains(path.getKey())) continue;
      String uri =
          path.getKey()
              .replace("{workspaceId}", firstWorkspace.getId().toString())
              .replace("{brandId}", firstBrand.getId().toString())
              .replaceAll("\\{[^}]+}", java.util.UUID.randomUUID().toString());
      var methods = path.getValue().fieldNames();
      while (methods.hasNext()) {
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                    org.springframework.http.HttpMethod.valueOf(
                        methods.next().toUpperCase(java.util.Locale.ROOT)),
                    uri))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("authentication_required"))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
      }
    }
  }

  @Test
  void paginationRejectsDeepOffsetsAndEqualNamesDoNotRepeatAcrossAdjacentPages() throws Exception {
    allowToken("page-owner", "page-owner", "OWNER", firstWorkspace);
    brands.saveAndFlush(new Brand(firstBrand.getName(), firstWorkspace));
    brands.saveAndFlush(new Brand(firstBrand.getName(), firstWorkspace));
    var ids = new java.util.ArrayList<String>();
    for (int page = 0; page < 3; page++) {
      var response =
          mvc.perform(
                  get("/api/v1/workspaces/{id}/brands", firstWorkspace.getId())
                      .header("Authorization", "Bearer page-owner")
                      .param("size", "1")
                      .param("page", Integer.toString(page)))
              .andExpect(status().isOk())
              .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
              .andReturn()
              .getResponse();
      ids.add(
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readTree(response.getContentAsString())
              .at("/content/0/id")
              .asText());
    }
    org.assertj.core.api.Assertions.assertThat(ids).doesNotHaveDuplicates().isSorted();
    for (String page : List.of("10001", "2147483647", "-1", "bad-input")) {
      mvc.perform(
              get("/api/v1/workspaces")
                  .header("Authorization", "Bearer page-owner")
                  .param("page", page))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("invalid_request"))
          .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    }
  }

  @Test
  void unknownInputFieldsCannotAssignInternalState() throws Exception {
    allowToken("create-owner", "create-owner", "OWNER", firstWorkspace);
    mvc.perform(
            post("/api/v1/workspaces")
                .header("Authorization", "Bearer create-owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Created\",\"industry\":\"Creator\",\"status\":\"ARCHIVED\",\"lockVersion\":999,\"role\":\"ADMIN\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.lockVersion").value(0))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @BeforeEach
  void setUp() {
    users.deleteAll();
    evidence.deleteAll();
    brands.deleteAll();
    workspaces.deleteAll();
    firstWorkspace = workspaces.save(new Workspace("Alpha Studio", "Creator"));
    secondWorkspace = workspaces.save(new Workspace("Beta Studio", "Creator"));
    firstBrand = brands.save(new Brand("Alpha Brand", firstWorkspace));
  }

  @Test
  void migrationDoesNotExposeDiscoveryOrReflectHostInBearerChallenge() throws Exception {
    mvc.perform(
            get("/.well-known/oauth-protected-resource")
                .header("Host", "attacker.example")
                .header("Forwarded", "host=internal.example;proto=http"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", "Bearer"))
        .andExpect(jsonPath("$.type").value("about:blank"));
    mvc.perform(get("/.well-known/oauth-protected-resource").with(jwt()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldKeepHealthPublicAndProtectBusinessEndpoints() throws Exception {
    mvc.perform(get("/api/v1/health"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/workspaces"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldReturnUnauthorizedForInvalidAndExpiredTokens() throws Exception {
    when(jwtDecoder.decode("invalid")).thenThrow(validationFailure("Invalid signature"));
    when(jwtDecoder.decode("expired")).thenThrow(validationFailure("Expired token"));

    mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer invalid"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer expired"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldScopeCreatorToTheirWorkspaces() throws Exception {
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);

    mvc.perform(get("/api/v1/workspaces").header("Authorization", "Bearer owner-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)))
        .andExpect(jsonPath("$.content[0].id").value(firstWorkspace.getId().toString()))
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            get("/api/v1/workspaces/{workspaceId}", secondWorkspace.getId())
                .header("Authorization", "Bearer owner-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowOwnerToCreateWorkspaceAndUseItOnTheNextRequest() throws Exception {
    allowToken("new-owner", "creator-new", "OWNER");

    String response =
        mvc.perform(
                post("/api/v1/workspaces")
                    .header("Authorization", "Bearer new-owner")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"My Studio\",\"industry\":\"Creator\"}"))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String workspaceId =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

    mvc.perform(
            get("/api/v1/workspaces/{workspaceId}", workspaceId)
                .header("Authorization", "Bearer new-owner"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk());
  }

  @Test
  void shouldEnforceCollaborationRolesAndWorkspaceOwnership() throws Exception {
    users.save(new CadenceUser("viewer-1", UserRole.VIEWER, List.of(firstWorkspace)));
    allowToken("viewer-token", "viewer-1", "VIEWER", firstWorkspace);
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);

    mvc.perform(
            post("/api/v1/workspaces/{workspaceId}/brands", firstWorkspace.getId())
                .header("Authorization", "Bearer viewer-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Brand\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/workspaces/{workspaceId}/brands", secondWorkspace.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Brand\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/cadence/brands/{brandId}/analyze", firstBrand.getId())
                .header("Authorization", "Bearer viewer-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldUpdateListAndArchiveWorkspaceAndBrand() throws Exception {
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);

    mvc.perform(
            patch("/api/v1/workspaces/{workspaceId}", firstWorkspace.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"expectedVersion\":0,\"name\":\"Updated Studio\",\"industry\":\"Retail\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Studio"))
        .andExpect(jsonPath("$.lockVersion").value(1));

    mvc.perform(
            get("/api/v1/workspaces/{workspaceId}/brands", firstWorkspace.getId())
                .header("Authorization", "Bearer owner-token")
                .queryParam("size", "10"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(1)));

    mvc.perform(
            patch("/api/v1/brands/{brandId}", firstBrand.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"expectedVersion\":0,\"name\":\"Updated Brand\",\"websiteUrl\":\"https://brand.test\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Updated Brand"));

    mvc.perform(
            post("/api/v1/brands/{brandId}/archive", firstBrand.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ARCHIVED"));
  }

  @Test
  void shouldRejectInvalidPaginationAndStaleUpdate() throws Exception {
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);

    mvc.perform(
            get("/api/v1/workspaces")
                .header("Authorization", "Bearer owner-token")
                .queryParam("size", "101"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch("/api/v1/workspaces/{workspaceId}", firstWorkspace.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"expectedVersion\":5,\"name\":\"Updated Studio\",\"industry\":\"Retail\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource conflict"));
  }

  @Test
  void shouldRegisterIdempotentEvidenceAndRejectUnsafeSources() throws Exception {
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);

    String body = "{\"type\":\"URL\",\"value\":\"https://Example.com/about#team\"}";
    String firstResponse =
        mvc.perform(
                post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                    .header("Authorization", "Bearer owner-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.evidence.type").value("URL"))
            .andExpect(jsonPath("$.evidence.sourceReference").value("https://example.com/about"))
            .andExpect(jsonPath("$.jobStatus").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstEvidenceId =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(firstResponse)
            .path("evidence")
            .path("id")
            .asText();

    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.evidence.id").value(firstEvidenceId));

    mvc.perform(
            get("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer owner-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));

    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer owner-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"http://127.0.0.1/admin\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldQuarantineValidatedFilesAndRejectContentTypeSpoofing() throws Exception {
    allowToken("owner-token", "creator-1", "OWNER", firstWorkspace);
    MockMultipartFile png =
        new MockMultipartFile(
            "file",
            "../logo.png",
            "image/png",
            new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1});

    mvc.perform(
            multipart("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .file(png)
                .header("Authorization", "Bearer owner-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.evidence.type").value("FILE"))
        .andExpect(jsonPath("$.evidence.mediaType").value("image/png"))
        .andExpect(jsonPath("$.evidence.originalFileName").value("logo.png"))
        .andExpect(jsonPath("$.evidence.sourceReference").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(
                    org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("quarantine:"))));

    MockMultipartFile spoofed =
        new MockMultipartFile(
            "file",
            "fake.png",
            "image/png",
            "%PDF-1.7 fake".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(
            multipart("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .file(spoofed)
                .header("Authorization", "Bearer owner-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
  }

  @Test
  void shouldDenyCrossWorkspaceEvidenceRegistrationAndListing() throws Exception {
    allowToken("other-owner", "other-creator", "OWNER", secondWorkspace);
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer other-owner")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"https://example.com/\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    mvc.perform(
            get("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer other-owner"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"https://example.com/\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldAuthorizeAnalysisAndPreserveResponseContract() throws Exception {
    String path = "/api/v1/cadence/brands/{brandId}/analyze";
    allowToken("owner-analysis", "analysis-owner", "OWNER", firstWorkspace);
    allowToken("other-analysis", "other-owner", "OWNER", secondWorkspace);
    allowToken("editor-analysis", "analysis-editor", "EDITOR", firstWorkspace);
    mvc.perform(post(path, firstBrand.getId()))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
    mvc.perform(post(path, firstBrand.getId()).header("Authorization", "Bearer other-analysis"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    mvc.perform(post(path, firstBrand.getId()).header("Authorization", "Bearer owner-analysis"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sampleCaption").value("Example brand caption"))
        .andExpect(jsonPath("$.fonts[0]").value("Inter"));
    mvc.perform(post(path, firstBrand.getId()).header("Authorization", "Bearer editor-analysis"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    firstWorkspace.archive();
    workspaces.saveAndFlush(firstWorkspace);
    mvc.perform(post(path, firstBrand.getId()).header("Authorization", "Bearer owner-analysis"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", firstBrand.getId())
                .header("Authorization", "Bearer owner-analysis")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TEXT\",\"value\":\"New evidence\"}"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isConflict());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"EDITOR", "VIEWER"})
  void reservedRolesCannotReadAssignedResourcesEvenWithOwnerClaim(UserRole role) throws Exception {
    users.save(new CadenceUser("reserved-user", role, List.of(firstWorkspace)));
    allowToken("reserved-token", "reserved-user", "OWNER", firstWorkspace);
    for (String path :
        List.of(
            "/api/v1/workspaces",
            "/api/v1/workspaces/" + firstWorkspace.getId(),
            "/api/v1/workspaces/" + firstWorkspace.getId() + "/brands",
            "/api/v1/brands/" + firstBrand.getId(),
            "/api/v1/brands/" + firstBrand.getId() + "/evidence")) {
      mvc.perform(get(path).header("Authorization", "Bearer reserved-token"))
          .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/v1/cadence/brands/{brandId}/analyze", firstBrand.getId())
                .header("Authorization", "Bearer reserved-token"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
  }

  private void allowToken(
      String tokenValue, String subject, String role, Workspace... assignedWorkspaces) {
    List<String> workspaceIds =
        java.util.Arrays.stream(assignedWorkspaces)
            .map(workspace -> workspace.getId().toString())
            .toList();
    Jwt jwt =
        new Jwt(
            tokenValue,
            NOW,
            NOW.plusSeconds(300),
            Map.of("alg", "RS256"),
            Map.of(
                "sub", subject,
                "cadence_role", role,
                "cadence_workspace_ids", workspaceIds));
    when(jwtDecoder.decode(tokenValue)).thenReturn(jwt);
  }

  private JwtValidationException validationFailure(String message) {
    OAuth2Error error = new OAuth2Error("invalid_token", message, null);
    return new JwtValidationException(message, List.of(error));
  }
}
