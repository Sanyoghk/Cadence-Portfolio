package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.usecases.RegisterBrandEvidenceUseCase;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public abstract class EvidenceRegistrationQuotaContract {
  @Autowired protected RegisterBrandEvidenceUseCase registration;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected DurableJobRepository jobs;
  @Autowired protected PlatformTransactionManager manager;
  @Autowired protected Clock clock;
  @Autowired protected MockMvc mvc;
  @MockitoBean protected EvidenceObjectStorage storage;
  @MockitoBean protected EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;

  @BeforeEach
  void prepareRegistrationQuota() {
    workspace = workspaces.saveAndFlush(new Workspace("Quota studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Quota brand", workspace));
    authenticate(workspace.getId());
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  protected Authentication owner(UUID workspaceId) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(
            UUID.randomUUID(), "registration-quota-test", UserRole.OWNER, Set.of(workspaceId)),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
  }

  protected void authenticate(UUID workspaceId) {
    SecurityContextHolder.getContext().setAuthentication(owner(workspaceId));
  }

  protected RegisterBrandEvidenceUseCase.Registration register(
      Brand target, EvidenceType type, String value) {
    return registration.register(target.getId(), Set.of(workspace.getId()), type, value);
  }

  @ParameterizedTest
  @EnumSource(
      value = EvidenceType.class,
      names = {"TEXT", "URL"})
  void textAndPendingUrlsConsumeTheSameWorkspaceHourlyBudget(EvidenceType firstType) {
    String firstValue = firstType == EvidenceType.URL ? "https://example.com/source" : "First text";
    register(brand, firstType, firstValue);
    long beforeJobs = jobs.count();
    EvidenceType secondType = firstType == EvidenceType.URL ? EvidenceType.TEXT : EvidenceType.URL;
    String secondValue =
        secondType == EvidenceType.URL ? "https://example.com/another" : "Second text";
    assertThatThrownBy(() -> register(brand, secondType, secondValue))
        .isInstanceOf(EvidenceQuotaExceededException.class);
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(1);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void concurrentRequestsAcrossBrandsCannotOversubscribeTheWorkspace() throws Exception {
    Brand second = brands.saveAndFlush(new Brand("Second quota brand", workspace));
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    long beforeJobs = jobs.count();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> concurrentRegistration(brand, "A", ready, start));
      var b = executor.submit(() -> concurrentRegistration(second, "B", ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(1);
    assertThat(jobs.count()).isEqualTo(beforeJobs + 1);
  }

  private boolean concurrentRegistration(
      Brand target, String text, CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    authenticate(workspace.getId());
    try {
      ready.countDown();
      if (!start.await(10, TimeUnit.SECONDS))
        throw new AssertionError("Registration barrier timed out");
      try {
        register(target, EvidenceType.TEXT, text);
        return true;
      } catch (EvidenceQuotaExceededException expected) {
        return false;
      }
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void duplicateAtBothRowAndHourlyCapsReturnsTheOriginalEvidenceAndJob() {
    var first = register(brand, EvidenceType.TEXT, "Original text");
    addHistoricalText("Historical text");
    long beforeJobs = jobs.count();
    var duplicate = register(brand, EvidenceType.TEXT, "  Original text  ");
    assertThat(duplicate.evidence().getId()).isEqualTo(first.evidence().getId());
    assertThat(duplicate.job().getId()).isEqualTo(first.job().getId());
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(2);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
  }

  @Test
  void historicalRowsStillConsumeTotalCapacityWhenTheHourlyWindowIsEmpty() {
    addHistoricalText("Historical A");
    addHistoricalText("Historical B");
    assertThat(
            evidence.countRecentInWorkspace(
                workspace.getId(), clock.instant().minus(Duration.ofHours(1))))
        .isZero();
    long beforeJobs = jobs.count();
    assertThatThrownBy(() -> register(brand, EvidenceType.URL, "https://example.com/new"))
        .isInstanceOf(EvidenceQuotaExceededException.class);
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(2);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
  }

  @Test
  void apiQuotaRejectionReturns429WithoutCreatingEvidenceOrJobs() throws Exception {
    register(brand, EvidenceType.TEXT, "Full hourly window");
    long beforeJobs = jobs.count();
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", brand.getId())
                .with(authentication(owner(workspace.getId())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"URL\",\"value\":\"https://example.com/new\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").value("Workspace evidence quota exceeded"))
        .andExpect(header().doesNotExist("Location"));
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(1);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void quotaIsTenantIsolatedAndForeignWorkspaceRequestsRemainRejected() {
    register(brand, EvidenceType.TEXT, "Full first workspace");
    Workspace other = workspaces.saveAndFlush(new Workspace("Other quota studio", "Creator"));
    Brand otherBrand = brands.saveAndFlush(new Brand("Other quota brand", other));
    assertThatThrownBy(() -> register(otherBrand, EvidenceType.TEXT, "Foreign workspace"))
        .isInstanceOf(ResourceNotFoundException.class);
    authenticate(other.getId());
    assertThat(
            registration.register(
                otherBrand.getId(), Set.of(other.getId()), EvidenceType.TEXT, "Own workspace"))
        .isNotNull();
    assertThat(evidence.countInWorkspace(other.getId())).isEqualTo(1);
  }

  @Test
  void archivedWorkspaceCannotRegisterAnAdditionalRow() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> workspaces.findById(workspace.getId()).orElseThrow().archive());
    assertThatThrownBy(() -> register(brand, EvidenceType.TEXT, "Archived workspace"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(evidence.countInWorkspace(workspace.getId())).isZero();
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void unauthenticatedRequestIsRejectedBeforeQuotaStateCanBeExposed() throws Exception {
    register(brand, EvidenceType.TEXT, "Full hourly window");
    long beforeJobs = jobs.count();
    mvc.perform(
            post("/api/v1/brands/{brandId}/evidence", brand.getId())
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"TEXT\",\"value\":\"Anonymous request\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(evidence.countInWorkspace(workspace.getId())).isEqualTo(1);
    assertThat(jobs.count()).isEqualTo(beforeJobs);
  }

  private void addHistoricalText(String text) {
    try {
      String checksum =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(("TEXT\n" + text).getBytes(StandardCharsets.UTF_8)));
      evidence.saveAndFlush(
          new BrandEvidence(
              brand,
              EvidenceType.TEXT,
              text,
              checksum,
              clock.instant().minus(Duration.ofHours(2))));
    } catch (java.security.NoSuchAlgorithmException failure) {
      throw new IllegalStateException(failure);
    }
  }
}
