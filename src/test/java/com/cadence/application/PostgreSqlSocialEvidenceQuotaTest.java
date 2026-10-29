package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.exceptions.EvidenceQuotaExceededException;
import com.cadence.application.ports.ApprovedSocialEvidenceSource;
import com.cadence.application.ports.EvidenceMalwareScanner;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.repositories.*;
import com.cadence.application.services.EvidenceProviderQuota;
import com.cadence.domain.*;
import com.cadence.domain.SocialEvidenceProvider;
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

@SpringBootTest(properties = "cadence.evidence.social.max-workspace-calls-per-hour=1")
@ActiveProfiles("postgres-test")
class PostgreSqlSocialEvidenceQuotaTest {
  @Autowired com.cadence.application.usecases.RegisterSocialBrandEvidenceUseCase socialRegistration;

  @Test
  void providerSnapshotAtTwentyThousandCharacterBoundaryPublishesWithItsReceipt() {
    Workspace workspace = workspaces.saveAndFlush(new Workspace("Long social snapshot", "Owner"));
    Brand brand = brands.saveAndFlush(new Brand("Social", workspace));
    authenticate(workspace.getId());
    String json = "{\"text\":\"" + "x".repeat(19989) + "\"}";
    assertThat(json).hasSize(20000);
    org.mockito.Mockito.when(source.fetch(brand.getId(), SocialEvidenceProvider.TIKTOK))
        .thenAnswer(
            ignored ->
                new ApprovedSocialEvidenceSource.Snapshot(
                    SocialEvidenceProvider.TIKTOK,
                    "fixture-open-id",
                    "v2",
                    Instant.now(clock),
                    json));
    var result =
        socialRegistration.register(
            brand.getId(), Set.of(workspace.getId()), SocialEvidenceProvider.TIKTOK);
    assertThat(result.evidence().getSourceReference()).isEqualTo(json);
    assertThat(origins.findById(result.evidence().getId())).isPresent();
    assertThat(
            jdbc.queryForObject(
                "SELECT char_length(source_reference) FROM brand_evidence WHERE id=?",
                Integer.class,
                result.evidence().getId()))
        .isEqualTo(20000);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE brand_evidence SET source_reference=? WHERE id=?",
                    "x".repeat(20001),
                    result.evidence().getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

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
  @Autowired JdbcTemplate jdbc;
  @Autowired EvidenceSocialOriginRepository origins;
  @Autowired com.cadence.application.usecases.RegisterBrandEvidenceUseCase registration;
  @Autowired java.time.Clock clock;
  @MockitoBean EvidenceObjectStorage storage;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean ApprovedSocialEvidenceSource source;

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void competingProviderReservationsAcrossTwoBrandsShareWorkspaceQuota() throws Exception {
    Workspace workspace = workspaces.saveAndFlush(new Workspace("Concurrent provider", "Owner"));
    Brand first = brands.saveAndFlush(new Brand("First", workspace));
    Brand second = brands.saveAndFlush(new Brand("Second", workspace));
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> results = new ArrayList<>();
      for (Brand brand : List.of(first, second))
        results.add(
            executor.submit(
                () -> {
                  authenticate(workspace.getId());
                  start.await();
                  try {
                    quota.reserve(
                        brand.getId(), Set.of(workspace.getId()), SocialEvidenceProvider.INSTAGRAM);
                    return true;
                  } catch (EvidenceQuotaExceededException expected) {
                    return false;
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      start.countDown();
      int accepted = 0;
      for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) accepted++;
      assertThat(accepted).isEqualTo(1);
    }
    assertThat(
            calls.countByWorkspaceIdAndProviderAndRequestedAtGreaterThanEqual(
                workspace.getId(), SocialEvidenceProvider.INSTAGRAM, Instant.EPOCH))
        .isEqualTo(1);
  }

  @Test
  void databaseRejectsForeignWorkspaceUnknownProviderMutationAndPrematureRetentionDeletion() {
    Workspace workspace = workspaces.saveAndFlush(new Workspace("Provider integrity", "Owner"));
    Workspace foreign = workspaces.saveAndFlush(new Workspace("Foreign", "Other"));
    Brand brand = brands.saveAndFlush(new Brand("Integrity", workspace));
    authenticate(workspace.getId());
    quota.reserve(brand.getId(), Set.of(workspace.getId()), SocialEvidenceProvider.TIKTOK);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_provider_calls(id,workspace_id,brand_id,provider,requested_at) values (?,?,?,'TIKTOK',clock_timestamp())",
                    UUID.randomUUID(),
                    foreign.getId(),
                    brand.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_provider_calls(id,workspace_id,brand_id,provider,requested_at) values (?,?,?,'UNKNOWN',clock_timestamp())",
                    UUID.randomUUID(),
                    workspace.getId(),
                    brand.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update evidence_provider_calls set requested_at=clock_timestamp()-interval '2 hours' where workspace_id=?",
                    workspace.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "delete from evidence_provider_calls where workspace_id=?", workspace.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void trustedOriginRequiresMatchingReceiptAndKeepsPublishedContentImmutable() {
    Workspace workspace = workspaces.saveAndFlush(new Workspace("Trusted origin", "Owner"));
    Brand brand = brands.saveAndFlush(new Brand("Origin", workspace));
    authenticate(workspace.getId());
    UUID receipt =
        quota.reserve(brand.getId(), Set.of(workspace.getId()), SocialEvidenceProvider.TIKTOK);
    var snapshot =
        new com.cadence.application.ports.ApprovedSocialEvidenceSource.Snapshot(
            SocialEvidenceProvider.TIKTOK,
            "fixture-open-id",
            "v2",
            Instant.now(clock),
            "{\"format\":\"approved-social-v1\"}");
    var result =
        registration.register(
            brand.getId(), Set.of(workspace.getId()), EvidenceType.TEXT, snapshot.canonicalJson());
    origins.saveAndFlush(
        new EvidenceSocialOrigin(
            result.evidence().getId(),
            receipt,
            snapshot.provider(),
            snapshot.accountId(),
            snapshot.apiVersion(),
            snapshot.retrievedAt(),
            result.evidence().getChecksum()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update evidence_social_origins set account_id='forged' where evidence_id=?",
                    result.evidence().getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "delete from evidence_social_origins where evidence_id=?",
                    result.evidence().getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_evidence set source_reference='tampered' where id=?",
                    result.evidence().getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var other =
        registration.register(
            brand.getId(),
            Set.of(workspace.getId()),
            EvidenceType.TEXT,
            "different manually supplied text");
    assertThat(origins.findById(other.evidence().getId())).isEmpty();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_social_origins(evidence_id,provider_call_id,provider,account_id,api_version,captured_at,checksum) values (?,?,'INSTAGRAM','123','v25.0',clock_timestamp(),?)",
                    other.evidence().getId(),
                    receipt,
                    other.evidence().getChecksum()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  private void authenticate(UUID workspace) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new CadencePrincipal(
                    UUID.randomUUID(), "provider-quota-fixture", UserRole.OWNER, Set.of(workspace)),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
  }
}
