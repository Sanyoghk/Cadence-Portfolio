package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.*;
import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.StrategyBuildTransactions;
import com.cadence.application.usecases.BuildStrategyUseCase;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import com.cadence.support.StrategyFixtures;
import java.time.*;
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
class PostgreSqlStrategyBuildTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired StrategyRepository strategies;
  @Autowired CadenceUserRepository users;
  @Autowired BuildStrategyUseCase build;
  @Autowired StrategyBuildTransactions transactions;
  @Autowired JdbcTemplate jdbc;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
  @MockitoBean StrategyGenerationService generator;
  @MockitoBean java.time.Clock clock;
  @MockitoBean EvidenceObjectStorage storage;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean ApprovedSocialEvidenceSource social;
  @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
  @Autowired com.cadence.application.usecases.BrandIdentityUseCase identityUseCase;
  UUID actorId;
  UUID identityId;
  Workspace workspace;
  Brand brand;
  Set<UUID> scope;

  @BeforeEach
  void setup() {
    when(clock.instant())
        .thenAnswer(i -> Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    workspace = workspaces.saveAndFlush(new Workspace("Studio", "Creator"));
    brand = new Brand("Brand", workspace);
    brand.updateSignature(StrategyFixtures.signature());
    brand = brands.saveAndFlush(brand);
    scope = Set.of(workspace.getId());
    actorId =
        users
            .saveAndFlush(
                new CadenceUser("strategy-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)))
            .getId();
    authenticate(workspace.getId(), UserRole.OWNER);
    var identity = identityUseCase.create(brand.getId(), scope, StrategyFixtures.identityContent());
    identityId = identity.id();
    identityUseCase.approve(identityId, scope, 0, actorId);
  }

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  void authenticate(UUID workspace, UserRole role) {
    var principal = new CadencePrincipal(actorId, "strategy-fixture", role, Set.of(workspace));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
  }

  long count() {
    return strategies
        .findAllByBrandId(brand.getId(), org.springframework.data.domain.PageRequest.of(0, 20))
        .getTotalElements();
  }

  @Autowired javax.sql.DataSource datasource;

  @Test
  void completionWaitsForAccountRevocationBeforeParentLocks() throws Exception {
    var captured = transactions.snapshot(brand.getId(), scope);
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var q = c.prepareStatement("update cadence_users set active=false where id=?")) {
        q.setObject(1, actorId);
        q.executeUpdate();
      }
      var attempt =
          pool.submit(
              () -> {
                authenticate(workspace.getId(), UserRole.OWNER);
                try {
                  return transactions.complete(captured, scope, StrategyFixtures.output());
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
                  Integer.class)
              > 0) {
            waiting = true;
            break;
          }
          Thread.sleep(20);
        }
        assertThat(waiting).isTrue();
        try (var q = c.prepareStatement("select id from workspaces where id=? for update nowait")) {
          q.setObject(1, workspace.getId());
          assertThat(q.executeQuery().next()).isTrue();
        }
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> attempt.get(15, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    assertThat(count()).isZero();
  }

  @Test
  void approvedContentIsUsedInsteadOfLegacySignatureAndAuditIsExact() {
    jdbc.update(
        "update brand_signature_fonts set font='Legacy ignored' where brand_id=?", brand.getId());
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              StrategyGenerationCommand command = i.getArgument(0);
              assertThat(command.signature().fonts()).containsExactly("Inter");
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return StrategyFixtures.output();
            });
    var id = build.execute(brand.getId(), scope).id();
    var source = strategies.findById(id).orElseThrow().getGeneration().getIdentitySource();
    assertThat(source.identityId()).isEqualTo(identityId);
    assertThat(source.requestedBy()).isEqualTo(actorId);
    assertThat(source.approvedBy()).isEqualTo(actorId);
    assertThat(source.version()).isEqualTo(1);
    assertThat(source.lockVersion()).isEqualTo(1);
    assertThat(source.checksum()).isEqualTo(StrategyFixtures.identityContent().checksum());
    assertThat(source.analysisId()).isNull();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategies set generation_identity_checksum=? where id=?",
                    "0".repeat(64),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update strategies set generation_requested_by=null where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into strategies select (jsonb_populate_record(null::strategies,to_jsonb(s)||jsonb_build_object('id',?::uuid,'version',2,'generation_id',?::uuid,'generation_identity_checksum',repeat('0',64)))).* from strategies s where s.id=?",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class)
        .hasMessageContaining("Strategy Identity source changed");
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void newDraftOrApprovalDuringModelCannotReplaceCapturedSource(boolean approve) {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              var draft =
                  identityUseCase
                      .update(identityId, scope, 1, StrategyFixtures.identityContent())
                      .details();
              if (approve) identityUseCase.approve(draft.id(), scope, 0, actorId);
              return StrategyFixtures.output();
            });
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count()).isZero();
  }

  @Test
  void latestDraftBlocksFallbackAndLockedIsNotApproved() {
    var draft =
        identityUseCase.update(identityId, scope, 1, StrategyFixtures.identityContent()).details();
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    identityUseCase.approve(draft.id(), scope, 0, actorId);
    jdbc.update(
        "update brand_identities set status='LOCKED',lock_version=lock_version+1 where id=?",
        draft.id());
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(generator);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"active", "membership", "role"})
  void actualAccessRevocationDuringModelRejectsPublication(String kind) {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              switch (kind) {
                case "active" ->
                    jdbc.update("update cadence_users set active=false where id=?", actorId);
                case "role" ->
                    jdbc.update("update cadence_users set role='VIEWER' where id=?", actorId);
                default ->
                    jdbc.update(
                        "delete from workspace_memberships where user_id=? and workspace_id=?",
                        actorId,
                        workspace.getId());
              }
              return StrategyFixtures.output();
            });
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(count()).isZero();
  }

  @Test
  void forgedCapturedIdentityAndActorAreRejected() {
    var captured = transactions.snapshot(brand.getId(), scope);
    var original = captured.source();
    var forged =
        new ApprovedIdentitySource(
            UUID.randomUUID(),
            original.version(),
            original.lockVersion(),
            original.checksum(),
            original.approvedBy(),
            original.approvedAt(),
            null,
            actorId);
    var snapshot =
        new StrategyBuildTransactions.Snapshot(brand.getId(), captured.command(), forged, scope);
    assertThatThrownBy(() -> transactions.complete(snapshot, scope, StrategyFixtures.output()))
        .isInstanceOf(IllegalStateException.class);
    var other =
        users.saveAndFlush(
            new CadenceUser("other-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    actorId = other.getId();
    authenticate(workspace.getId(), UserRole.OWNER);
    assertThatThrownBy(() -> transactions.complete(captured, scope, StrategyFixtures.output()))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(count()).isZero();
  }

  @Test
  void persistsDraftPillarsCadenceAndProvenanceWithNoProviderTransaction() {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return StrategyFixtures.output();
            });
    var result = build.execute(brand.getId(), scope);
    assertThat(result.version()).isEqualTo(1);
    assertThat(result.status()).isEqualTo(ApprovalStatus.DRAFT);
    assertThat(result.content()).isEqualTo(StrategyFixtures.output());
    assertThat(
            jdbc.queryForObject(
                "select sum(pillar_weight) from strategy_content_pillars where strategy_id=?",
                Integer.class,
                result.id()))
        .isEqualTo(100);
    assertThat(
            jdbc.queryForObject(
                "select posts_per_week from strategies where id=?", Integer.class, result.id()))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                "select generation_provider from strategies where id=?", String.class, result.id()))
        .isEqualTo("test-provider");
    assertThat(
            jdbc.queryForObject(
                "select publishing_time::text from strategy_best_publishing_times where strategy_id=?",
                String.class,
                result.id()))
        .isEqualTo("12:00:00");
    var reloadedTimes =
        new org.springframework.transaction.support.TransactionTemplate(transactionManager)
            .execute(
                status ->
                    strategies.findById(result.id()).orElseThrow().getCadence().getBestTimes());
    assertThat(reloadedTimes).containsExactly(LocalTime.NOON);
  }

  @Test
  void nullAndProviderFailurePreservePreviousDraft() {
    when(generator.generate(any())).thenReturn(StrategyFixtures.output());
    var original = build.execute(brand.getId(), scope);
    reset(generator);
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(StrategyGenerationUnavailableException.class);
    when(generator.generate(any()))
        .thenThrow(new IllegalArgumentException("api_key=private-secret"));
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(StrategyGenerationUnavailableException.class)
        .hasNoCause()
        .hasMessage("Strategy generation is unavailable");
    assertThat(count()).isEqualTo(1);
    assertThat(strategies.findById(original.id())).isPresent();
  }

  @Test
  void missingSignatureAndArchiveAreRejectedBeforeProvider() {
    var missing = brands.saveAndFlush(new Brand("Missing", workspace));
    assertThatThrownBy(() -> build.execute(missing.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    var archived = brands.findById(brand.getId()).orElseThrow();
    archived.archive();
    brands.saveAndFlush(archived);
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(generator);
    assertThat(count()).isZero();
  }

  @Test
  void foreignScopeAndBrandAndReservedRolesAreDenied() {
    assertThatThrownBy(() -> build.execute(brand.getId(), Set.of(UUID.randomUUID())))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    var foreign = workspaces.saveAndFlush(new Workspace("Foreign", "Creator"));
    var foreignBrand = brands.saveAndFlush(new Brand("Foreign", foreign));
    assertThatThrownBy(() -> build.execute(foreignBrand.getId(), scope))
        .isInstanceOf(ResourceNotFoundException.class);
    for (var role : List.of(UserRole.EDITOR, UserRole.VIEWER)) {
      authenticate(workspace.getId(), role);
      assertThatThrownBy(() -> build.execute(brand.getId(), scope))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
      assertThatThrownBy(() -> transactions.snapshot(brand.getId(), scope))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    SecurityContextHolder.clearContext();
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(
            org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
                .class);
    verifyNoInteractions(generator);
  }

  @Test
  void sourceEditDuringProviderCannotPublish() {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              var edited = brands.findByIdAndWorkspaceIdIn(brand.getId(), scope).orElseThrow();
              edited.rename("Changed");
              brands.saveAndFlush(edited);
              return StrategyFixtures.output();
            });
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count()).isZero();
  }

  @Test
  void directApprovedIdentityContentEditIsRejectedAndCannotPublish() {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              jdbc.update(
                  "update identity_fonts set item_value='Changed' where identity_id=?", identityId);
              return StrategyFixtures.output();
            });
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(StrategyGenerationUnavailableException.class);
    assertThat(count()).isZero();
  }

  @Test
  void workspaceArchiveDuringProviderCannotPublish() {
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              var edited = workspaces.findById(workspace.getId()).orElseThrow();
              edited.archive();
              workspaces.saveAndFlush(edited);
              return StrategyFixtures.output();
            });
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count()).isZero();
  }

  @Test
  void deadlineAndIndependentDatabaseClockRejectLatePublication() {
    var snapshot = transactions.snapshot(brand.getId(), scope);
    when(clock.instant()).thenReturn(Instant.now().plusSeconds(61));
    assertThatThrownBy(() -> transactions.complete(snapshot, scope, StrategyFixtures.output()))
        .isInstanceOf(IllegalStateException.class);
    when(clock.instant())
        .thenReturn(
            Instant.now().minusSeconds(120).truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    when(generator.generate(any())).thenReturn(StrategyFixtures.output());
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(count()).isZero();
  }

  @Test
  void concurrentGenerationsAllocateDistinctMonotonicDraftVersions() throws Exception {
    var ready = new CountDownLatch(2);
    var release = new CountDownLatch(1);
    when(generator.generate(any()))
        .thenAnswer(
            i -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              ready.countDown();
              if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("timeout");
              return StrategyFixtures.output();
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first =
          executor.submit(
              () -> {
                authenticate(workspace.getId(), UserRole.OWNER);
                try {
                  return build.execute(brand.getId(), scope);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      var second =
          executor.submit(
              () -> {
                authenticate(workspace.getId(), UserRole.OWNER);
                try {
                  return build.execute(brand.getId(), scope);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        release.countDown();
      }
      assertThat(
              List.of(
                  first.get(10, TimeUnit.SECONDS).version(),
                  second.get(10, TimeUnit.SECONDS).version()))
          .containsExactlyInAnyOrder(1, 2);
    }
    assertThat(count()).isEqualTo(2);
  }

  @Test
  void generationProvenanceAndIdentityCannotBeForgedOrErased() {
    when(generator.generate(any())).thenReturn(StrategyFixtures.output());
    var generated = build.execute(brand.getId(), scope);
    var id = generated.id();
    assertThatThrownBy(
            () -> jdbc.update("update strategies set generation_provider='forged' where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("update strategies set version=version+1 where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("delete from strategies where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(count()).isEqualTo(1);
  }

  @Test
  void allocationChecksAreDeferredAndRejectPartialDraftEdits() {
    when(generator.generate(any())).thenReturn(StrategyFixtures.output());
    var id = build.execute(brand.getId(), scope).id();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategy_content_pillars set pillar_weight=1 where strategy_id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("delete from strategy_best_publishing_times where strategy_id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select sum(pillar_weight) from strategy_content_pillars where strategy_id=?",
                Integer.class,
                id))
        .isEqualTo(100);
  }

  @Test
  void approvedAndLockedContentCannotBeEditedOrDowngraded() {
    when(generator.generate(any())).thenReturn(StrategyFixtures.output());
    var id = build.execute(brand.getId(), scope).id();
    var actor =
        users.saveAndFlush(
            new CadenceUser("approval-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    jdbc.update(
        "update strategies set status='APPROVED',approved_by=?,approved_at=now() where id=?",
        actor.getId(),
        id);
    assertThatThrownBy(() -> jdbc.update("update strategies set posts_per_week=4 where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategy_content_pillars set pillar_description='forged' where strategy_id=?",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(() -> jdbc.update("update strategies set status='DRAFT' where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbc.update("update strategies set status='LOCKED' where id=?", id);
    assertThatThrownBy(() -> jdbc.update("update strategies set status='APPROVED' where id=?", id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void maximumVersionRejectsGenerationBeforeProvider() {
    jdbc.update(
        "insert into strategies(id,created_at,updated_at,brand_id,version,status,posts_per_week,publishing_timezone) values(?,now(),now(),?,2147483647,'DRAFT',3,'UTC')",
        UUID.randomUUID(),
        brand.getId());
    assertThatThrownBy(() -> build.execute(brand.getId(), scope))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Strategy version limit reached");
    verifyNoInteractions(generator);
  }
}
