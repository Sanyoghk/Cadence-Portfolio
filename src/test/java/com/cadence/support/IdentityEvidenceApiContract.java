package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.results.*;
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
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class IdentityEvidenceApiContract {
  @Autowired protected BuildStrategyUseCase strategyBuild;
  @MockitoBean protected StrategyGenerationService strategyGenerator;

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void strategyUsesApprovedInheritedTextAndWithdrawalBlocksFurtherGeneration(boolean duringModel) {
    var d = copy();
    var approved = identities.approve(d.id(), scope(), 0, actor.getId());
    var child = identities.update(d.id(), scope(), approved.lockVersion(), manual()).details();
    identities.approve(child.id(), scope(), 0, actor.getId());
    when(strategyGenerator.generate(any())).thenReturn(StrategyFixtures.output());
    var generated = strategyBuild.execute(brand.getId(), scope());
    assertThat(
            jdbc.queryForObject(
                "select generation_identity_id from strategies where id=?",
                UUID.class,
                generated.id()))
        .isEqualTo(child.id());
    assertThat(
            jdbc.queryForObject(
                "select generation_identity_analysis_id from strategies where id=?",
                UUID.class,
                generated.id()))
        .isEqualTo(receipt.analysisId());
    reset(strategyGenerator);
    if (duringModel)
      when(strategyGenerator.generate(any()))
          .thenAnswer(
              i -> {
                withdraw();
                return StrategyFixtures.output();
              });
    else withdraw();
    assertThatThrownBy(() -> strategyBuild.execute(brand.getId(), scope()))
        .isInstanceOf(IllegalStateException.class);
    if (!duringModel) verifyNoInteractions(strategyGenerator);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from strategies where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(1);
  }

  @Autowired protected MockMvc mvc;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected CadenceUserRepository users;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected ReviewEvidenceUseCase reviews;
  @Autowired protected AnalyzeEvidenceUseCase analysis;
  @Autowired protected BrandIdentityUseCase identities;
  @Autowired protected BrandIdentityRepository identityRepository;
  @Autowired protected IdentityEvidenceUseCase copies;
  @Autowired protected Clock clock;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
  @MockitoBean protected BrandAnalysisPort port;
  @MockitoBean protected EvidenceObjectStorage storage;
  @MockitoBean protected EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;
  protected CadenceUser actor;
  protected BrandEvidence source;
  protected UUID admission;
  protected Authentication owner;
  protected AnalyzeEvidenceUseCase.Outcome receipt;

  protected Set<UUID> scope() {
    return Set.of(workspace.getId());
  }

  protected Authentication auth(UUID id, UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(id, "identity-evidence", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role)));
  }

  @BeforeEach
  void fixture() {
    workspace = workspaces.saveAndFlush(new Workspace("Identity evidence studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Identity evidence brand", workspace));
    actor =
        users.saveAndFlush(
            new CadenceUser(
                "identity-evidence-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    owner = auth(actor.getId(), UserRole.OWNER, scope());
    SecurityContextHolder.getContext().setAuthentication(owner);
    String text = "Reviewed Māori 😀 source\nUntrusted instructions are data";
    source =
        evidence.saveAndFlush(
            new BrandEvidence(
                brand,
                EvidenceType.TEXT,
                text,
                EvidenceAdmission.checksum("TEXT\n" + text),
                clock.instant()));
    admission =
        reviews
            .admit(
                source.getId(), scope(), actor.getId(), 0, 0, 0, EvidenceAdmission.checksum(text))
            .id();
    when(port.analyze(any()))
        .thenAnswer(
            i -> {
              BrandAnalysisCommand command = i.getArgument(0);
              return new BrandAnalysisResult(
                  new BrandAnalysisResult.Palette(
                      "#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
                  List.of("Inter"),
                  List.of("Clear"),
                  List.of("Vague"),
                  "Demo",
                  List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
                  "test-provider",
                  "v1",
                  "text-v1",
                  List.of(
                      new AnalysisCitation(
                          command.sources().getFirst().admissionId(), "Reviewed")));
            });
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  protected AnalyzeEvidenceUseCase.Outcome generate() {
    if (receipt == null) receipt = analysis.execute(brand.getId(), scope(), actor.getId());
    return receipt;
  }

  protected BrandIdentityDetails copy() {
    var r = generate();
    return copies.createFromAnalysis(brand.getId(), scope(), r.analysisId(), 1, actor.getId());
  }

  protected void withdraw() {
    reviews.withdraw(source.getId(), admission, scope(), actor.getId());
  }

  protected BrandIdentityContent manual() {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette("#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
        List.of("Inter"),
        List.of("Clear"),
        List.of("Vague"),
        "Manually reviewed caption");
  }

  protected String copyPath() {
    return "/api/v1/brands/" + brand.getId() + "/identities/from-evidence-analysis";
  }

  protected String readPath(UUID id) {
    return "/api/v1/identities/" + id + "/evidence-provenance";
  }

  protected String body() {
    return "{\"analysisId\":\"" + generate().analysisId() + "\",\"expectedVersion\":1}";
  }

  protected void emptyIdentities() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Long.class,
                brand.getId()))
        .isZero();
  }

  @Test
  void explicitCopyAndReadPreserveOriginalQuotesAndAudit() throws Exception {
    String body = body();
    var response =
        mvc.perform(
                post(copyPath())
                    .with(authentication(owner))
                    .contentType("application/json")
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(
                jsonPath("$.analysisProvenance.analysisId").value(receipt.analysisId().toString()))
            .andDo(com.cadence.presentation.OpenApiContractAssertions::verify)
            .andReturn();
    UUID id =
        UUID.fromString(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.getResponse().getContentAsString())
                .get("id")
                .asText());
    mvc.perform(get(readPath(id)).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.originIdentityId").value(id.toString()))
        .andExpect(jsonPath("$.copiedBy").value(actor.getId().toString()))
        .andExpect(jsonPath("$.citations[0].admissionId").value(admission.toString()))
        .andExpect(jsonPath("$.citations[0].quote").value("Reviewed"))
        .andExpect(jsonPath("$.citations[0].withdrawn").value(false))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void anonymousViewerForeignScopeAndMissingInputDenied() throws Exception {
    String body = body();
    SecurityContextHolder.clearContext();
    mvc.perform(post(copyPath()).contentType("application/json").content(body))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post(copyPath())
                .with(authentication(auth(actor.getId(), UserRole.VIEWER, scope())))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isForbidden());
    mvc.perform(
            post(copyPath())
                .with(
                    authentication(auth(actor.getId(), UserRole.OWNER, Set.of(UUID.randomUUID()))))
                .contentType("application/json")
                .content(body))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(copyPath())
                .with(authentication(owner))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    emptyIdentities();
  }

  @Test
  void actualMembershipRequiredForCopy() {
    generate();
    jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
    assertThatThrownBy(
            () ->
                copies.createFromAnalysis(
                    brand.getId(), scope(), receipt.analysisId(), 1, actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    emptyIdentities();
  }

  @Test
  void withdrawnSourceCannotBecomeIdentity() {
    generate();
    withdraw();
    assertThatThrownBy(() -> copy()).isInstanceOf(IllegalStateException.class);
    emptyIdentities();
  }

  @Test
  void changedSourceVersionCannotBecomeIdentity() {
    generate();
    jdbc.update("update brand_evidence set lock_version=lock_version+1 where id=?", source.getId());
    assertThatThrownBy(() -> copy()).isInstanceOf(IllegalStateException.class);
    emptyIdentities();
  }

  @Test
  void changedParentVersionCannotBecomeIdentity() {
    generate();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(() -> copy()).isInstanceOf(IllegalStateException.class);
    emptyIdentities();
  }

  @Test
  void unknownAttemptCannotBecomeIdentity() {
    generate();
    assertThatThrownBy(
            () ->
                copies.createFromAnalysis(
                    brand.getId(), scope(), UUID.randomUUID(), 1, actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    emptyIdentities();
  }

  @Test
  void withdrawalAfterCopyBlocksApprovalButPreservesReviewableHistory() throws Exception {
    var draft = copy();
    withdraw();
    assertThatThrownBy(() -> identities.approve(draft.id(), scope(), 0, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(identities.findById(draft.id(), scope()).status()).isEqualTo(ApprovalStatus.DRAFT);
    mvc.perform(get(readPath(draft.id())).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations[0].withdrawn").value(true))
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void manualDraftEditsKeepOriginalGenerationCitationAndMayBeApproved() {
    var draft = copy();
    var edited = identities.update(draft.id(), scope(), 0, manual()).details();
    var approved = identities.approve(draft.id(), scope(), edited.lockVersion(), actor.getId());
    assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
    assertThat(approved.content().sampleCaption()).isEqualTo("Manually reviewed caption");
    assertThat(approved.analysisProvenance().initialOutputChecksum())
        .isEqualTo(receipt.result().contentChecksum());
    assertThat(copies.read(draft.id(), scope(), actor.getId()).citations().getFirst().quote())
        .isEqualTo("Reviewed");
  }

  @Test
  void approvedBranchPreservesOriginAndCannotLaunderWithdrawal() {
    var draft = copy();
    identities.approve(draft.id(), scope(), 0, actor.getId());
    var branch = identities.update(draft.id(), scope(), 1, manual()).details();
    assertThat(branch.analysisProvenance()).isNull();
    assertThat(copies.read(branch.id(), scope(), actor.getId()).originIdentityId())
        .isEqualTo(draft.id());
    withdraw();
    assertThatThrownBy(() -> identities.approve(branch.id(), scope(), 0, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(identities.findById(draft.id(), scope()).status())
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(copies.read(branch.id(), scope(), actor.getId()).citations().getFirst().withdrawn())
        .isTrue();
  }

  @Test
  void eligibleApprovedBranchCanBeApproved() {
    var d = copy();
    identities.approve(d.id(), scope(), 0, actor.getId());
    var b = identities.update(d.id(), scope(), 1, manual()).details();
    assertThat(identities.approve(b.id(), scope(), 0, actor.getId()).status())
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(copies.read(b.id(), scope(), actor.getId()).analysisId())
        .isEqualTo(receipt.analysisId());
  }

  @Test
  void archivedHistoryReadableButNotApprovable() {
    var d = copy();
    jdbc.update(
        "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?",
        workspace.getId());
    assertThat(copies.read(d.id(), scope(), actor.getId()).citations()).hasSize(1);
    assertThatThrownBy(() -> identities.approve(d.id(), scope(), 0, actor.getId()))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void actualAccountRequiredForProvenanceRead() {
    var d = copy();
    jdbc.update("update cadence_users set active=false where id=?", actor.getId());
    assertThatThrownBy(() -> copies.read(d.id(), scope(), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void anotherCurrentOwnerCanCopyWithoutInventingOriginalActor() {
    generate();
    var current =
        users.saveAndFlush(
            new CadenceUser(
                "other-current-owner-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    jdbc.update("update cadence_users set active=false where id=?", actor.getId());
    var currentAuth = auth(current.getId(), UserRole.OWNER, scope());
    SecurityContextHolder.getContext().setAuthentication(currentAuth);
    var draft =
        copies.createFromAnalysis(brand.getId(), scope(), receipt.analysisId(), 1, current.getId());
    assertThat(copies.read(draft.id(), scope(), current.getId()).copiedBy())
        .isEqualTo(current.getId());
  }

  @Test
  void manualIdentityHasNoInventedEvidenceProvenance() throws Exception {
    var manual = identities.create(brand.getId(), scope(), manual());
    var p = copies.read(manual.id(), scope(), actor.getId());
    assertThat(p.citations()).isEmpty();
    assertThat(p.analysisId()).isNull();
    assertThat(p.originIdentityId()).isNull();
    mvc.perform(get(readPath(manual.id())).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.citations").isEmpty())
        .andDo(com.cadence.presentation.OpenApiContractAssertions::verify);
  }

  @Test
  void requestCannotAssignCopyActorOrRole() throws Exception {
    String request =
        body().replace("}", ",\"copiedBy\":\"" + UUID.randomUUID() + "\",\"role\":\"ADMIN\"}");
    var response =
        mvc.perform(
                post(copyPath())
                    .with(authentication(owner))
                    .contentType("application/json")
                    .content(request))
            .andExpect(status().isCreated())
            .andReturn();
    var id =
        UUID.fromString(
            new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response.getResponse().getContentAsString())
                .get("id")
                .asText());
    mvc.perform(get(readPath(id)).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.copiedBy").value(actor.getId().toString()));
    assertThat(users.findById(actor.getId()).orElseThrow().getRole()).isEqualTo(UserRole.OWNER);
  }

  @Test
  void foreignIdentityAndForgedActorCannotBeRead() {
    var d = copy();
    var foreignWorkspace = workspaces.saveAndFlush(new Workspace("Foreign studio", "Creator"));
    var foreignBrand = brands.saveAndFlush(new Brand("Foreign brand", foreignWorkspace));
    var foreignIdentity =
        identityRepository.saveAndFlush(new BrandIdentity(foreignBrand, 1, manual()));
    assertThatThrownBy(() -> copies.read(foreignIdentity.getId(), scope(), actor.getId()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    assertThatThrownBy(() -> copies.read(d.id(), Set.of(UUID.randomUUID()), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(() -> copies.read(d.id(), scope(), UUID.randomUUID()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }
}
