package com.cadence.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import com.cadence.domain.SocialEvidenceProvider;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "cadence.evidence.social.max-workspace-calls-per-hour=2")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SocialEvidenceSecurityIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired EvidenceProviderCallRepository calls;
  @Autowired EvidenceSocialOriginRepository origins;
  @MockitoBean JwtDecoder jwt;
  @MockitoBean ApprovedSocialEvidenceSource source;
  private Workspace workspace;
  private Brand brand;

  @BeforeEach
  void setup() {
    workspace = workspaces.saveAndFlush(new Workspace("Social fixture", "Creator"));
    brand = brands.saveAndFlush(new Brand("Bound creator", workspace));
    when(source.fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM))
        .thenAnswer(
            call ->
                new ApprovedSocialEvidenceSource.Snapshot(
                    SocialEvidenceProvider.INSTAGRAM,
                    "17841400000000001",
                    "v25.0",
                    java.time.Instant.now(),
                    "{\"format\":\"approved-social-v1\",\"provider\":\"INSTAGRAM\",\"posts\":[]}"));
  }

  @Test
  void scopedOwnerCreatesCanonicalTextEvidenceAndDurableJob() throws Exception {
    long originCount = origins.count();
    mvc.perform(request().with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isAccepted())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.evidence.type").value("TEXT"))
        .andExpect(
            jsonPath("$.evidence.sourceReference")
                .value(org.hamcrest.Matchers.containsString("approved-social-v1")));
    verify(source).fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isEqualTo(1);
    assertThat(origins.count()).isEqualTo(originCount + 1);
  }

  @Test
  void unchangedContentAtDifferentRetrievalTimesReturnsSameEvidenceJobAndImmutableOrigin()
      throws Exception {
    var firstTime = java.time.Instant.now();
    String content = "{\"format\":\"approved-social-v1\",\"provider\":\"INSTAGRAM\",\"posts\":[]}";
    when(source.fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM))
        .thenReturn(
            new ApprovedSocialEvidenceSource.Snapshot(
                SocialEvidenceProvider.INSTAGRAM, "17841400000000001", "v25.0", firstTime, content),
            new ApprovedSocialEvidenceSource.Snapshot(
                SocialEvidenceProvider.INSTAGRAM,
                "17841400000000001",
                "v25.0",
                firstTime.plusSeconds(15),
                content));
    long beforeEvidence = evidence.count();
    long beforeOrigin = origins.count();
    var owner = actor(UserRole.OWNER, Set.of(workspace.getId()));
    var first =
        mvc.perform(request().with(authentication(owner)))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse();
    UUID evidenceId =
        UUID.fromString(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first.getContentAsString())
                .path("evidence")
                .path("id")
                .asText());
    var origin = origins.findById(evidenceId).orElseThrow();
    var retry =
        mvc.perform(request().with(authentication(owner)))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse();
    assertThat(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(retry.getContentAsString())
                .path("evidence")
                .path("id")
                .asText())
        .isEqualTo(evidenceId.toString());
    assertThat(retry.getHeader("Location")).isEqualTo(first.getHeader("Location"));
    var unchanged = origins.findById(evidenceId).orElseThrow();
    assertThat(unchanged.getProviderCallId()).isEqualTo(origin.getProviderCallId());
    assertThat(unchanged.getCapturedAt()).isEqualTo(origin.getCapturedAt());
    assertThat(unchanged.getChecksum()).isEqualTo(origin.getChecksum());
    assertThat(evidence.count()).isEqualTo(beforeEvidence + 1);
    assertThat(origins.count()).isEqualTo(beforeOrigin + 1);
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isEqualTo(2);
    mvc.perform(request().with(authentication(owner)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isTooManyRequests());
    verify(source, times(2)).fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
  }

  @Test
  void failedDuplicateRefreshRetainsCommittedQuotaAndOriginalEvidenceOrigin() throws Exception {
    var owner = actor(UserRole.OWNER, Set.of(workspace.getId()));
    var first =
        mvc.perform(request().with(authentication(owner)))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isAccepted())
            .andReturn()
            .getResponse();
    UUID evidenceId =
        UUID.fromString(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(first.getContentAsString())
                .path("evidence")
                .path("id")
                .asText());
    var origin = origins.findById(evidenceId).orElseThrow();
    long beforeEvidence = evidence.count();
    long beforeOrigin = origins.count();
    doThrow(new IllegalStateException("provider refresh unavailable"))
        .when(source)
        .fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    mvc.perform(request().with(authentication(owner)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isServiceUnavailable());
    mvc.perform(request().with(authentication(owner)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isTooManyRequests());
    verify(source, times(2)).fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    assertThat(evidence.count()).isEqualTo(beforeEvidence);
    assertThat(origins.count()).isEqualTo(beforeOrigin);
    var unchanged = origins.findById(evidenceId).orElseThrow();
    assertThat(unchanged.getProviderCallId()).isEqualTo(origin.getProviderCallId());
    assertThat(unchanged.getCapturedAt()).isEqualTo(origin.getCapturedAt());
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isEqualTo(2);
  }

  @Test
  void userSuppliedProviderShapedTextCannotCreateTrustedOrigin() throws Exception {
    long originCount = origins.count();
    String forged =
        "{\"format\":\"approved-social-v1\",\"provider\":\"INSTAGRAM\",\"accountId\":\"17841400000000001\"}";
    String request =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .writeValueAsString(Map.of("type", "TEXT", "value", forged));
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", brand.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(request)
                .with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isAccepted());
    assertThat(origins.count()).isEqualTo(originCount);
    assertNoCalls();
  }

  @Test
  void unauthenticatedRequestRejectedBeforeSourceAndQuota() throws Exception {
    mvc.perform(request())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
    assertNoCalls();
  }

  @Test
  void editorCannotUseProviderCredentials() throws Exception {
    mvc.perform(request().with(authentication(actor(UserRole.EDITOR, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    assertNoCalls();
  }

  @Test
  void foreignWorkspaceOwnerRejectedBeforeExternalIoAndQuota() throws Exception {
    mvc.perform(request().with(authentication(actor(UserRole.OWNER, Set.of(UUID.randomUUID())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    assertNoCalls();
  }

  @Test
  void archivedBrandRejectedBeforeExternalIo() throws Exception {
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(request().with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isConflict());
    // Local binding preflight may precede the locked archive recheck, but no HTTP may follow it.
    verify(source).requireApprovedBinding(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    verifyNoMoreInteractions(source);
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isZero();
  }

  @Test
  void realMissingExpiredUnapprovedBindingsCannotConsumeAnySharedProviderBudget() throws Exception {
    var now = java.time.Instant.now();
    var properties = new com.cadence.infrastructure.ingest.social.SocialEvidenceProperties();
    var binding = new com.cadence.infrastructure.ingest.social.SocialEvidenceProperties.Binding();
    binding.setBrandId(brand.getId());
    binding.setProvider(SocialEvidenceProvider.INSTAGRAM);
    binding.setAccountId("17841400000000001");
    binding.setApiVersion("v25.0");
    binding.setToken("fixture-not-a-production-token");
    binding.setApproved(true);
    binding.setScopes(Set.of("instagram_basic", "pages_read_engagement"));
    binding.setExpiresAt(now.plusSeconds(3600));
    properties.setEnabled(true);
    properties.setBindings(List.of(binding));
    var transport =
        mock(com.cadence.infrastructure.ingest.social.SocialProviderHttpTransport.class);
    var realSource =
        new com.cadence.infrastructure.ingest.social.OfficialSocialEvidenceSource(
            properties, transport, java.time.Clock.fixed(now, java.time.ZoneOffset.UTC));
    doAnswer(
            call -> {
              realSource.requireApprovedBinding(call.getArgument(0), call.getArgument(1));
              return null;
            })
        .when(source)
        .requireApprovedBinding(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    long beforeCalls =
        calls.countByProviderAndRequestedAtGreaterThanEqual(
            SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH);
    long beforeEvidence = evidence.count();
    long beforeOrigins = origins.count();
    var owner = actor(UserRole.OWNER, Set.of(workspace.getId()));
    for (int mode = 0; mode < 5; mode++) {
      binding.setBrandId(brand.getId());
      binding.setExpiresAt(now.plusSeconds(3600));
      binding.setApproved(true);
      binding.setScopes(Set.of("instagram_basic", "pages_read_engagement"));
      properties.setEnabled(true);
      switch (mode) {
        case 0 -> binding.setBrandId(UUID.randomUUID());
        case 1 -> binding.setExpiresAt(now.plusSeconds(5));
        case 2 -> binding.setApproved(false);
        case 3 -> binding.setScopes(Set.of("instagram_basic"));
        case 4 -> properties.setEnabled(false);
        default -> throw new AssertionError("unexpected fixture");
      }
      for (int attempt = 0; attempt < 10; attempt++)
        mvc.perform(request().with(authentication(owner)))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isServiceUnavailable());
    }
    assertThat(
            calls.countByProviderAndRequestedAtGreaterThanEqual(
                SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isEqualTo(beforeCalls);
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isZero();
    assertThat(evidence.count()).isEqualTo(beforeEvidence);
    assertThat(origins.count()).isEqualTo(beforeOrigins);
    verify(source, never()).fetch(any(), any());
    verifyNoInteractions(transport);
    properties.setEnabled(true);
    binding.setScopes(Set.of("instagram_basic", "pages_read_engagement"));
    mvc.perform(request().with(authentication(owner)))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isAccepted());
    assertThat(
            calls.countByProviderAndRequestedAtGreaterThanEqual(
                SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isEqualTo(beforeCalls + 1);
  }

  @Test
  void providerFailureAnd429ConsumeCommittedAttemptWithoutPublishingOrLeaking() throws Exception {
    long before = evidence.count();
    doThrow(new IllegalStateException("access_token=secret-provider.internal"))
        .when(source)
        .fetch(any(), any());
    var response =
        mvc.perform(
                request().with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isServiceUnavailable())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(response).doesNotContain("access_token", "secret-provider", "internal");
    doThrow(new EvidenceQuotaExceededException()).when(source).fetch(any(), any());
    mvc.perform(request().with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isTooManyRequests());
    mvc.perform(request().with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isTooManyRequests());
    verify(source, times(2)).fetch(brand.getId(), SocialEvidenceProvider.INSTAGRAM);
    assertThat(evidence.count()).isEqualTo(before);
  }

  @Test
  void unknownProviderCannotSelectArbitraryEndpoint() throws Exception {
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence/social", brand.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"http://169.254.169.254\"}")
                .with(authentication(actor(UserRole.OWNER, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
    assertNoCalls();
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request() {
    return post("/api/v1/brands/{brandId}/evidence/social", brand.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"provider\":\"INSTAGRAM\"}");
  }

  private Authentication actor(UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(UUID.randomUUID(), "social-fixture", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private void assertNoCalls() {
    verifyNoInteractions(source);
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, java.time.Instant.EPOCH))
        .isZero();
  }
}
