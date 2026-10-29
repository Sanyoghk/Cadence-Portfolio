package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.exceptions.SocialEvidenceUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.EvidenceProviderQuota;
import com.cadence.application.usecases.RegisterSocialBrandEvidenceUseCase;
import com.cadence.domain.*;
import com.cadence.persistence.PostgreSqlTestDatabase;
import com.cadence.security.CadencePrincipal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "cadence.evidence.social.max-provider-calls-per-hour=1",
      "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
    })
@ActiveProfiles("postgres-test")
class PostgreSqlSocialProviderCapacityTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    properties.add("spring.datasource.username", POSTGRES::getUsername);
    properties.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired EvidenceProviderCallRepository calls;
  @Autowired EvidenceProviderQuota quota;
  @Autowired RegisterSocialBrandEvidenceUseCase registration;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean EvidenceObjectStorage storage;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean ApprovedSocialEvidenceSource source;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void competingWorkspacesCannotOversubscribeProviderWideCostBudget() throws Exception {
    Workspace first = workspaces.saveAndFlush(new Workspace("Provider workspace one", "Owner"));
    Workspace second = workspaces.saveAndFlush(new Workspace("Provider workspace two", "Owner"));
    Brand firstBrand = brands.saveAndFlush(new Brand("First", first));
    Brand secondBrand = brands.saveAndFlush(new Brand("Second", second));
    Brand unapproved = brands.saveAndFlush(new Brand("No approved binding", first));
    doThrow(new SocialEvidenceUnavailableException())
        .when(source)
        .requireApprovedBinding(unapproved.getId(), SocialEvidenceProvider.INSTAGRAM);
    authenticate(first.getId());
    for (int attempt = 0; attempt < 50; attempt++) {
      assertThatThrownBy(
              () ->
                  quota.reserve(
                      unapproved.getId(), Set.of(first.getId()), SocialEvidenceProvider.INSTAGRAM))
          .isInstanceOf(SocialEvidenceUnavailableException.class);
    }
    SecurityContextHolder.clearContext();
    assertThat(
            calls.countByProviderAndRequestedAtGreaterThanEqual(
                SocialEvidenceProvider.INSTAGRAM, Instant.EPOCH))
        .isZero();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> accepted = new ArrayList<>();
      for (Brand brand : List.of(firstBrand, secondBrand))
        accepted.add(
            executor.submit(
                () -> {
                  UUID workspaceId = brand.getWorkspace().getId();
                  authenticate(workspaceId);
                  ready.countDown();
                  if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("test timeout");
                  try {
                    quota.reserve(
                        brand.getId(), Set.of(workspaceId), SocialEvidenceProvider.INSTAGRAM);
                    return true;
                  } catch (EvidenceQuotaExceededException expected) {
                    return false;
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              List.of(
                  accepted.get(0).get(10, TimeUnit.SECONDS),
                  accepted.get(1).get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            calls.countByProviderAndRequestedAtGreaterThanEqual(
                SocialEvidenceProvider.INSTAGRAM, Instant.EPOCH))
        .isEqualTo(1);
    verify(source, times(50))
        .requireApprovedBinding(unapproved.getId(), SocialEvidenceProvider.INSTAGRAM);
    verify(source).requireApprovedBinding(firstBrand.getId(), SocialEvidenceProvider.INSTAGRAM);
    verify(source).requireApprovedBinding(secondBrand.getId(), SocialEvidenceProvider.INSTAGRAM);
    verifyNoMoreInteractions(source);
  }

  @Test
  void failedAttemptCommitsProviderBudgetAndOtherWorkspaceCannotRetryAroundIt() {
    Workspace first = workspaces.saveAndFlush(new Workspace("Failed provider attempt", "Owner"));
    Workspace second = workspaces.saveAndFlush(new Workspace("Other provider attempt", "Owner"));
    Brand firstBrand = brands.saveAndFlush(new Brand("Failed", first));
    Brand secondBrand = brands.saveAndFlush(new Brand("Other", second));
    doThrow(new IllegalStateException("Bearer fixture-sensitive-provider-error"))
        .when(source)
        .fetch(firstBrand.getId(), SocialEvidenceProvider.TIKTOK);
    authenticate(first.getId());
    assertThatThrownBy(
            () ->
                registration.register(
                    firstBrand.getId(), Set.of(first.getId()), SocialEvidenceProvider.TIKTOK))
        .isInstanceOf(SocialEvidenceUnavailableException.class)
        .hasNoCause();
    authenticate(second.getId());
    assertThatThrownBy(
            () ->
                registration.register(
                    secondBrand.getId(), Set.of(second.getId()), SocialEvidenceProvider.TIKTOK))
        .isInstanceOf(EvidenceQuotaExceededException.class);
    assertThat(
            calls.countByProviderAndRequestedAtGreaterThanEqual(
                SocialEvidenceProvider.TIKTOK, Instant.EPOCH))
        .isEqualTo(1);
    verify(source).fetch(firstBrand.getId(), SocialEvidenceProvider.TIKTOK);
    verify(source).requireApprovedBinding(firstBrand.getId(), SocialEvidenceProvider.TIKTOK);
    verify(source).requireApprovedBinding(secondBrand.getId(), SocialEvidenceProvider.TIKTOK);
    verifyNoMoreInteractions(source);
  }

  @Test
  void databasePreservesExactlyTwoImmutableProviderLockIdentities() {
    assertThat(jdbc.queryForObject("select count(*) from provider_capacity_slots", Integer.class))
        .isEqualTo(2);
    assertThatThrownBy(
            () -> jdbc.update("delete from provider_capacity_slots where provider='INSTAGRAM'"))
        .hasStackTraceContaining("Social provider capacity identities are immutable");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update provider_capacity_slots set provider='TIKTOK' where provider='INSTAGRAM'"))
        .hasStackTraceContaining("Social provider capacity identities are immutable");
    assertThatThrownBy(
            () -> jdbc.update("insert into provider_capacity_slots(provider) values ('OTHER')"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private static void authenticate(UUID workspaceId) {
    var principal =
        new CadencePrincipal(
            UUID.randomUUID(), "social-global-fixture", UserRole.OWNER, Set.of(workspaceId));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                principal, "N/A", List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
  }
}
