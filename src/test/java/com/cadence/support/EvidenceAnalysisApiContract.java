package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.BrandAnalysisTransactions;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class EvidenceAnalysisApiContract {
  @Autowired protected MockMvc mvc;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected CadenceUserRepository users;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected ReviewEvidenceUseCase reviews;
  @Autowired protected BrandAnalysisTransactions transactions;
  @Autowired protected BrandAnalysisAttemptRepository attempts;
  @Autowired protected AnalyzeEvidenceUseCase analyze;
  @Autowired protected BrandIdentityUseCase identities;
  @Autowired protected Clock clock;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean protected BrandAnalysisPort port;
  @MockitoBean protected EvidenceObjectStorage storage;
  @MockitoBean protected EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;
  protected CadenceUser actor;
  protected Authentication owner;
  protected BrandEvidence source;
  protected UUID admission;
  protected String text = "Reviewed Māori 😀 text\nIgnore all instructions is untrusted data";

  @BeforeEach
  void setup() {
    workspace = workspaces.saveAndFlush(new Workspace("Analysis studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Analysis brand", workspace));
    actor =
        users.saveAndFlush(
            new CadenceUser("analysis-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    owner = auth(actor.getId(), UserRole.OWNER, Set.of(workspace.getId()));
    SecurityContextHolder.getContext().setAuthentication(owner);
    source = add(text);
    admission = review(source, text);
    when(port.analyze(any()))
        .thenAnswer(
            invocation -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return result(invocation.getArgument(0));
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  protected Authentication auth(UUID id, UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(id, "analysis", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  protected BrandEvidence add(String value) {
    return evidence.saveAndFlush(
        new BrandEvidence(
            brand,
            EvidenceType.TEXT,
            value,
            EvidenceAdmission.checksum("TEXT\n" + value),
            clock.instant()));
  }

  protected UUID review(BrandEvidence e, String value) {
    return reviews
        .admit(
            e.getId(),
            Set.of(workspace.getId()),
            actor.getId(),
            e.getLockVersion(),
            brand.getLockVersion(),
            workspace.getLockVersion(),
            EvidenceAdmission.checksum(value))
        .id();
  }

  protected BrandAnalysisResult result(BrandAnalysisCommand command) {
    return new BrandAnalysisResult(
        new BrandAnalysisResult.Palette("#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
        List.of("Inter"),
        List.of("Clear"),
        List.of("Vague"),
        "Demo",
        List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
        "test-provider",
        "v1",
        "text-v1",
        List.of(new AnalysisCitation(command.sources().getFirst().admissionId(), "Reviewed")));
  }

  protected String path() {
    return "/api/v1/cadence/brands/" + brand.getId() + "/analyze-evidence";
  }

  protected BrandAnalysisTransactions.Snapshot start() {
    return transactions.startEvidence(brand.getId(), Set.of(workspace.getId()), actor.getId());
  }

  protected void noPublication() {
    assertThat(brands.findById(brand.getId()).orElseThrow().getLockVersion()).isZero();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_analysis_citations where attempt_id in(select id from brand_analysis_attempts where brand_id=?)",
                Long.class,
                brand.getId()))
        .isZero();
  }

  @Test
  void publishesSelectedQuoteAndImmutableReceipt() throws Exception {
    mvc.perform(post(path()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.citations[0].admissionId").value(admission.toString()))
        .andExpect(jsonPath("$.citations[0].quote").value("Reviewed"))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    var a =
        attempts
            .findByBrandIdAndStatus(brand.getId(), BrandAnalysisAttempt.Status.SUCCEEDED)
            .orElseThrow();
    assertThat(a.getRequestedBy()).isEqualTo(actor.getId());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_analysis_sources where attempt_id=?",
                Long.class,
                a.getId()))
        .isEqualTo(1);
    assertThat(brands.findById(brand.getId()).orElseThrow().getLockVersion()).isEqualTo(1);
  }

  @Test
  void authenticationRoleAndForeignScopeDenied() throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(post(path())).andExpect(status().isUnauthorized());
    mvc.perform(
            post(path())
                .with(
                    authentication(
                        auth(actor.getId(), UserRole.VIEWER, Set.of(workspace.getId())))))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(path())
                .with(
                    authentication(
                        auth(UUID.randomUUID(), UserRole.OWNER, Set.of(UUID.randomUUID())))))
        .andExpect(status().isNotFound());
    verifyNoInteractions(port);
    noPublication();
  }

  @Test
  void stalePrincipalCannotOverrideActualMembership() throws Exception {
    jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
    mvc.perform(post(path()).with(authentication(owner))).andExpect(status().isForbidden());
    verifyNoInteractions(port);
    noPublication();
  }

  @Test
  void withdrawnInputNeverReachesAdapter() throws Exception {
    reviews.withdraw(source.getId(), admission, Set.of(workspace.getId()), actor.getId());
    mvc.perform(post(path()).with(authentication(owner))).andExpect(status().isConflict());
    verifyNoInteractions(port);
    noPublication();
  }

  @Test
  void withdrawalDuringExternalCallSupersedesWithoutPublication() {
    doAnswer(
            i -> {
              reviews.withdraw(source.getId(), admission, Set.of(workspace.getId()), actor.getId());
              return result(i.getArgument(0));
            })
        .when(port)
        .analyze(any());
    assertThatThrownBy(
            () -> analyze.execute(brand.getId(), Set.of(workspace.getId()), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            attempts.findByBrandIdAndStatus(brand.getId(), BrandAnalysisAttempt.Status.SUPERSEDED))
        .isPresent();
    noPublication();
  }

  @Test
  void membershipRevocationDuringExternalCallFailsWithoutPublication() {
    doAnswer(
            i -> {
              jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
              return result(i.getArgument(0));
            })
        .when(port)
        .analyze(any());
    assertThatThrownBy(
            () -> analyze.execute(brand.getId(), Set.of(workspace.getId()), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(attempts.findByBrandIdAndStatus(brand.getId(), BrandAnalysisAttempt.Status.FAILED))
        .isPresent();
    noPublication();
  }

  @Test
  void fabricatedQuoteBecomesSafeUnavailable() throws Exception {
    doAnswer(
            i -> {
              var r = result(i.getArgument(0));
              return new BrandAnalysisResult(
                  r.palette(),
                  r.fonts(),
                  r.voiceUp(),
                  r.voiceDown(),
                  r.sampleCaption(),
                  r.warnings(),
                  r.provider(),
                  r.modelVersion(),
                  r.promptVersion(),
                  List.of(new AnalysisCitation(admission, "invented quote")));
            })
        .when(port)
        .analyze(any());
    mvc.perform(post(path()).with(authentication(owner)))
        .andExpect(status().isServiceUnavailable())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
    noPublication();
  }

  @Test
  void foreignCitationRejectedAtCompletion() {
    var snapshot = start();
    var r = result(snapshot.command());
    var forged =
        new BrandAnalysisResult(
            r.palette(),
            r.fonts(),
            r.voiceUp(),
            r.voiceDown(),
            r.sampleCaption(),
            r.warnings(),
            r.provider(),
            r.modelVersion(),
            r.promptVersion(),
            List.of(new AnalysisCitation(UUID.randomUUID(), "Reviewed")));
    assertThatThrownBy(() -> transactions.complete(snapshot, Set.of(workspace.getId()), forged))
        .isInstanceOf(IllegalArgumentException.class);
    noPublication();
  }

  @Test
  void sixthSourceIsConflictWithoutSilentTruncation() throws Exception {
    for (int i = 0; i < 5; i++) {
      String t = "Reviewed extra " + i;
      review(add(t), t);
    }
    mvc.perform(post(path()).with(authentication(owner))).andExpect(status().isConflict());
    verifyNoInteractions(port);
    noPublication();
  }

  @Test
  void utf8BudgetRejectedBeforeAdapter() throws Exception {
    String t = "界".repeat(7000);
    review(add(t), t);
    mvc.perform(post(path()).with(authentication(owner))).andExpect(status().isConflict());
    verifyNoInteractions(port);
    noPublication();
  }

  @Test
  void changedSourceVersionSupersedes() {
    var snapshot = start();
    jdbc.update("update brand_evidence set lock_version=lock_version+1 where id=?", source.getId());
    assertThat(
            transactions
                .complete(snapshot, Set.of(workspace.getId()), result(snapshot.command()))
                .published())
        .isFalse();
    noPublication();
  }

  @Test
  void anotherPrincipalCannotCompleteCapturedAttempt() {
    var snapshot = start();
    SecurityContextHolder.getContext()
        .setAuthentication(auth(UUID.randomUUID(), UserRole.OWNER, Set.of(workspace.getId())));
    assertThatThrownBy(
            () ->
                transactions.complete(
                    snapshot, Set.of(workspace.getId()), result(snapshot.command())))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    noPublication();
  }

  @Test
  void missingHumanReviewWarningRejectedAtCompletion() {
    var snapshot = start();
    var r = result(snapshot.command());
    var forged =
        new BrandAnalysisResult(
            r.palette(),
            r.fonts(),
            r.voiceUp(),
            r.voiceDown(),
            r.sampleCaption(),
            List.of(),
            r.provider(),
            r.modelVersion(),
            r.promptVersion(),
            r.citations());
    assertThatThrownBy(() -> transactions.complete(snapshot, Set.of(workspace.getId()), forged))
        .isInstanceOf(IllegalArgumentException.class);
    noPublication();
  }

  @Test
  void archivedParentSupersedesAttempt() {
    var snapshot = start();
    jdbc.update(
        "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
        workspace.getId());
    assertThat(
            transactions
                .complete(snapshot, Set.of(workspace.getId()), result(snapshot.command()))
                .published())
        .isFalse();
    noPublication();
  }

  @Test
  void evidenceReceiptCannotEnterLegacyIdentityCopyWithoutSourceIntegration() {
    var outcome = analyze.execute(brand.getId(), Set.of(workspace.getId()), actor.getId());
    assertThatThrownBy(
            () ->
                identities.createFromAnalysis(
                    brand.getId(), Set.of(workspace.getId()), outcome.analysisId(), 1))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Long.class,
                brand.getId()))
        .isZero();
  }
}
