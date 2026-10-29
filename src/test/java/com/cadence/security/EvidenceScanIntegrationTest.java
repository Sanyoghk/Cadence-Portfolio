package com.cadence.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.domain.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvidenceScanIntegrationTest {
  private static final byte[] PDF = "%PDF-1.7 bounded fixture".getBytes(StandardCharsets.UTF_8);
  private static final String SAFE_ERROR = "Evidence file could not be scanned; try again later";
  @Autowired private MockMvc mvc;
  @Autowired private WorkspaceRepository workspaces;
  @Autowired private BrandRepository brands;
  @Autowired private BrandEvidenceRepository evidence;
  @Autowired private DurableJobRepository jobs;
  @MockitoBean private JwtDecoder decoder;
  @MockitoBean private EvidenceMalwareScanner scanner;
  @MockitoBean private EvidenceObjectStorage storage;
  private Workspace workspace;
  private Brand brand;
  private long evidenceBefore;
  private long jobsBefore;

  @BeforeEach
  void setUp() {
    SecurityContextHolder.clearContext();
    workspace = workspaces.saveAndFlush(new Workspace("Scan studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Scan brand", workspace));
    evidenceBefore = evidence.count();
    jobsBefore = jobs.count();
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  private Authentication actor(UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(UUID.randomUUID(), "scan-test", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  private MockMultipartHttpServletRequestBuilder upload() {
    return multipart("/api/v1/brands/{brandId}/evidence", brand.getId())
        .file(new MockMultipartFile("file", "source.pdf", "application/pdf", PDF));
  }

  private Authentication owner() {
    return actor(UserRole.OWNER, Set.of(workspace.getId()));
  }

  private MalwareScanReport report(MalwareScanResult result) {
    return new MalwareScanReport(result, "test-engine", "test-signatures");
  }

  private void assertNoRegistration() {
    verifyNoInteractions(storage);
    assertEquals(evidenceBefore, evidence.count());
    assertEquals(jobsBefore, jobs.count());
  }

  @Test
  void cleanScanRegistersQuarantinedEvidenceAndIdempotentJob() throws Exception {
    when(scanner.scan(any())).thenReturn(report(MalwareScanResult.CLEAN));
    when(storage.storeQuarantined(anyString(), any()))
        .thenAnswer(call -> "quarantine:" + call.getArgument(0));
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(upload().with(authentication(owner())))
          .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
          .andExpect(status().isAccepted())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.evidence.status").value("PENDING"))
          .andExpect(
              jsonPath("$.evidence.sourceReference").value(org.hamcrest.Matchers.nullValue()))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(
                          org.hamcrest.Matchers.containsString("quarantine:"))))
          .andExpect(jsonPath("$.jobStatus").value("PENDING"));
    }
    assertEquals(evidenceBefore + 1, evidence.count());
    assertEquals(jobsBefore + 1, jobs.count());
    assertTrue(
        evidence.findAll().stream()
            .filter(row -> row.getBrand().getId().equals(brand.getId()))
            .findFirst()
            .orElseThrow()
            .getSourceReference()
            .startsWith("quarantine:quarantine/"));
    verify(scanner, times(2)).scan(aryEq(PDF));
    verify(storage).storeQuarantined(anyString(), aryEq(PDF));
  }

  @Test
  void infectedScanRejectsUploadBeforeAnyStorageOrPersistence() throws Exception {
    when(scanner.scan(any())).thenReturn(report(MalwareScanResult.INFECTED));
    mvc.perform(upload().with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Evidence file failed the malware scan"))
        .andExpect(jsonPath("$.code").value("evidence_malware_rejected"));
    assertNoRegistration();
  }

  @Test
  void storageFailureReturnsSafe503WithoutRegistration() throws Exception {
    when(scanner.scan(any())).thenReturn(report(MalwareScanResult.CLEAN));
    when(storage.storeQuarantined(anyString(), any()))
        .thenThrow(new IllegalStateException("secret-provider /internal/path"));
    var response =
        mvc.perform(upload().with(authentication(owner())))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Evidence storage unavailable"))
            .andExpect(
                jsonPath("$.detail").value("Evidence file could not be stored; try again later"))
            .andExpect(header().doesNotExist("Location"))
            .andReturn();
    assertNull(response.getResolvedException().getCause());
    assertFalse(response.getResponse().getContentAsString().contains("secret-provider"));
    assertEquals(evidenceBefore, evidence.count());
    assertEquals(jobsBefore, jobs.count());
  }

  @ParameterizedTest
  @EnumSource(
      value = MalwareScanResult.class,
      names = {"UNAVAILABLE", "ERROR"})
  @NullSource
  void missingOrFailedScanFailsClosed(MalwareScanResult result) throws Exception {
    when(scanner.scan(any())).thenReturn(result == null ? null : report(result));
    mvc.perform(upload().with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Evidence scanning unavailable"))
        .andExpect(jsonPath("$.detail").value(SAFE_ERROR))
        .andExpect(header().doesNotExist("Location"));
    assertNoRegistration();
  }

  @ParameterizedTest
  @ValueSource(strings = {"argument", "state", "unexpected"})
  void adapterExceptionsNeverDiscloseProviderDetails(String kind) throws Exception {
    String sensitive = "scanner.internal /private/source.pdf Authorization=secret-fixture";
    RuntimeException failure =
        switch (kind) {
          case "argument" -> new IllegalArgumentException(sensitive);
          case "state" -> new IllegalStateException(sensitive);
          default -> new RuntimeException(sensitive);
        };
    when(scanner.scan(any())).thenThrow(failure);
    var response =
        mvc.perform(upload().with(authentication(owner())))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value(SAFE_ERROR))
            .andReturn();
    assertFalse(response.getResponse().getContentAsString().contains(sensitive));
    assertNull(response.getResolvedException().getCause());
    assertNoRegistration();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"EDITOR", "VIEWER"})
  void reservedRolesCannotInvokeScanner(UserRole role) throws Exception {
    mvc.perform(upload().with(authentication(actor(role, Set.of(workspace.getId())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    verifyNoInteractions(scanner);
    assertNoRegistration();
  }

  @Test
  void unauthenticatedAndForeignWorkspaceRequestsCannotInvokeScanner() throws Exception {
    mvc.perform(upload())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isUnauthorized());
    mvc.perform(upload().with(authentication(actor(UserRole.OWNER, Set.of(UUID.randomUUID())))))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isForbidden());
    verifyNoInteractions(scanner);
    assertNoRegistration();
  }

  @Test
  void invalidContentIsRejectedBeforeScanning() throws Exception {
    mvc.perform(
            multipart("/api/v1/brands/{brandId}/evidence", brand.getId())
                .file(new MockMultipartFile("file", "fake.png", "image/png", PDF))
                .with(authentication(owner())))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
        .andExpect(status().isBadRequest());
    verifyNoInteractions(scanner);
    assertNoRegistration();
  }
}
