package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.application.usecases.BrandIdentityUseCase;
import com.cadence.domain.*;
import com.cadence.presentation.OpenApiContractAssertions;
import com.cadence.security.CadencePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class BrandIdentityApiContract {
  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected BrandIdentityRepository identities;
  @Autowired protected BrandIdentityUseCase useCase;
  @MockitoBean protected com.cadence.application.ports.EvidenceObjectStorage storage;
  @MockitoBean protected com.cadence.application.ports.EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;
  protected Authentication owner;
  @Autowired protected CadenceUserRepository users;
  protected UUID actorId;
  @Autowired protected org.springframework.transaction.PlatformTransactionManager manager;

  @Test
  void approvesCommittedContentAndApprovedEditCreatesIndependentDraftWithLineage()
      throws Exception {
    var id = create();
    mvc.perform(
            patch("/api/v1/identities/{id}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of("expectedVersion", 0, "content", content("Reviewed caption")))))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/identities/{id}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            post("/api/v1/identities/{id}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "expectedVersion",
                            1,
                            "approvedBy",
                            UUID.randomUUID(),
                            "approvedAt",
                            "2000-01-01T00:00:00Z",
                            "content",
                            content("Unreviewed forged content")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedBy").value(actorId.toString()))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty())
        .andExpect(jsonPath("$.content.sampleCaption").value("Reviewed caption"))
        .andExpect(jsonPath("$.lockVersion").value(2))
        .andDo(OpenApiContractAssertions::verify);
    create();
    var response =
        mvc.perform(
                patch("/api/v1/identities/{id}", id)
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        mapper.writeValueAsString(
                            Map.of("expectedVersion", 2, "content", content("New branch")))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.revisedFromId").value(id.toString()))
            .andExpect(jsonPath("$.approvedBy").isEmpty())
            .andExpect(jsonPath("$.approvedAt").isEmpty())
            .andExpect(jsonPath("$.lockVersion").value(0))
            .andDo(OpenApiContractAssertions::verify)
            .andReturn()
            .getResponse();
    var branchId = mapper.readTree(response.getContentAsString()).path("id").asText();
    assertThat(response.getHeader("Location")).isEqualTo("/api/v1/identities/" + branchId);
    mvc.perform(get("/api/v1/identities/{id}", id).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.lockVersion").value(2))
        .andExpect(jsonPath("$.content.sampleCaption").value("Reviewed caption"));
    mvc.perform(
            post("/api/v1/identities/{id}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2}"))
        .andExpect(status().isConflict());
  }

  @Test
  void approvalRejectsAnonymousRolesForeignIdsInvalidTokensAndForgedDirectActor() throws Exception {
    var id = create();
    var path = "/api/v1/identities/" + id + "/approval";
    mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content("{\"expectedVersion\":0}"))
        .andExpect(status().isUnauthorized());
    for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER)) {
      var denied = auth(role, Set.of(workspace.getId()));
      mvc.perform(
              post(path)
                  .with(authentication(denied))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedVersion\":0}"))
          .andExpect(status().isForbidden());
      SecurityContextHolder.getContext().setAuthentication(denied);
      assertThatThrownBy(
              () ->
                  useCase.approve(
                      id,
                      Set.of(workspace.getId()),
                      0,
                      ((CadencePrincipal) denied.getPrincipal()).userId()))
          .isInstanceOf(AccessDeniedException.class);
    }
    for (var unknown : List.of(id, UUID.randomUUID()))
      mvc.perform(
              post("/api/v1/identities/{id}/approval", unknown)
                  .with(authentication(auth(UserRole.OWNER, Set.of(UUID.randomUUID()))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedVersion\":0}"))
          .andExpect(status().isNotFound())
          .andDo(OpenApiContractAssertions::verify);
    for (var invalid : List.of("{}", "{\"expectedVersion\":null}", "{\"expectedVersion\":-1}"))
      mvc.perform(
              post(path)
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest());
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(() -> useCase.approve(id, Set.of(workspace.getId()), 0, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> useCase.approve(id, Set.of(workspace.getId(), UUID.randomUUID()), 0, actorId))
        .isInstanceOf(AccessDeniedException.class);
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(
            post(path)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isConflict());
  }

  @Test
  void approvalRechecksActualAccountRoleMembershipAndActivity() throws Exception {
    var id = create();
    var inactive =
        new CadenceUser("inactive-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace));
    org.springframework.test.util.ReflectionTestUtils.setField(inactive, "active", false);
    for (var actor :
        List.of(
            users.saveAndFlush(inactive),
            users.saveAndFlush(
                new CadenceUser("no-membership-" + UUID.randomUUID(), UserRole.OWNER, Set.of())),
            users.saveAndFlush(
                new CadenceUser(
                    "wrong-role-" + UUID.randomUUID(), UserRole.VIEWER, Set.of(workspace))))) {
      var stale =
          UsernamePasswordAuthenticationToken.authenticated(
              new CadencePrincipal(
                  actor.getId(),
                  actor.getExternalSubject(),
                  UserRole.OWNER,
                  Set.of(workspace.getId())),
              null,
              List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
      mvc.perform(
              post("/api/v1/identities/{id}/approval", id)
                  .with(authentication(stale))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedVersion\":0}"))
          .andExpect(status().isForbidden())
          .andDo(OpenApiContractAssertions::verify);
    }
    mvc.perform(
            post("/api/v1/identities/{id}/approval", id)
                .with(authentication(auth(UserRole.OWNER, Set.of(workspace.getId()))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isForbidden());
    assertThat(identities.findById(id).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.DRAFT);
  }

  @Test
  void approvalReadsCommittedContentInFreshPersistenceContext() throws Exception {
    var id = create();
    SecurityContextHolder.getContext().setAuthentication(owner);
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              var outer = identities.findById(id).orElseThrow();
              outer.replaceContent(content("Uncommitted"));
              var approved = useCase.approve(id, Set.of(workspace.getId()), 0, actorId);
              assertThat(approved.content().sampleCaption()).isEqualTo("Creator caption");
              tx.setRollbackOnly();
            });
    assertThat(useCase.findById(id, Set.of(workspace.getId())).status())
        .isEqualTo(ApprovalStatus.APPROVED);
    assertThat(useCase.findById(id, Set.of(workspace.getId())).content().sampleCaption())
        .isEqualTo("Creator caption");
  }

  @Autowired
  protected com.cadence.application.services.BrandAnalysisTransactions analysisTransactions;

  protected UUID successfulAnalysis() {
    SecurityContextHolder.getContext().setAuthentication(owner);
    var snapshot = analysisTransactions.start(brand.getId(), Set.of(workspace.getId()));
    var c = content("Declared Māori \uD83D\uDE00 caption");
    var p = c.palette();
    var result =
        new com.cadence.application.ports.BrandAnalysisResult(
            new com.cadence.application.ports.BrandAnalysisResult.Palette(
                p.primary(), p.secondary(), p.accent(), p.neutral(), p.background()),
            c.fonts(),
            c.voiceUp(),
            c.voiceDown(),
            c.sampleCaption(),
            List.of(com.cadence.application.ports.BrandAnalysisResult.Warning.DECLARED_FIELDS_ONLY),
            "fixture",
            "model-v1",
            "prompt-v1");
    assertThat(
            analysisTransactions.complete(snapshot, Set.of(workspace.getId()), result).published())
        .isTrue();
    brand = brands.findById(brand.getId()).orElseThrow();
    return snapshot.command().analysisId();
  }

  protected org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      fromAnalysisRequest(UUID analysisId, long token) throws Exception {
    return post("/api/v1/brands/{id}/identities/from-analysis", brand.getId())
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            mapper.writeValueAsString(
                Map.of(
                    "analysisId",
                    analysisId,
                    "expectedVersion",
                    token,
                    "content",
                    content("Forged ignored"))));
  }

  @Test
  void copiesVerifiedAnalysisWithOriginalReceiptAndDoesNotInventBranchGeneration()
      throws Exception {
    var analysisId = successfulAnalysis();
    var response =
        mvc.perform(
                fromAnalysisRequest(analysisId, brand.getLockVersion()).with(authentication(owner)))
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.content.sampleCaption").value("Declared Māori \uD83D\uDE00 caption"))
            .andExpect(jsonPath("$.analysisProvenance.analysisId").value(analysisId.toString()))
            .andExpect(jsonPath("$.analysisProvenance.warnings").value("DECLARED_FIELDS_ONLY"))
            .andDo(OpenApiContractAssertions::verify)
            .andReturn()
            .getResponse();
    var id = UUID.fromString(mapper.readTree(response.getContentAsString()).path("id").asText());
    SecurityContextHolder.getContext().setAuthentication(owner);
    var original = useCase.findById(id, Set.of(workspace.getId()));
    var edited =
        useCase
            .update(id, Set.of(workspace.getId()), original.lockVersion(), content("Manual edit"))
            .details();
    assertThat(edited.analysisProvenance()).isEqualTo(original.analysisProvenance());
    assertThat(edited.content().checksum())
        .isNotEqualTo(original.analysisProvenance().initialOutputChecksum());
    var approved = useCase.approve(id, Set.of(workspace.getId()), edited.lockVersion(), actorId);
    var branch =
        useCase
            .update(id, Set.of(workspace.getId()), approved.lockVersion(), content("Branch"))
            .details();
    assertThat(branch.analysisProvenance()).isNull();
    assertThat(branch.revisedFromId()).isEqualTo(id);
    assertThat(useCase.findById(id, Set.of(workspace.getId())).analysisProvenance())
        .isEqualTo(original.analysisProvenance());
  }

  @Test
  void analysisCopyRejectsMissingForeignStaleAndArchivedSources() throws Exception {
    var analysisId = successfulAnalysis();
    mvc.perform(
            fromAnalysisRequest(UUID.randomUUID(), brand.getLockVersion())
                .with(authentication(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(fromAnalysisRequest(analysisId, 0).with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(
            fromAnalysisRequest(analysisId, brand.getLockVersion())
                .with(authentication(auth(UserRole.OWNER, Set.of(UUID.randomUUID())))))
        .andExpect(status().isNotFound());
    var other = brands.saveAndFlush(new Brand("Other", workspace));
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () ->
                useCase.createFromAnalysis(other.getId(), Set.of(workspace.getId()), analysisId, 0))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(fromAnalysisRequest(analysisId, brand.getLockVersion()).with(authentication(owner)))
        .andExpect(status().isConflict());
    assertThat(
            identities.findAllByBrandId(
                brand.getId(), org.springframework.data.domain.PageRequest.of(0, 10)))
        .isEmpty();
  }

  @Test
  void analysisCopyDeniesAnonymousRolesAndDirectScopeForgery() throws Exception {
    var analysisId = successfulAnalysis();
    SecurityContextHolder.clearContext();
    mvc.perform(fromAnalysisRequest(analysisId, brand.getLockVersion()))
        .andExpect(status().isUnauthorized());
    for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER)) {
      var denied = auth(role, Set.of(workspace.getId()));
      mvc.perform(
              fromAnalysisRequest(analysisId, brand.getLockVersion()).with(authentication(denied)))
          .andExpect(status().isForbidden());
      SecurityContextHolder.getContext().setAuthentication(denied);
      assertThatThrownBy(
              () ->
                  useCase.createFromAnalysis(
                      brand.getId(), Set.of(workspace.getId()), analysisId, brand.getLockVersion()))
          .isInstanceOf(AccessDeniedException.class);
    }
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () ->
                useCase.createFromAnalysis(
                    brand.getId(),
                    Set.of(workspace.getId(), UUID.randomUUID()),
                    analysisId,
                    brand.getLockVersion()))
        .isInstanceOf(AccessDeniedException.class);
    for (var body :
        List.of(
            "{}",
            "{\"analysisId\":\"bad\",\"expectedVersion\":1}",
            "{\"analysisId\":\"" + analysisId + "\",\"expectedVersion\":-1}"))
      mvc.perform(
              post("/api/v1/brands/{id}/identities/from-analysis", brand.getId())
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
  }

  @Test
  void analysisCopyRejectsRunningAndSupersededDeclaredSources() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(owner);
    var running = analysisTransactions.start(brand.getId(), Set.of(workspace.getId()));
    mvc.perform(fromAnalysisRequest(running.command().analysisId(), 0).with(authentication(owner)))
        .andExpect(status().isConflict());
    SecurityContextHolder.getContext().setAuthentication(owner);
    analysisTransactions.fail(running, Set.of(workspace.getId()));
    mvc.perform(fromAnalysisRequest(running.command().analysisId(), 0).with(authentication(owner)))
        .andExpect(status().isConflict());
    var analysisId = successfulAnalysis();
    workspace.archive();
    workspaces.saveAndFlush(workspace);
    mvc.perform(fromAnalysisRequest(analysisId, brand.getLockVersion()).with(authentication(owner)))
        .andExpect(status().isConflict());
  }

  @Test
  void serverSelectsQualifiedCurrentReceiptWithoutClientKnowingInternalId() throws Exception {
    mvc.perform(
            post("/api/v1/brands/{id}/identities/from-analysis", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isConflict());
    var analysisId = successfulAnalysis();
    mvc.perform(
            post("/api/v1/brands/{id}/identities/from-analysis", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(Map.of("expectedVersion", brand.getLockVersion()))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.analysisProvenance.analysisId").value(analysisId.toString()))
        .andDo(OpenApiContractAssertions::verify);
  }

  @BeforeEach
  void identityFixture() {
    workspace = workspaces.saveAndFlush(new Workspace("Identity studio", "Creator"));
    var legacy = new Brand("Brand", workspace);
    legacy.updateSignature(
        new BrandSignature(
            new ColorPalette("#112233", "#445566", "#778899", "#AABBCC", "#DDEEFF"),
            List.of("Legacy font"),
            List.of("Legacy voice"),
            List.of("Legacy down"),
            "Legacy caption"));
    brand = brands.saveAndFlush(legacy);
    var actor =
        users.saveAndFlush(
            new CadenceUser("identity-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    actorId = actor.getId();
    owner =
        UsernamePasswordAuthenticationToken.authenticated(
            new CadencePrincipal(
                actorId, actor.getExternalSubject(), UserRole.OWNER, Set.of(workspace.getId())),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  protected Authentication auth(UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(UUID.randomUUID(), "identity-test", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  protected BrandIdentityContent content(String caption) {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        List.of("Inter"),
        List.of("Clear"),
        List.of("Spam"),
        caption);
  }

  protected String body() throws Exception {
    return mapper.writeValueAsString(Map.of("content", content("Creator caption")));
  }

  protected UUID create() throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/brands/{brandId}/identities", brand.getId())
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andDo(OpenApiContractAssertions::verify)
            .andReturn()
            .getResponse();
    var id = UUID.fromString(mapper.readTree(response.getContentAsString()).path("id").asText());
    assertThat(response.getHeader("Location")).isEqualTo("/api/v1/identities/" + id);
    return id;
  }

  @Test
  void createsReadsListsAndEditsIndependentDraftWithoutChangingLegacySignature() throws Exception {
    var first = create();
    var second = create();
    mvc.perform(get("/api/v1/identities/{identityId}", first).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lockVersion").value(0))
        .andExpect(jsonPath("$.workspaceId").value(workspace.getId().toString()))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/brands/{brandId}/identities", brand.getId())
                .with(authentication(owner))
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(second.toString()))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            patch("/api/v1/identities/{identityId}", first)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of("expectedVersion", 0, "content", content("Edited caption")))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lockVersion").value(1))
        .andExpect(jsonPath("$.content.sampleCaption").value("Edited caption"))
        .andDo(OpenApiContractAssertions::verify);
    var legacy = brands.findById(brand.getId()).orElseThrow().getSignature();
    assertThat(legacy.getSampleCaption()).isEqualTo("Legacy caption");
    assertThat(legacy.getPalette().getPrimary()).isEqualTo("#112233");
    SecurityContextHolder.getContext().setAuthentication(owner);
    var snapshot = useCase.findById(first, Set.of(workspace.getId()));
    assertThatThrownBy(() -> snapshot.content().fonts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(useCase.findById(second, Set.of(workspace.getId())).content().sampleCaption())
        .isEqualTo("Creator caption");
  }

  @Test
  void rejectsStaleUpdateAndMissingOrInvalidRevisionToken() throws Exception {
    var id = create();
    var valid =
        mapper.writeValueAsString(Map.of("expectedVersion", 0, "content", content("Edited")));
    mvc.perform(
            patch("/api/v1/identities/{identityId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/identities/{identityId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(valid))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    for (var invalid :
        List.of(body(), "{}", valid.replace("\"expectedVersion\":0", "\"expectedVersion\":-1")))
      mvc.perform(
              patch("/api/v1/identities/{identityId}", id)
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest())
          .andDo(OpenApiContractAssertions::verify);
  }

  @Test
  void rejectsMalformedUnboundedAndDuplicateContent() throws Exception {
    for (var invalid :
        List.of(
            body().replace("#000000", "red"),
            body().replace("[\"Inter\"]", "[]"),
            body().replace("[\"Inter\"]", "[\"Inter\",\"inter\"]"),
            body().replace("Creator caption", " "),
            body().replace("Creator caption", "X".repeat(1001)),
            body().replace("Inter", "Bad\\nFont"),
            "{\"content\":null}",
            "{\"content\":{\"palette\":null}}"))
      mvc.perform(
              post("/api/v1/brands/{brandId}/identities", brand.getId())
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(invalid))
          .andExpect(status().isBadRequest())
          .andDo(OpenApiContractAssertions::verify);
    assertThat(
            identities
                .findAllByBrandId(
                    brand.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements())
        .isZero();
  }

  @Test
  void clientCannotAssignApprovedStatusVersionOrTenant() throws Exception {
    var json = mapper.readTree(body());
    ((com.fasterxml.jackson.databind.node.ObjectNode) json)
        .put("status", "APPROVED")
        .put("version", 99)
        .put("brandId", UUID.randomUUID().toString())
        .put("approvedBy", UUID.randomUUID().toString());
    mvc.perform(
            post("/api/v1/brands/{brandId}/identities", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.toString()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.brandId").value(brand.getId().toString()));
  }

  @Test
  void rejectsAnonymousAndReservedRolesAtHttpAndDirectBeanBoundaries() throws Exception {
    var id = create();
    for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER)) {
      var denied = auth(role, Set.of(workspace.getId()));
      mvc.perform(
              post("/api/v1/brands/{brandId}/identities", brand.getId())
                  .with(authentication(denied))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/identities/{identityId}", id).with(authentication(denied)))
          .andExpect(status().isForbidden());
      mvc.perform(
              get("/api/v1/brands/{brandId}/identities", brand.getId())
                  .with(authentication(denied)))
          .andExpect(status().isForbidden());
      mvc.perform(
              patch("/api/v1/identities/{identityId}", id)
                  .with(authentication(denied))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          Map.of("expectedVersion", 0, "content", content("Denied")))))
          .andExpect(status().isForbidden());
      SecurityContextHolder.getContext().setAuthentication(denied);
      assertThatThrownBy(
              () -> useCase.create(brand.getId(), Set.of(workspace.getId()), content("Denied")))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> useCase.findById(id, Set.of(workspace.getId())))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(
              () ->
                  useCase.findAll(
                      brand.getId(),
                      Set.of(workspace.getId()),
                      org.springframework.data.domain.PageRequest.of(0, 10)))
          .isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> useCase.update(id, Set.of(workspace.getId()), 0, content("Denied")))
          .isInstanceOf(AccessDeniedException.class);
    }
    SecurityContextHolder.clearContext();
    mvc.perform(
            post("/api/v1/brands/{brandId}/identities", brand.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/identities/{identityId}", id)).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/brands/{brandId}/identities", brand.getId()))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            patch("/api/v1/identities/{identityId}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void hidesForeignAndMissingResourcesAndRejectsMalformedIds() throws Exception {
    var id = create();
    var foreign = auth(UserRole.OWNER, Set.of(UUID.randomUUID()));
    for (var unknown : List.of(id, UUID.randomUUID())) {
      mvc.perform(get("/api/v1/identities/{identityId}", unknown).with(authentication(foreign)))
          .andExpect(status().isNotFound())
          .andDo(OpenApiContractAssertions::verify);
      mvc.perform(
              patch("/api/v1/identities/{identityId}", unknown)
                  .with(authentication(foreign))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          Map.of("expectedVersion", 0, "content", content("Denied")))))
          .andExpect(status().isNotFound());
    }
    mvc.perform(
            post("/api/v1/brands/{brandId}/identities", brand.getId())
                .with(authentication(foreign))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/brands/{brandId}/identities", brand.getId()).with(authentication(foreign)))
        .andExpect(status().isNotFound());
    mvc.perform(get("/api/v1/identities/not-a-uuid").with(authentication(owner)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsUnboundedPaginationAndKeepsArchivedHistoryReadable() throws Exception {
    var id = create();
    for (var size : List.of("0", "101"))
      mvc.perform(
              get("/api/v1/brands/{brandId}/identities", brand.getId())
                  .with(authentication(owner))
                  .param("size", size))
          .andExpect(status().isBadRequest());
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(get("/api/v1/identities/{identityId}", id).with(authentication(owner)))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/brands/{brandId}/identities", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body()))
        .andExpect(status().isConflict());
    mvc.perform(
            patch("/api/v1/identities/{identityId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    mapper.writeValueAsString(
                        Map.of("expectedVersion", 0, "content", content("Denied")))))
        .andExpect(status().isConflict());
  }
}
