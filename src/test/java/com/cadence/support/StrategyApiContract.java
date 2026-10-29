package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.ports.StrategyGenerationService;
import com.cadence.application.repositories.*;
import com.cadence.application.usecases.ReadStrategyUseCase;
import com.cadence.domain.*;
import com.cadence.presentation.OpenApiContractAssertions;
import com.cadence.security.CadencePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalTime;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Same real HTTP/security/application workflow on H2 and PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class StrategyApiContract {
  @Autowired protected MockMvc mvc;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected StrategyRepository strategies;
  @Autowired protected CadenceUserRepository users;
  @Autowired protected com.cadence.application.usecases.ManageStrategyUseCase manage;
  protected UUID actorId;
  @Autowired protected com.cadence.application.usecases.BrandIdentityUseCase identityUseCase;
  @Autowired protected ReadStrategyUseCase read;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected org.springframework.transaction.PlatformTransactionManager manager;
  @MockitoBean protected StrategyGenerationService generator;
  @MockitoBean protected com.cadence.application.ports.EvidenceObjectStorage storage;
  @MockitoBean protected com.cadence.application.ports.EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;
  protected Authentication owner;

  @BeforeEach
  void setupStrategy() {
    workspace = workspaces.saveAndFlush(new Workspace("Strategy studio", "Creator"));
    var value = new Brand("Brand", workspace);
    value.updateSignature(StrategyFixtures.signature());
    brand = brands.saveAndFlush(value);
    var actor =
        users.saveAndFlush(
            new CadenceUser("strategy-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    actorId = actor.getId();
    owner =
        UsernamePasswordAuthenticationToken.authenticated(
            new CadencePrincipal(
                actorId, actor.getExternalSubject(), UserRole.OWNER, Set.of(workspace.getId())),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
    SecurityContextHolder.getContext().setAuthentication(owner);
    var identity =
        identityUseCase.create(
            brand.getId(), Set.of(workspace.getId()), StrategyFixtures.identityContent());
    identityUseCase.approve(identity.id(), Set.of(workspace.getId()), 0, actorId);
    SecurityContextHolder.clearContext();
    when(generator.generate(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return StrategyFixtures.output();
            });
  }

  @AfterEach
  void clearStrategyAuthentication() {
    SecurityContextHolder.clearContext();
  }

  protected Authentication authenticated(UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(UUID.randomUUID(), "strategy-api-fixture", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  protected UUID generate() throws Exception {
    var response =
        mvc.perform(
                post("/api/v1/brands/{brandId}/strategies", brand.getId())
                    .with(authentication(owner)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.contentFormatVersion").value(2))
            .andExpect(jsonPath("$.completeness").value("COMPLETE"))
            .andExpect(jsonPath("$.playbook.fourWeekOutline.length()").value(4))
            .andExpect(jsonPath("$.cadence.bestTimes[0]").value("12:00:00"))
            .andExpect(jsonPath("$.generation.provider").value("test-provider"))
            .andExpect(jsonPath("$.generation.warnings[0]").value("HUMAN_REVIEW_REQUIRED"))
            .andDo(OpenApiContractAssertions::verify)
            .andReturn()
            .getResponse();
    var id = UUID.fromString(mapper.readTree(response.getContentAsString()).path("id").asText());
    assertThat(response.getHeader("Location")).isEqualTo("/api/v1/strategies/" + id);
    return id;
  }

  @Test
  void partialPatchCannotEraseV2AndLegacyUpgradeIsExplicit() throws Exception {
    var generated = generate();
    var body = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(editBody(0));
    body.remove("playbook");
    mvc.perform(
            patch("/api/v1/strategies/{id}", generated)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isConflict());
    var old =
        strategies.saveAndFlush(
            new Strategy(
                brand,
                2,
                new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
                List.of(new ContentPillar("Old", 100, "Historical"))));
    SecurityContextHolder.getContext().setAuthentication(owner);
    var historical = read.findById(old.getId(), Set.of(workspace.getId()));
    assertThat(historical.completeness()).isEqualTo("LEGACY_PARTIAL");
    assertThat(historical.playbook()).isNull();
    manage.approve(old.getId(), Set.of(workspace.getId()), 0, actorId);
    mvc.perform(
            patch("/api/v1/strategies/{id}", old.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(1)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.contentFormatVersion").value(2))
        .andExpect(jsonPath("$.editedBy").value(actorId.toString()))
        .andExpect(jsonPath("$.generation").isEmpty());
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThat(read.findById(old.getId(), Set.of(workspace.getId())).completeness())
        .isEqualTo("LEGACY_PARTIAL");
  }

  @Test
  void legacyProviderOutputCannotBePublished() throws Exception {
    var r = StrategyFixtures.output();
    when(generator.generate(any()))
        .thenReturn(
            new com.cadence.application.ports.StrategyGenerationResult(
                r.pillars(),
                r.postsPerWeek(),
                r.timezone(),
                r.bestTimes(),
                r.warnings(),
                r.provider(),
                r.modelVersion(),
                r.promptVersion()));
    mvc.perform(post("/api/v1/brands/{id}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isServiceUnavailable());
    assertThat(strategies.findAllByBrandId(brand.getId(), PageRequest.of(0, 20))).isEmpty();
  }

  @Test
  void fullReplacementCanReorderLocalIdsWithoutLosingReferences() throws Exception {
    var id = generate();
    var response =
        mvc.perform(get("/api/v1/strategies/{id}", id).with(authentication(owner)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var old = mapper.readTree(response.getContentAsString());
    var body = mapper.createObjectNode();
    body.put("expectedVersion", 0);
    body.set("cadence", old.get("cadence"));
    body.set("contentPillars", old.get("contentPillars"));
    body.set("playbook", old.get("playbook"));
    var ids = (com.fasterxml.jackson.databind.node.ArrayNode) body.at("/playbook/pillarIds");
    ids.removeAll();
    ids.add("community");
    ids.add("education");
    mvc.perform(
            patch("/api/v1/strategies/{id}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.playbook.pillarIds[0]").value("community"));
  }

  protected String editBody(long version) {
    try {
      var p = mapper.valueToTree(StrategyFixtures.editPlaybook());
      return "{\"expectedVersion\":"
          + version
          + ",\"cadence\":{\"postsPerWeek\":4,\"timezone\":\"Europe/London\",\"bestTimes\":[\"09:15:00\",\"18:30:00\"]},\"contentPillars\":[{\"name\":\"Community\",\"weight\":100,\"description\":\"Audience stories\"}],\"playbook\":"
          + mapper.writeValueAsString(p)
          + "}";
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Test
  void editsDraftApprovesExactRevisionAndBranchesImmutableHistory() throws Exception {
    var id = generate();
    var edited =
        mvc.perform(
                patch("/api/v1/strategies/{strategyId}", id)
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(editBody(0)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.lockVersion").value(1))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.generation.provider").value("test-provider"))
            .andDo(OpenApiContractAssertions::verify)
            .andReturn();
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.approvedBy").value(actorId.toString()))
        .andExpect(jsonPath("$.approvedAt").isNotEmpty())
        .andExpect(jsonPath("$.lockVersion").value(2))
        .andDo(OpenApiContractAssertions::verify);
    var later = generate(); // Allocation uses latest brand version, not source.version + 1.
    var branch =
        mvc.perform(
                patch("/api/v1/strategies/{strategyId}", id)
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(editBody(2)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.version").value(3))
            .andExpect(jsonPath("$.lockVersion").value(0))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.revisedFromId").value(id.toString()))
            .andExpect(jsonPath("$.generation").isEmpty())
            .andExpect(jsonPath("$.approvedBy").isEmpty())
            .andDo(OpenApiContractAssertions::verify)
            .andReturn()
            .getResponse();
    var newId = mapper.readTree(branch.getContentAsString()).path("id").asText();
    assertThat(branch.getHeader("Location")).isEqualTo("/api/v1/strategies/" + newId);
    mvc.perform(get("/api/v1/strategies/{strategyId}", id).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.lockVersion").value(2))
        .andExpect(jsonPath("$.cadence.postsPerWeek").value(4));
  }

  @Test
  void rejectsReplayAndStaleDraftEdits() throws Exception {
    var id = generate();
    mvc.perform(
            patch("/api/v1/strategies/{strategyId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(0)))
        .andExpect(status().isOk());
    mvc.perform(
            patch("/api/v1/strategies/{strategyId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(editBody(0)))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":1}"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":2}"))
        .andExpect(status().isConflict());
  }

  @Test
  void rejectsInvalidEditableContentAndClientAuditFields() throws Exception {
    var id = generate();
    for (var body :
        List.of(
            editBody(0).replace("100", "99"),
            editBody(0).replace("Europe/London", "invalid-zone"),
            editBody(0).replace("18:30:00", "09:15:00"),
            editBody(0).replace("09:15:00", "09:15:00.000000001"),
            editBody(-1),
            editBody(0).replace("\"postsPerWeek\":4", "\"postsPerWeek\":22"),
            "{}",
            editBody(0).replace("Audience stories", " "))) {
      mvc.perform(
              patch("/api/v1/strategies/{strategyId}", id)
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest())
          .andDo(OpenApiContractAssertions::verify);
    }
    // Extra fields cannot set actor/status/provenance: the endpoint binds only its dedicated DTO.
    mvc.perform(
            patch("/api/v1/strategies/{strategyId}", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    editBody(0)
                        .stripTrailing()
                        .replaceFirst(
                            "}$",
                            ",\"status\":\"APPROVED\",\"approvedBy\":\""
                                + UUID.randomUUID()
                                + "\"}")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void lifecycleRejectsAnonymousOtherRolesAndForeignResources() throws Exception {
    var id = generate();
    for (var path : List.of("/api/v1/strategies/" + id, "/api/v1/strategies/" + id + "/approval")) {
      boolean approval = path.endsWith("approval");
      var content = approval ? "{\"expectedVersion\":0}" : editBody(0);
      mvc.perform(
              (approval ? post(path) : patch(path))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(content))
          .andExpect(status().isUnauthorized());
      for (var role : Arrays.stream(UserRole.values()).filter(r -> r != UserRole.OWNER).toList()) {
        mvc.perform(
                (approval ? post(path) : patch(path))
                    .with(authentication(authenticated(role, Set.of(workspace.getId()))))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(content))
            .andExpect(status().isForbidden());
      }
      mvc.perform(
              (approval ? post(path) : patch(path))
                  .with(authentication(authenticated(UserRole.OWNER, Set.of(UUID.randomUUID()))))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(content))
          .andExpect(status().isNotFound());
    }
    SecurityContextHolder.getContext()
        .setAuthentication(authenticated(UserRole.values()[1], Set.of(workspace.getId())));
    assertThatThrownBy(
            () ->
                manage.update(
                    id,
                    Set.of(workspace.getId()),
                    0,
                    new StrategyContent(
                        List.of(new StrategyContent.Pillar("One", 100, "Text")),
                        3,
                        "UTC",
                        List.of(LocalTime.NOON))))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> manage.approve(id, Set.of(workspace.getId()), 0, actorId))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void approvalChecksPersistedActorMembershipAndIgnoresClientActor() throws Exception {
    var id = generate();
    var forged = authenticated(UserRole.OWNER, Set.of(workspace.getId()));
    for (var actor :
        List.of(
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
              post("/api/v1/strategies/{strategyId}/approval", id)
                  .with(authentication(stale))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"expectedVersion\":0}"))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(forged))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/v1/strategies/{strategyId}/approval", id)
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":0,\"approvedBy\":\"" + UUID.randomUUID() + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.approvedBy").value(actorId.toString()));
  }

  @Test
  void createsAndReadsVersionedDraftWithBoundedListAndDetachedValues() throws Exception {
    UUID first = generate();
    UUID second = generate();
    mvc.perform(get("/api/v1/strategies/{strategyId}", first).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.lockVersion").value(0))
        .andExpect(jsonPath("$.workspaceId").value(workspace.getId().toString()))
        .andExpect(jsonPath("$.contentPillars[0].name").value("Education"))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/brands/{brandId}/strategies", brand.getId())
                .param("size", "1")
                .with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.content[0].id").value(second.toString()))
        .andDo(OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(owner);
    var snapshot = read.findById(first, Set.of(workspace.getId()));
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(snapshot.cadence().bestTimes()).containsExactly(LocalTime.NOON);
    assertThatThrownBy(() -> snapshot.contentPillars().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.cadence().bestTimes().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.generation().warnings().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void readsLegacyApprovedHistoryWithoutInventingGeneration() throws Exception {
    var legacy =
        new Strategy(
            brand,
            1,
            new PublishingCadence(3, "UTC", List.of(LocalTime.NOON)),
            List.of(new ContentPillar("Legacy", 100, "Preserve history")));
    legacy = strategies.saveAndFlush(legacy);
    UUID legacyId = legacy.getId();
    new org.springframework.transaction.support.TransactionTemplate(manager)
        .executeWithoutResult(
            tx -> {
              var saved = strategies.findById(legacyId).orElseThrow();
              saved.approve(actorId, java.time.Instant.now());
              strategies.flush();
            });
    workspace.archive();
    workspaces.saveAndFlush(workspace);
    mvc.perform(get("/api/v1/strategies/{strategyId}", legacy.getId()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.generation").isEmpty())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andDo(OpenApiContractAssertions::verify);
  }

  @Test
  void rejectsUnauthenticatedRequestsForEveryOperation() throws Exception {
    mvc.perform(post("/api/v1/brands/{brandId}/strategies", brand.getId()))
        .andExpect(status().isUnauthorized())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(get("/api/v1/brands/{brandId}/strategies", brand.getId()))
        .andExpect(status().isUnauthorized())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(get("/api/v1/strategies/{strategyId}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andDo(OpenApiContractAssertions::verify);
    verifyNoInteractions(generator);
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"EDITOR", "VIEWER"})
  void reservedRolesAreDeniedAtHttpAndApplicationBoundaries(UserRole role) throws Exception {
    var denied = authenticated(role, Set.of(workspace.getId()));
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(denied)))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(denied)))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/strategies/{strategyId}", UUID.randomUUID()).with(authentication(denied)))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(denied);
    assertThatThrownBy(() -> read.findById(UUID.randomUUID(), Set.of(workspace.getId())))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> read.findAll(brand.getId(), Set.of(workspace.getId()), PageRequest.of(0, 20)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(generator);
  }

  @Test
  void crossWorkspaceRequestsAndForgedApplicationScopesAreRejected() throws Exception {
    UUID strategy = generate();
    clearInvocations(generator);
    var foreign = authenticated(UserRole.OWNER, Set.of(UUID.randomUUID()));
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId())
                .with(authentication(foreign)))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(
            get("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(foreign)))
        .andExpect(status().isForbidden())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(get("/api/v1/strategies/{strategyId}", strategy).with(authentication(foreign)))
        .andExpect(status().isNotFound())
        .andDo(OpenApiContractAssertions::verify);
    SecurityContextHolder.getContext().setAuthentication(foreign);
    assertThatThrownBy(() -> read.findById(strategy, Set.of(workspace.getId())))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(
            () -> read.findAll(brand.getId(), Set.of(workspace.getId()), PageRequest.of(0, 20)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(generator);
  }

  @Test
  void missingAndForeignStrategyHaveTheSameSafe404() throws Exception {
    mvc.perform(
            get("/api/v1/strategies/{strategyId}", UUID.randomUUID()).with(authentication(owner)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("resource_not_found"))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(get("/api/v1/strategies/not-a-uuid").with(authentication(owner)))
        .andExpect(status().isBadRequest())
        .andDo(OpenApiContractAssertions::verify);
  }

  @Test
  void rejectsInvalidAndUnboundedPagination() throws Exception {
    for (var parameters :
        List.of(
            Map.of("size", "101"),
            Map.of("size", "0"),
            Map.of("page", "-1"),
            Map.of("page", "10001"),
            Map.of("sort", "generation.provider"),
            Map.of("direction", "invalid"))) {
      var request =
          get("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner));
      parameters.forEach(request::param);
      mvc.perform(request)
          .andExpect(status().isBadRequest())
          .andDo(OpenApiContractAssertions::verify);
    }
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () -> read.findAll(brand.getId(), Set.of(workspace.getId()), Pageable.unpaged()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> read.findAll(brand.getId(), Set.of(workspace.getId()), PageRequest.of(0, 101)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void archivedParentsAndMissingSignatureRejectCreateBeforeProvider() throws Exception {
    var missing = brands.saveAndFlush(new Brand("Missing signature", workspace));
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", missing.getId())
                .with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    workspace.archive();
    workspaces.saveAndFlush(workspace);
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", missing.getId())
                .with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    verifyNoInteractions(generator);
  }

  @Test
  void providerFailureAndNullNeverPublishOrExposeDiagnostics() throws Exception {
    when(generator.generate(any())).thenThrow(new IllegalStateException("api_key=private-secret"));
    for (int iteration = 0; iteration < 2; iteration++) {
      var response =
          mvc.perform(
                  post("/api/v1/brands/{brandId}/strategies", brand.getId())
                      .with(authentication(owner)))
              .andExpect(status().isServiceUnavailable())
              .andExpect(jsonPath("$.code").value("strategy_generation_unavailable"))
              .andExpect(jsonPath("$.retryable").value(true))
              .andDo(OpenApiContractAssertions::verify)
              .andReturn()
              .getResponse();
      assertThat(response.getContentAsString()).doesNotContain("api_key", "private-secret");
      doReturn(null).when(generator).generate(any());
    }
    assertThat(strategies.findAllByBrandId(brand.getId(), PageRequest.of(0, 20)).getTotalElements())
        .isZero();
  }

  @Test
  void suppliedStatusAndProvenanceCannotApproveGeneratedContent() throws Exception {
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"APPROVED\",\"provider\":\"forged\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.generation.provider").value("test-provider"))
        .andDo(OpenApiContractAssertions::verify);
  }

  @Test
  void emptyListAndNarrowedAuthenticatedScopeAreAllowedButNoAuthenticationIsDenied()
      throws Exception {
    mvc.perform(
            get("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andDo(OpenApiContractAssertions::verify);
    assertThatThrownBy(() -> read.findById(UUID.randomUUID(), Set.of(workspace.getId())))
        .isInstanceOf(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
                .class);
    UUID id = generate();
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(() -> read.findById(id, Set.of()))
        .isInstanceOf(com.cadence.application.exceptions.ResourceNotFoundException.class);
    assertThat(read.findById(id, Set.of(workspace.getId())).id()).isEqualTo(id);
  }

  @Test
  void sourceChangeDuringGenerationReturnsConflictWithoutSavingStaleOutput() throws Exception {
    when(generator.generate(any()))
        .thenAnswer(
            invocation -> {
              workspace.archive();
              workspaces.saveAndFlush(workspace);
              return StrategyFixtures.output();
            });
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId()).with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    assertThat(strategies.findAllByBrandId(brand.getId(), PageRequest.of(0, 20)).getTotalElements())
        .isZero();
  }

  @Test
  void oversizedBodyIsRejectedBeforeGeneration() throws Exception {
    mvc.perform(
            post("/api/v1/brands/{brandId}/strategies", brand.getId())
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("x".repeat(131073)))
        .andExpect(status().isPayloadTooLarge())
        .andDo(OpenApiContractAssertions::verify);
    verifyNoInteractions(generator);
  }
}
