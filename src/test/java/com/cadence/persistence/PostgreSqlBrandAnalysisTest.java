package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.BrandAnalysisTransactions;
import com.cadence.application.usecases.AnalyzeBrandUseCase;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest
@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlBrandAnalysisTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired WorkspaceRepository workspaces;
  @Autowired CadenceUserRepository users;
  UUID actor;
  @Autowired BrandRepository brands;
  @Autowired BrandAnalysisAttemptRepository attempts;
  @Autowired AnalyzeBrandUseCase analysis;
  @Autowired BrandAnalysisTransactions transactions;
  @Autowired JdbcTemplate jdbc;
  @MockitoBean EvidenceObjectStorage storage;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean ApprovedSocialEvidenceSource socialSource;
  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @MockitoBean BrandAnalysisPort port;
  @MockitoBean java.time.Clock clock;
  Workspace workspace;
  Brand brand;
  Set<UUID> scope;

  static BrandAnalysisResult result(String caption) {
    return new BrandAnalysisResult(
        new BrandAnalysisResult.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        List.of("Inter"),
        List.of("clear"),
        List.of("spam"),
        caption,
        List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
        "test-provider",
        "model-v1",
        "prompt-v1");
  }

  @BeforeEach
  void setup() {
    when(clock.instant())
        .thenAnswer(i -> java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    workspace = workspaces.saveAndFlush(new Workspace("Studio", "Creator"));
    brand = new Brand("Brand", workspace);
    brand.updateSignature(result("Previous").toSignature());
    brand = brands.saveAndFlush(brand);
    scope = Set.of(workspace.getId());
    actor =
        users
            .saveAndFlush(
                new CadenceUser("analysis-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)))
            .getId();
    authenticate(workspace.getId());
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void authenticate(UUID workspace) {
    var principal =
        new CadencePrincipal(actor, "analysis-fixture", UserRole.OWNER, Set.of(workspace));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
  }

  BrandAnalysisAttempt receipt() {
    return attempts.findAll().stream()
        .filter(a -> a.getBrandId().equals(brand.getId()))
        .findFirst()
        .orElseThrow();
  }

  String caption() {
    return brands
        .findByIdAndWorkspaceIdIn(brand.getId(), scope)
        .orElseThrow()
        .getSignature()
        .getSampleCaption();
  }

  @Test
  void publishesValidatedOutputAndProvenanceWithoutTransactionDuringAdapter() {
    when(port.analyze(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return result("Published");
            });
    assertThat(analysis.execute(brand.getId(), scope).getSampleCaption()).isEqualTo("Published");
    var attempt = receipt();
    assertThat(attempt.getStatus()).isEqualTo(BrandAnalysisAttempt.Status.SUCCEEDED);
    assertThat(attempt.getProvider()).isEqualTo("test-provider");
    assertThat(attempt.getModelVersion()).isEqualTo("model-v1");
    assertThat(attempt.getWarnings()).isEqualTo("HUMAN_REVIEW_REQUIRED");
    assertThat(attempt.getOutputChecksum()).isEqualTo(result("Published").contentChecksum());
    assertThat(caption()).isEqualTo("Published");
  }

  @Test
  void identicalPublicationStillAdvancesVersionAndPersistsEachReceipt() {
    when(port.analyze(any())).thenReturn(result("Previous"));
    analysis.execute(brand.getId(), scope);
    analysis.execute(brand.getId(), scope);
    assertThat(brands.findById(brand.getId()).orElseThrow().getLockVersion())
        .isEqualTo(brand.getLockVersion() + 2);
    assertThat(
            attempts.findAll().stream().filter(a -> a.getBrandId().equals(brand.getId())).toList())
        .hasSize(2)
        .allMatch(a -> a.getStatus() == BrandAnalysisAttempt.Status.SUCCEEDED);
  }

  @Test
  void providerFailurePreservesPreviousSignatureAndCommitsFailedReceipt() {
    when(port.analyze(any())).thenThrow(new IllegalArgumentException("api_key=private-secret"));
    assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
        .isInstanceOf(BrandAnalysisUnavailableException.class)
        .hasNoCause()
        .hasMessage("Brand analysis is unavailable");
    assertThat(caption()).isEqualTo("Previous");
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.FAILED);
    assertThat(receipt().getProvider()).isNull();
  }

  @Test
  void nullOutputCannotReplacePreviousSignature() {
    assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
        .isInstanceOf(BrandAnalysisUnavailableException.class);
    assertThat(caption()).isEqualTo("Previous");
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.FAILED);
  }

  @Test
  void brandEditedDuringAnalysisSupersedesResult() {
    when(port.analyze(any()))
        .thenAnswer(
            i -> {
              var edited = brands.findByIdAndWorkspaceIdIn(brand.getId(), scope).orElseThrow();
              edited.rename("Changed");
              brands.saveAndFlush(edited);
              return result("Stale");
            });
    assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    assertThat(caption()).isEqualTo("Previous");
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.SUPERSEDED);
  }

  @Test
  void workspaceArchivedDuringAnalysisSupersedesResult() {
    when(port.analyze(any()))
        .thenAnswer(
            i -> {
              var edited = workspaces.findById(workspace.getId()).orElseThrow();
              edited.archive();
              workspaces.saveAndFlush(edited);
              return result("Stale");
            });
    assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    assertThat(caption()).isEqualTo("Previous");
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.SUPERSEDED);
  }

  @Test
  void concurrentRequestCannotStartSecondAdapterCall() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(port.analyze(any()))
        .thenAnswer(
            i -> {
              entered.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
              return result("Only winner");
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                authenticate(workspace.getId());
                try {
                  return analysis.execute(brand.getId(), scope);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Brand analysis is already running");
      } finally {
        release.countDown();
      }
      assertThat(first.get(10, TimeUnit.SECONDS).getSampleCaption()).isEqualTo("Only winner");
    }
    verify(port, times(1)).analyze(any());
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.SUCCEEDED);
  }

  @Test
  void expiredResultCannotPublishAndExpiredRunningAttemptCanBeRecovered() {
    var first = transactions.start(brand.getId(), scope);
    when(clock.instant())
        .thenReturn(
            java.time.Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                .plusSeconds(61));
    var second = transactions.start(brand.getId(), scope);
    assertThat(attempts.findById(first.command().analysisId()).orElseThrow().getStatus())
        .isEqualTo(BrandAnalysisAttempt.Status.FAILED);
    assertThat(transactions.complete(first, scope, result("Late old result")).published())
        .isFalse();
    assertThat(transactions.complete(second, scope, result("New result")).published()).isTrue();
    assertThat(caption()).isEqualTo("New result");
  }

  @Test
  void deadlineAtCompletionSupersedesOtherwiseValidResult() {
    var first = transactions.start(brand.getId(), scope);
    when(clock.instant())
        .thenReturn(
            java.time.Instant.now()
                .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
                .plusSeconds(61));
    assertThat(transactions.complete(first, scope, result("Too late")).published()).isFalse();
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.SUPERSEDED);
    assertThat(caption()).isEqualTo("Previous");
  }

  @Test
  void databaseClockRejectsBackdatedPublicationAndRollsBackSignatureAtomically() {
    var past =
        java.time.Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
    when(clock.instant()).thenReturn(past);
    when(port.analyze(any()))
        .thenAnswer(
            i -> {
              when(clock.instant()).thenReturn(past.plusSeconds(1));
              return result("Expired by real DB clock");
            });
    assertThatThrownBy(() -> analysis.execute(brand.getId(), scope))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(caption()).isEqualTo("Previous");
    assertThat(receipt().getStatus()).isEqualTo(BrandAnalysisAttempt.Status.FAILED);
  }

  @Test
  void immutableTerminalReceiptAndSourceCannotBeRewrittenOrDeleted() {
    when(port.analyze(any())).thenReturn(result("Published"));
    analysis.execute(brand.getId(), scope);
    var id = receipt().getId();
    assertThatThrownBy(
            () ->
                jdbc.update("update brand_analysis_attempts set provider='forged' where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("delete from brand_analysis_attempts where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(receipt().getProvider()).isEqualTo("test-provider");
  }

  @Test
  void transactionalHelpersRejectForgedScopeAndForeignBrandBeforeCreatingAttempt() {
    assertThatThrownBy(() -> transactions.start(brand.getId(), Set.of(UUID.randomUUID())))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    var foreign = workspaces.saveAndFlush(new Workspace("Foreign", "Creator"));
    var foreignBrand = brands.saveAndFlush(new Brand("Foreign", foreign));
    assertThatThrownBy(() -> transactions.start(foreignBrand.getId(), scope))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoInteractions(port);
    assertThat(
            attempts.findAll().stream().filter(a -> a.getBrandId().equals(brand.getId())).toList())
        .isEmpty();
  }

  @Test
  void databaseRejectsDuplicateRunningAttemptAndSourceMutation() {
    var snapshot = transactions.start(brand.getId(), scope);
    var id = snapshot.command().analysisId();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_analysis_attempts set brand_version=brand_version+1 where id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_analysis_attempts select ?,created_at,updated_at,lock_version,brand_id,brand_version,workspace_version,source_checksum,started_at,deadline,completed_at,status,provider,model_version,prompt_version,warnings,output_checksum from brand_analysis_attempts where id=?",
                    UUID.randomUUID(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_analysis_attempts set status='FAILED',completed_at=null where id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    transactions.fail(snapshot, scope);
  }
}
