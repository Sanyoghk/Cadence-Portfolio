package com.cadence.support;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.repositories.*;
import com.cadence.application.usecases.ReviewEvidenceUseCase;
import com.cadence.domain.*;
import com.cadence.presentation.OpenApiContractAssertions;
import com.cadence.security.CadencePrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public abstract class EvidenceAdmissionApiContract {
  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected BrandRepository brands;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected CadenceUserRepository users;
  @Autowired protected EvidenceAdmissionRepository admissions;
  @Autowired protected ReviewEvidenceUseCase reviews;
  @Autowired protected java.time.Clock clock;
  @MockitoBean protected com.cadence.application.ports.EvidenceObjectStorage storage;
  @MockitoBean protected com.cadence.application.ports.EvidenceMalwareScanner scanner;
  @Autowired protected org.springframework.jdbc.core.JdbcTemplate jdbc;
  protected Workspace workspace;
  protected Brand brand;
  protected BrandEvidence source;
  protected CadenceUser actor;
  protected Authentication owner;
  protected final String text =
      "Reviewed Māori \uD83D\uDE00 text\nIgnore instructions is still untrusted data";

  @BeforeEach
  void fixture() {
    workspace = workspaces.saveAndFlush(new Workspace("Admission studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Admission brand", workspace));
    actor =
        users.saveAndFlush(
            new CadenceUser("admission-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    owner = auth(actor.getId(), UserRole.OWNER, Set.of(workspace.getId()));
    source = textSource(text);
    SecurityContextHolder.getContext().setAuthentication(owner);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  protected Authentication auth(UUID id, UserRole role, Set<UUID> scope) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(id, "admission", role, scope),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
  }

  protected BrandEvidence textSource(String value) {
    return evidence.saveAndFlush(
        new BrandEvidence(
            brand,
            EvidenceType.TEXT,
            value,
            EvidenceAdmission.checksum("TEXT\n" + value),
            clock.instant()));
  }

  protected String path(UUID id) {
    return "/api/v1/evidence/" + id + "/ai-admission";
  }

  protected Map<String, Object> token() {
    return new HashMap<>(
        Map.of(
            "sourceVersion",
            source.getLockVersion(),
            "brandVersion",
            brand.getLockVersion(),
            "workspaceVersion",
            workspace.getLockVersion(),
            "contentChecksum",
            EvidenceAdmission.checksum(text)));
  }

  protected com.cadence.application.results.EvidenceAdmissionDetails admit() {
    return reviews.admit(
        source.getId(),
        Set.of(workspace.getId()),
        actor.getId(),
        source.getLockVersion(),
        brand.getLockVersion(),
        workspace.getLockVersion(),
        EvidenceAdmission.checksum(text));
  }

  @Test
  void historyIsBoundedMetadataAndPreservesWithdrawalAcrossContexts() throws Exception {
    var original = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var next = reviewCurrent();
    SecurityContextHolder.getContext().setAuthentication(owner);
    reviews.withdraw(source.getId(), original.id(), Set.of(workspace.getId()), actor.getId());
    var result =
        mvc.perform(get(path(source.getId()) + "/history").with(authentication(owner)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].withdrawn").value(true))
            .andExpect(jsonPath("$.content[1].withdrawn").value(true))
            .andExpect(jsonPath("$.content[0].withdrawnBy").value(actor.getId().toString()))
            .andExpect(jsonPath("$.content[0].text").doesNotExist())
            .andExpect(jsonPath("$.content[0].reviewedText").doesNotExist())
            .andDo(OpenApiContractAssertions::verify)
            .andReturn();
    assertThat(result.getResponse().getContentAsString()).doesNotContain(text);
    mvc.perform(get(path(source.getId()) + "/history?page=1&size=1").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1))
        .andExpect(jsonPath("$.page").value(1))
        .andDo(OpenApiContractAssertions::verify);
    assertThat(admissions.findById(next.id()).orElseThrow().getReviewedText()).isEqualTo(text);
    jdbc.update("update brands set status='ARCHIVED' where id=?", brand.getId());
    mvc.perform(get(path(source.getId()) + "/history").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void historyChecksScopeActualActorAndPagination() throws Exception {
    admit();
    String url = path(source.getId()) + "/history";
    SecurityContextHolder.clearContext();
    mvc.perform(get(url)).andExpect(status().isUnauthorized());
    mvc.perform(
            get(url)
                .with(
                    authentication(
                        auth(actor.getId(), UserRole.EDITOR, Set.of(workspace.getId())))))
        .andExpect(status().isForbidden());
    mvc.perform(
            get(url)
                .with(
                    authentication(auth(actor.getId(), UserRole.OWNER, Set.of(UUID.randomUUID())))))
        .andExpect(status().isNotFound());
    for (String query : new String[] {"page=-1", "page=10001", "size=0", "size=21"})
      mvc.perform(get(url + "?" + query).with(authentication(owner)))
          .andExpect(status().isBadRequest());
    jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
    mvc.perform(get(url).with(authentication(owner))).andExpect(status().isForbidden());
  }

  @Test
  void withdrawalPreservesSnapshotAndPermanentlyRejectsReuse() throws Exception {
    var a = admit();
    String root = path(source.getId()) + "/" + a.id();
    mvc.perform(get(root + "/snapshot").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(text))
        .andExpect(jsonPath("$.admission.id").value(a.id().toString()))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(post(root + "/withdrawals").with(authentication(owner)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.admissionId").value(a.id().toString()))
        .andExpect(jsonPath("$.withdrawnBy").value(actor.getId().toString()))
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(get(root + "/snapshot").with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    mvc.perform(post(root + "/withdrawals").with(authentication(owner)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    assertThat(admissions.findById(a.id()).orElseThrow().getReviewedText()).isEqualTo(text);
    assertThat(evidence.findById(source.getId()).orElseThrow().getLockVersion())
        .isEqualTo(source.getLockVersion());
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
  }

  protected com.cadence.application.results.EvidenceAdmissionDetails reviewCurrent()
      throws Exception {
    var preview =
        mvc.perform(get(path(source.getId())).with(authentication(owner)))
            .andExpect(status().isOk())
            .andReturn();
    var tokens = mapper.readTree(preview.getResponse().getContentAsString());
    var body =
        Map.of(
            "sourceVersion",
            tokens.get("sourceVersion").longValue(),
            "brandVersion",
            tokens.get("brandVersion").longValue(),
            "workspaceVersion",
            tokens.get("workspaceVersion").longValue(),
            "contentChecksum",
            tokens.get("contentChecksum").asText());
    var response =
        mvc.perform(
                post(path(source.getId()))
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andDo(OpenApiContractAssertions::verify)
            .andReturn();
    return mapper.readValue(
        response.getResponse().getContentAsString(),
        com.cadence.application.results.EvidenceAdmissionDetails.class);
  }

  @Test
  void reReviewsNewParentContextsPreservingOldReceiptsAndRejectingReplay() throws Exception {
    var original = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
    var next = reviewCurrent();
    assertThat(next.id()).isNotEqualTo(original.id());
    assertThat(next.sourceVersion()).isEqualTo(original.sourceVersion());
    assertThat(next.brandVersion()).isEqualTo(1);
    assertThat(next.contentChecksum()).isEqualTo(original.contentChecksum());
    mvc.perform(
            get(path(source.getId()) + "/" + original.id() + "/snapshot")
                .with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(
            get(path(source.getId()) + "/" + next.id() + "/snapshot").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(text));
    var replay = token();
    replay.put("brandVersion", 1);
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(replay)))
        .andExpect(status().isConflict());
    jdbc.update("update workspaces set lock_version=lock_version+1 where id=?", workspace.getId());
    var finalReview = reviewCurrent();
    assertThat(finalReview.workspaceVersion()).isEqualTo(1);
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(3);
    var stored = admissions.findById(original.id()).orElseThrow();
    assertThat(stored.getBrandVersion()).isZero();
    assertThat(stored.getWorkspaceVersion()).isZero();
    assertThat(stored.getReviewedText()).isEqualTo(text);
    assertThat(stored.getReviewedAt()).isEqualTo(original.reviewedAt());
  }

  @Test
  void withdrawingOldContextRevokesNewContextAndCannotBeBypassedByRenameOrAnotherOwner()
      throws Exception {
    var original = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var next = reviewCurrent();
    mvc.perform(
            post(path(source.getId()) + "/" + original.id() + "/withdrawals")
                .with(authentication(owner)))
        .andExpect(status().isCreated());
    mvc.perform(
            get(path(source.getId()) + "/" + next.id() + "/snapshot").with(authentication(owner)))
        .andExpect(status().isConflict());
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    var tokens = token();
    tokens.put("brandVersion", 2);
    var other =
        users.saveAndFlush(
            new CadenceUser("other-owner-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    mvc.perform(
            post(path(source.getId()))
                .with(
                    authentication(auth(other.getId(), UserRole.OWNER, Set.of(workspace.getId()))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(tokens)))
        .andExpect(status().isConflict())
        .andDo(OpenApiContractAssertions::verify);
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(2);
  }

  @Test
  void changedSourceNeedsExactNewReviewAndDoesNotRewriteWithdrawnVersion() throws Exception {
    var old = admit();
    mvc.perform(
            post(path(source.getId()) + "/" + old.id() + "/withdrawals")
                .with(authentication(owner)))
        .andExpect(status().isCreated());
    String changed = "New source content";
    // Test a committed source revision; no public source-edit endpoint is introduced.
    jdbc.update(
        "update brand_evidence set source_reference=?,checksum=?,lock_version=lock_version+1 where id=?",
        changed,
        EvidenceAdmission.checksum("TEXT\n" + changed),
        source.getId());
    var next = reviewCurrent();
    assertThat(next.sourceVersion()).isEqualTo(1);
    assertThat(next.contentChecksum()).isEqualTo(EvidenceAdmission.checksum(changed));
    mvc.perform(
            get(path(source.getId()) + "/" + next.id() + "/snapshot").with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value(changed));
    assertThat(admissions.findById(old.id()).orElseThrow().getReviewedText()).isEqualTo(text);
  }

  @Test
  void snapshotRejectsChangedParentsButWithdrawalStillWorksAfterArchive() throws Exception {
    var a = admit();
    String root = path(source.getId()) + "/" + a.id();
    jdbc.update("update workspaces set lock_version=lock_version+1 where id=?", workspace.getId());
    mvc.perform(get(root + "/snapshot").with(authentication(owner)))
        .andExpect(status().isConflict());
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(get(root + "/snapshot").with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(post(root + "/withdrawals").with(authentication(owner)))
        .andExpect(status().isCreated());
  }

  @Test
  void newOperationsRejectAnonymousReservedRolesForeignAdmissionsAndRevokedMembership()
      throws Exception {
    var a = admit();
    String root = path(source.getId()) + "/" + a.id();
    SecurityContextHolder.clearContext();
    for (boolean withdrawal : List.of(false, true)) {
      var request = withdrawal ? post(root + "/withdrawals") : get(root + "/snapshot");
      mvc.perform(request)
          .andExpect(status().isUnauthorized())
          .andDo(OpenApiContractAssertions::verify);
      for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER))
        mvc.perform(
                (withdrawal ? post(root + "/withdrawals") : get(root + "/snapshot"))
                    .with(authentication(auth(actor.getId(), role, Set.of(workspace.getId())))))
            .andExpect(status().isForbidden());
      mvc.perform(
              (withdrawal ? post(root + "/withdrawals") : get(root + "/snapshot"))
                  .with(
                      authentication(
                          auth(actor.getId(), UserRole.OWNER, Set.of(UUID.randomUUID())))))
          .andExpect(status().isNotFound());
      String wrong = path(source.getId()) + "/" + UUID.randomUUID();
      mvc.perform(
              (withdrawal ? post(wrong + "/withdrawals") : get(wrong + "/snapshot"))
                  .with(authentication(owner)))
          .andExpect(status().isNotFound());
    }
    var otherSource = textSource("Other reviewed data");
    mvc.perform(
            get(path(otherSource.getId()) + "/" + a.id() + "/snapshot").with(authentication(owner)))
        .andExpect(status().isNotFound());
    mvc.perform(
            post(path(otherSource.getId()) + "/" + a.id() + "/withdrawals")
                .with(authentication(owner)))
        .andExpect(status().isNotFound());
    jdbc.update("delete from workspace_memberships where user_id=?", actor.getId());
    mvc.perform(get(root + "/snapshot").with(authentication(owner)))
        .andExpect(status().isForbidden());
    mvc.perform(post(root + "/withdrawals").with(authentication(owner)))
        .andExpect(status().isForbidden());
  }

  @Test
  void snapshotRejectsSourceMutationAndDirectCallsCannotForgeActorOrScope() {
    var a = admit();
    assertThatThrownBy(
            () ->
                reviews.withdraw(
                    source.getId(), a.id(), Set.of(workspace.getId()), UUID.randomUUID()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                reviews.snapshot(source.getId(), a.id(), Set.of(UUID.randomUUID()), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    jdbc.update("update brand_evidence set lock_version=lock_version+1 where id=?", source.getId());
    assertThatThrownBy(
            () ->
                reviews.snapshot(source.getId(), a.id(), Set.of(workspace.getId()), actor.getId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            reviews
                .withdraw(source.getId(), a.id(), Set.of(workspace.getId()), actor.getId())
                .admissionId())
        .isEqualTo(a.id());
  }

  @Test
  void reviewsExactStoredTextAndCreatesImmutableSnapshotWithoutChangingIngestOrClaimingGeneration()
      throws Exception {
    mvc.perform(get(path(source.getId())).with(authentication(owner)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.text").value(text))
        .andExpect(jsonPath("$.contentChecksum").value(EvidenceAdmission.checksum(text)))
        .andExpect(jsonPath("$.policyVersion").value(EvidenceAdmission.POLICY_VERSION))
        .andDo(OpenApiContractAssertions::verify);
    var request = token();
    request.put("reviewedBy", UUID.randomUUID());
    request.put("text", "Forged");
    request.put("reviewedAt", "2000-01-01T00:00:00Z");
    var response =
        mvc.perform(
                post(path(source.getId()))
                    .with(authentication(owner))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.reviewedBy").value(actor.getId().toString()))
            .andExpect(jsonPath("$.reviewedAt").isNotEmpty())
            .andExpect(jsonPath("$.text").doesNotExist())
            .andDo(OpenApiContractAssertions::verify)
            .andReturn();
    UUID id =
        UUID.fromString(
            mapper.readTree(response.getResponse().getContentAsString()).path("id").asText());
    assertThat(admissions.findById(id).orElseThrow().getReviewedText()).isEqualTo(text);
    assertThat(evidence.findById(source.getId()).orElseThrow().getStatus())
        .isEqualTo(EvidenceStatus.PENDING);
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
  }

  @Test
  void deniesAnonymousReservedRolesForeignResourcesAndForgedDirectCallScopeOrActor()
      throws Exception {
    SecurityContextHolder.clearContext();
    mvc.perform(get(path(source.getId()))).andExpect(status().isUnauthorized());
    mvc.perform(post(path(source.getId())).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized());
    for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER)) {
      var denied = auth(actor.getId(), role, Set.of(workspace.getId()));
      mvc.perform(get(path(source.getId())).with(authentication(denied)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(path(source.getId()))
                  .with(authentication(denied))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(token())))
          .andExpect(status().isForbidden());
      SecurityContextHolder.getContext().setAuthentication(denied);
      assertThatThrownBy(
              () -> reviews.preview(source.getId(), Set.of(workspace.getId()), actor.getId()))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    var foreign = auth(actor.getId(), UserRole.OWNER, Set.of(UUID.randomUUID()));
    for (var id : List.of(source.getId(), UUID.randomUUID())) {
      mvc.perform(get(path(id)).with(authentication(foreign))).andExpect(status().isNotFound());
      mvc.perform(
              post(path(id))
                  .with(authentication(foreign))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(token())))
          .andExpect(status().isNotFound());
    }
    SecurityContextHolder.getContext().setAuthentication(owner);
    assertThatThrownBy(
            () -> reviews.preview(source.getId(), Set.of(workspace.getId()), UUID.randomUUID()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                reviews.preview(
                    source.getId(), Set.of(workspace.getId(), UUID.randomUUID()), actor.getId()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
  }

  @Test
  void rejectsInvalidAndStaleVersionsChecksumsAndChangedParents() throws Exception {
    for (var field :
        List.of("sourceVersion", "brandVersion", "workspaceVersion", "contentChecksum")) {
      var stale = token();
      stale.put(field, field.equals("contentChecksum") ? "0".repeat(64) : 99);
      mvc.perform(
              post(path(source.getId()))
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(stale)))
          .andExpect(status().isConflict());
      var invalid = token();
      invalid.put(field, field.equals("contentChecksum") ? "invalid" : -1);
      mvc.perform(
              post(path(source.getId()))
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
    SecurityContextHolder.getContext().setAuthentication(owner);
    workspace.rename("New context");
    workspaces.saveAndFlush(workspace);
    assertThatThrownBy(
            () ->
                reviews.admit(
                    source.getId(),
                    Set.of(workspace.getId()),
                    actor.getId(),
                    0,
                    0,
                    0,
                    EvidenceAdmission.checksum(text)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(admissions.existsByEvidenceIdAndSourceVersion(source.getId(), 0)).isFalse();
  }

  @Test
  void rejectsUnsupportedSourcesMalformedStoredTextAndArchives() throws Exception {
    var url =
        evidence.saveAndFlush(
            new BrandEvidence(
                brand,
                EvidenceType.URL,
                "https://example.com",
                EvidenceAdmission.checksum("URL\nhttps://example.com"),
                Instant.now()));
    mvc.perform(get(path(url.getId())).with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path(url.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
    var malformed = textSource("A\u0001B");
    mvc.perform(get(path(malformed.getId())).with(authentication(owner)))
        .andExpect(status().isConflict());
    brand.archive();
    brands.saveAndFlush(brand);
    mvc.perform(get(path(source.getId())).with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
  }

  @Test
  void rechecksActualAccountRoleActivityAndMembershipDespiteStalePrincipal() throws Exception {
    for (var query :
        List.of(
            "update cadence_users set active=false,lock_version=lock_version+1 where id=?",
            "update cadence_users set active=true,role='EDITOR',lock_version=lock_version+1 where id=?",
            "update cadence_users set active=true,role='VIEWER',lock_version=lock_version+1 where id=?",
            "delete from workspace_memberships where user_id=?")) {
      if (query.startsWith("delete"))
        jdbc.update("update cadence_users set role='OWNER' where id=?", actor.getId());
      jdbc.update(query, actor.getId());
      mvc.perform(get(path(source.getId())).with(authentication(owner)))
          .andExpect(status().isForbidden());
      mvc.perform(
              post(path(source.getId()))
                  .with(authentication(owner))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(token())))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void archivedWorkspaceAndForgedDirectAdmissionAreDenied() throws Exception {
    assertThatThrownBy(
            () ->
                reviews.admit(
                    source.getId(),
                    Set.of(workspace.getId()),
                    UUID.randomUUID(),
                    0,
                    0,
                    0,
                    EvidenceAdmission.checksum(text)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThatThrownBy(
            () ->
                reviews.admit(
                    source.getId(),
                    Set.of(workspace.getId(), UUID.randomUUID()),
                    actor.getId(),
                    0,
                    0,
                    0,
                    EvidenceAdmission.checksum(text)))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    workspace.archive();
    workspaces.saveAndFlush(workspace);
    mvc.perform(get(path(source.getId())).with(authentication(owner)))
        .andExpect(status().isConflict());
    mvc.perform(
            post(path(source.getId()))
                .with(authentication(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(token())))
        .andExpect(status().isConflict());
    assertThat(admissions.existsByEvidenceIdAndSourceVersion(source.getId(), 0)).isFalse();
  }

  @Test
  void acceptsBoundedTextAndPreservesMarkupAsData() throws Exception {
    for (var value : List.of("x".repeat(20000), "<script>alert('untrusted')</script>\r\n\ttext")) {
      var item = textSource(value);
      var preview = reviews.preview(item.getId(), Set.of(workspace.getId()), actor.getId());
      assertThat(preview.text()).isEqualTo(value);
      var accepted =
          reviews.admit(
              item.getId(),
              Set.of(workspace.getId()),
              actor.getId(),
              0,
              0,
              0,
              preview.contentChecksum());
      assertThat(admissions.findById(accepted.id()).orElseThrow().getReviewedText())
          .isEqualTo(value);
    }
  }
}
