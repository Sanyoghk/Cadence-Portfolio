package com.cadence.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.exceptions.ResourceNotFoundException;
import com.cadence.application.repositories.*;
import com.cadence.application.usecases.GetJobUseCase;
import com.cadence.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobApiIntegrationTest {
  @MockitoBean private JwtDecoder decoder;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;
  @Autowired private WorkspaceRepository workspaces;
  @Autowired private BrandRepository brands;
  @Autowired private BrandEvidenceRepository evidence;
  @Autowired private DurableJobRepository jobs;
  @Autowired private GetJobUseCase getJob;
  private Workspace workspace;
  private Brand brand;
  private BrandEvidence source;
  private DurableJob job;
  private final Instant now = Instant.parse("2026-09-11T00:00:00Z");

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    workspace = workspaces.saveAndFlush(new Workspace("Job studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Job brand", workspace));
    source =
        evidence.saveAndFlush(
            new BrandEvidence(
                brand, EvidenceType.URL, "https://example.com/", "a".repeat(64), now));
    job = saveJob("BRAND_EVIDENCE_INGEST", "evidence:" + source.getId());
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private DurableJob saveJob(String type, String reference) {
    return jobs.saveAndFlush(new DurableJob(type, reference, "test:" + UUID.randomUUID(), 3, now));
  }

  private Authentication actor(UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(UUID.randomUUID(), "job-test", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private Authentication owner() {
    return actor(UserRole.OWNER, Set.of(workspace.getId()));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"OWNER"})
  void membersCanReadJobProgress(UserRole role) throws Exception {
    mvc.perform(
            get("/api/v1/jobs/{id}", job.getId())
                .with(authentication(actor(role, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.id").value(job.getId().toString()))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.attempts").value(0))
        .andExpect(jsonPath("$.maxAttempts").value(3));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"EDITOR", "VIEWER"})
  void reservedRolesCannotReadJobThroughHttpOrUseCase(UserRole role) throws Exception {
    Authentication reserved = actor(role, Set.of(workspace.getId()));
    mvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(authentication(reserved)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    SecurityContextHolder.getContext().setAuthentication(reserved);
    assertThrows(
        AccessDeniedException.class, () -> getJob.execute(job.getId(), Set.of(workspace.getId())));
  }

  @ParameterizedTest
  @EnumSource(JobStatus.class)
  void exposesOnlyPublicFieldsForEveryState(JobStatus state) throws Exception {
    if (state != JobStatus.PENDING) job.claim("internal-worker", now, Duration.ofMinutes(2));
    if (state == JobStatus.SUCCEEDED)
      job.succeed("internal-worker", job.getAttempts(), now.plusSeconds(1));
    if (state == JobStatus.FAILED || state == JobStatus.DEAD) {
      job.fail(
          "internal-worker",
          job.getAttempts(),
          now.plusSeconds(1),
          "internal-secret-marker /private/path",
          new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1)),
          state == JobStatus.DEAD);
    }
    jobs.flush();
    String body =
        mvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(authentication(owner())))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var node = json.readTree(body);
    assertEquals(6, node.size());
    assertEquals(state.name(), node.get("status").asText());
    assertEquals(state == JobStatus.PENDING ? 0 : 1, node.get("attempts").asInt());
    assertEquals(
        state != JobStatus.PENDING && state != JobStatus.FAILED,
        node.get("nextAttemptAt").isNull());
    assertEquals(
        state != JobStatus.SUCCEEDED && state != JobStatus.DEAD, node.get("completedAt").isNull());
    for (String field :
        List.of(
            "payloadReference",
            "idempotencyKey",
            "lastError",
            "leaseOwner",
            "leaseExpiresAt",
            "type")) {
      assertFalse(node.has(field));
    }
    assertFalse(body.contains("internal-secret-marker"));
    assertFalse(body.contains("evidence:"));
  }

  @Test
  void unauthenticatedAndUnknownRolesAreDenied() throws Exception {
    mvc.perform(get("/api/v1/jobs/{id}", job.getId()))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
    Authentication unsupported =
        UsernamePasswordAuthenticationToken.authenticated(
            owner().getPrincipal(), null, List.of(new SimpleGrantedAuthority("ROLE_UNKNOWN")));
    mvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(authentication(unsupported)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
  }

  @Test
  void foreignMissingUnsupportedAndOrphanJobsHaveTheSameNotFoundResponse() throws Exception {
    Authentication outsider = actor(UserRole.OWNER, Set.of(UUID.randomUUID()));
    mvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(authentication(outsider)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Job not found"));
    mvc.perform(
            get("/api/v1/jobs/{id}", job.getId())
                .with(authentication(actor(UserRole.OWNER, Set.of()))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isNotFound());
    List<UUID> hidden =
        List.of(
            UUID.randomUUID(),
            saveJob("FUTURE_JOB", "evidence:" + source.getId()).getId(),
            saveJob("BRAND_EVIDENCE_INGEST", "evidence:not-a-uuid").getId(),
            saveJob("BRAND_EVIDENCE_INGEST", "evidence:" + UUID.randomUUID()).getId());
    for (UUID id : hidden) {
      mvc.perform(get("/api/v1/jobs/{id}", id).with(authentication(owner())))
          .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.detail").value("Job not found"));
    }
  }

  @Test
  void archivePreservesAuthorizedProgressHistory() throws Exception {
    brand.archive();
    workspace.archive();
    brands.flush();
    mvc.perform(get("/api/v1/jobs/{id}", job.getId()).with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk());
  }

  @Test
  void rejectsMalformedIdsAndDoesNotExposeWriteOperations() throws Exception {
    mvc.perform(get("/api/v1/jobs/not-a-uuid").with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/jobs/{id}", job.getId()).with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isMethodNotAllowed());
  }

  @Test
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void registrationReturnsStableFollowableLocation() throws Exception {
    String path = "/api/v1/brands/{id}/evidence";
    String request = "{\"type\":\"TEXT\",\"value\":\"Job source\"}";
    String firstLocation = null;
    for (int attempt = 0; attempt < 2; attempt++) {
      var response =
          mvc.perform(
                  post(path, brand.getId())
                      .with(authentication(owner()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(request))
              .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
              .andExpect(status().isAccepted())
              .andReturn()
              .getResponse();
      String id = json.readTree(response.getContentAsString()).get("jobId").asText();
      String location = response.getHeader("Location");
      assertEquals("/api/v1/jobs/" + id, location);
      if (firstLocation != null) assertEquals(firstLocation, location);
      firstLocation = location;
      mvc.perform(get(location).with(authentication(owner())))
          .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id));
    }
    var file =
        new MockMultipartFile(
            "file",
            "source.pdf",
            "application/pdf",
            "%PDF-1.7".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    var upload =
        mvc.perform(multipart(path, brand.getId()).file(file).with(authentication(owner())))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse();
    assertEquals(
        "/api/v1/jobs/" + json.readTree(upload.getContentAsString()).get("jobId").asText(),
        upload.getHeader("Location"));
    mvc.perform(get(upload.getHeader("Location")).with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isOk());
  }

  @Test
  void methodSecurityRejectsForgedScopeAndDirectUnauthenticatedCalls() {
    assertThrows(
        AuthenticationCredentialsNotFoundException.class,
        () -> getJob.execute(job.getId(), Set.of(workspace.getId())));
    SecurityContextHolder.getContext().setAuthentication(owner());
    assertThrows(
        AccessDeniedException.class, () -> getJob.execute(job.getId(), Set.of(UUID.randomUUID())));
    assertThrows(AccessDeniedException.class, () -> getJob.execute(job.getId(), null));
    assertThrows(ResourceNotFoundException.class, () -> getJob.execute(job.getId(), Set.of()));
    assertEquals(job.getId(), getJob.execute(job.getId(), Set.of(workspace.getId())).getId());
  }
}
