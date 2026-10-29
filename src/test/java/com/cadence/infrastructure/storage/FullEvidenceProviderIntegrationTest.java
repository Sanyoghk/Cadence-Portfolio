package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.application.usecases.AnalyzeBrandUseCase;
import com.cadence.application.usecases.BuildStrategyUseCase;
import com.cadence.application.usecases.RegisterBrandEvidenceUseCase;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import com.cadence.support.StrategyFixtures;
import com.github.dockerjava.api.model.Capability;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.MountableFile;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * Full durable pipeline with real providers; the public website fetch is an offline byte fixture.
 */
@SpringBootTest(properties = "cadence.evidence.artifacts-enabled=true")
@ActiveProfiles({"test", "postgres-test", "s3", "clamd", "renderer"})
@Import(FullEvidenceProviderIntegrationTest.SignatureFixture.class)
@Testcontainers
class FullEvidenceProviderIntegrationTest {
  private static final String BUCKET = "private-evidence";
  private static final String ROOT_USER = "fixture-" + UUID.randomUUID();
  private static final String ROOT_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
  private static final String APP_USER = "app-" + UUID.randomUUID();
  private static final String APP_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
  private static final String RUNTIME_ROLE =
      "fixture_runtime_" + UUID.randomUUID().toString().replace("-", "");
  private static final String RUNTIME_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
  private static boolean runtimePrivilegesRestricted;
  private static final String TOKEN =
      UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
  private static final Path RENDERER_DIRECTORY = Path.of("docker", "evidence-renderer");
  private static final String PRIVATE_CIDRS =
      "127.0.0.0/8,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16";
  private static final byte[] EICAR =
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
          .getBytes(StandardCharsets.US_ASCII);

  @Container
  static final PostgreSQLContainer<?> POSTGRES = com.cadence.support.TestContainerImages.postgres();

  @Container
  static final GenericContainer<?> MINIO =
      new GenericContainer<>(com.cadence.support.TestContainerImages.minio())
          .withEnv("MINIO_ROOT_USER", ROOT_USER)
          .withEnv("MINIO_ROOT_PASSWORD", ROOT_SECRET)
          .withEnv("MINIO_BROWSER", "off")
          .withExposedPorts(9000)
          .waitingFor(
              Wait.forHttp("/minio/health/ready")
                  .forPort(9000)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  @Container
  static final GenericContainer<?> CLAMAV =
      new GenericContainer<>(com.cadence.support.TestContainerImages.clamav())
          .withEnv("TZ", "UTC")
          .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
          .withEnv("CLAMD_STARTUP_TIMEOUT", "180")
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("docker/clamav/clamd.conf")),
              "/etc/clamav/clamd.conf")
          .withExposedPorts(3310)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4)));

  @Container
  static final GenericContainer<?> RENDERER =
      new GenericContainer<>(rendererImage())
          .withEnv("CADENCE_RENDERER_TOKEN", TOKEN)
          .withExposedPorts(8081)
          .waitingFor(
              Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(4)))
          .withCreateContainerCmdModifier(
              command ->
                  command
                      .getHostConfig()
                      .withCapDrop(Capability.ALL)
                      .withReadonlyRootfs(true)
                      .withMemory(2L * 1024 * 1024 * 1024)
                      .withNanoCPUs(1_000_000_000L)
                      .withPidsLimit(256L)
                      .withShmSize(256L * 1024 * 1024)
                      .withTmpFs(
                          Map.of(
                              "/tmp", "rw,noexec,nosuid,nodev,size=128m",
                              "/dev/mqueue", "ro,noexec,nosuid,nodev,size=4k,mode=000"))
                      .withSecurityOpts(RendererContainerSupport.securityOptions(seccomp())));

  @DynamicPropertySource
  static void providers(DynamicPropertyRegistry registry) throws Exception {
    RendererContainerSupport.verifyNamespaceBoundary(RENDERER);
    bootstrapMinio();
    bootstrapDatabaseRuntime();
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.datasource.username", () -> RUNTIME_ROLE);
    registry.add("spring.datasource.password", () -> RUNTIME_SECRET);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
    registry.add(
        "cadence.evidence.s3.endpoint", FullEvidenceProviderIntegrationTest::minioEndpoint);
    registry.add("cadence.evidence.s3.bucket", () -> BUCKET);
    registry.add("cadence.evidence.s3.access-key", () -> APP_USER);
    registry.add("cadence.evidence.s3.secret-key", () -> APP_SECRET);
    registry.add("cadence.evidence.s3.allow-insecure-local", () -> true);
    registry.add("cadence.evidence.s3.minio-fixture", () -> true);
    registry.add("cadence.evidence.clamd.host", CLAMAV::getHost);
    registry.add("cadence.evidence.clamd.port", () -> CLAMAV.getMappedPort(3310));
    registry.add("cadence.evidence.clamd.allowed-cidrs", () -> PRIVATE_CIDRS);
    registry.add("cadence.evidence.clamd.private-channel-acknowledged", () -> true);
    registry.add("cadence.evidence.renderer.host", RENDERER::getHost);
    registry.add("cadence.evidence.renderer.port", () -> RENDERER.getMappedPort(8081));
    registry.add("cadence.evidence.renderer.allowed-cidrs", () -> PRIVATE_CIDRS);
    registry.add("cadence.evidence.renderer.token", () -> TOKEN);
    registry.add("cadence.evidence.renderer.private-channel-acknowledged", () -> true);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SignatureFixture {
    /**
     * Only scanner freshness is anchored; durable leases/receipts keep the real PostgreSQL clock.
     */
    @Bean(destroyMethod = "close")
    @Primary
    ClamdEvidenceMalwareScanner fixtureSignatureScanner(ClamdScannerProperties properties)
        throws Exception {
      return new ClamdEvidenceMalwareScanner(
          properties,
          InetAddress::getAllByName,
          Clock.fixed(loadedDatabaseTime().plusSeconds(3600), ZoneOffset.UTC));
    }
  }

  @MockitoBean UrlEvidenceFetcher fetcher;
  @MockitoBean BrandAnalysisPort analysisPort;
  @MockitoBean StrategyGenerationService strategyGenerator;
  @Autowired BuildStrategyUseCase buildStrategy;
  @Autowired com.cadence.application.usecases.BrandIdentityUseCase identityUseCase;
  @Autowired com.cadence.application.usecases.ManageStrategyUseCase manageStrategy;
  @Autowired StrategyRepository strategies;
  @Autowired CadenceUserRepository users;
  @Autowired RegisterBrandEvidenceUseCase register;
  @Autowired AnalyzeBrandUseCase analyze;
  @Autowired JdbcTemplate jdbc;
  @Autowired EvidenceMalwareScanWorker scanWorker;
  @Autowired UrlFetchWorker fetchWorker;
  @Autowired EvidenceArtifactWorker artifactWorker;
  @Autowired EvidenceObjectStorage storage;
  @Autowired EvidenceMalwareScanner scanner;
  @Autowired EvidenceRenderer renderer;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired EvidenceScanResultRepository scans;
  @Autowired EvidenceArtifactRepository artifacts;
  @Autowired QuarantineObjectRepository objects;
  private Workspace workspace;
  private Brand brand;

  @BeforeEach
  void owner() throws Exception {
    restrictRuntimePrivileges();
    // Only analysis is a bounded fixture; storage/scanner/renderer remain actual production
    // adapters.
    when(analysisPort.analyze(any()))
        .thenReturn(
            new BrandAnalysisResult(
                new BrandAnalysisResult.Palette(
                    "#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
                List.of("Inter"),
                List.of("clear"),
                List.of("spam"),
                "Fixture caption",
                List.of(BrandAnalysisResult.Warning.DEMONSTRATION_ONLY),
                "test-fixture",
                "model-v1",
                "prompt-v1"));
    assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo(RUNTIME_ROLE);
    workspace = workspaces.saveAndFlush(new Workspace("Full provider fixture", "Creator"));
    brand = brands.saveAndFlush(new Brand("Evidence brand", workspace));
    var actor =
        users.saveAndFlush(
            new CadenceUser("full-" + UUID.randomUUID(), UserRole.OWNER, Set.of(workspace)));
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CadencePrincipal(
                    actor.getId(),
                    "full-provider-fixture",
                    UserRole.OWNER,
                    Set.of(workspace.getId())),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OWNER"))));
    assertThat(storage).isInstanceOf(S3EvidenceObjectStorage.class);
    assertThat(scanner).isInstanceOf(ClamdEvidenceMalwareScanner.class);
    assertThat(renderer).isInstanceOf(RendererEvidenceAdapter.class);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authorizedPdfTraversesPrivateStorageRealScanAndIsolatedTextExtraction() {
    byte[] source = pdf("Full provider document evidence");
    var registration =
        register.registerFile(
            brand.getId(), Set.of(workspace.getId()), "document.pdf", "application/pdf", source);
    assertThat(storage.readQuarantined(registration.evidence().getSourceReference(), source.length))
        .isEqualTo(source);
    assertThat(scanWorker.runOnce()).isTrue();
    var scan = latestScan(registration.evidence().getId());
    assertThat(scan.getVerdict()).isEqualTo(ScanVerdict.CLEAN);
    assertThat(scan.getScannerEngine()).isEqualTo("clamav-1.4.6");
    assertThat(scan.getChecksum()).isEqualTo(checksum(source));
    assertThat(artifactWorker.runOnce()).isTrue();
    var artifact = artifactFor(registration.evidence().getId());
    assertThat(artifact.getKind()).isEqualTo(EvidenceArtifact.Kind.PDF_TEXT);
    assertThat(artifact.getExtractedText()).isEqualTo("Full provider document evidence");
    assertThat(artifact.getSourceChecksum()).isEqualTo(checksum(source));
    assertThat(artifact.getScanResultId()).isEqualTo(scan.getId());
    assertThat(artifact.getRendererVersion()).isEqualTo("isolated-v1");
    assertThat(artifact.getStorageReference()).isNull();
    assertThat(artifactWorker.runOnce()).isFalse();
  }

  @Test
  void authorizedWebsiteTraversesFetchScanScreenshotAndDurablePrivateArtifactReference() {
    String url = "https://example.com/full-provider-evidence";
    byte[] html =
        ("<!doctype html><html><head><title>Fixture website</title></head>"
                + "<body><h1>Full provider website evidence</h1>"
                + "<script>document.body.remove()</script></body></html>")
            .getBytes(StandardCharsets.UTF_8);
    when(fetcher.fetch(url)).thenReturn(new UrlEvidenceFetcher.FetchedUrl(html, "text/html"));
    var registration =
        register.register(brand.getId(), Set.of(workspace.getId()), EvidenceType.URL, url);
    assertThat(fetchWorker.runOnce()).isTrue();
    var fetched = evidence.findById(registration.evidence().getId()).orElseThrow();
    assertThat(fetched.getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThat(fetched.getFetchedChecksum()).isEqualTo(checksum(html));
    assertThat(storage.readQuarantined(fetched.getFetchedReference(), html.length)).isEqualTo(html);
    assertThat(scanWorker.runOnce()).isTrue();
    var scan = latestScan(fetched.getId());
    assertThat(scan.getVerdict()).isEqualTo(ScanVerdict.CLEAN);
    assertThat(artifactWorker.runOnce()).isTrue();
    var artifact = artifactFor(fetched.getId());
    assertThat(artifact.getKind()).isEqualTo(EvidenceArtifact.Kind.SCREENSHOT);
    assertThat(artifact.getSourceChecksum()).isEqualTo(checksum(html));
    assertThat(artifact.getScanResultId()).isEqualTo(scan.getId());
    assertThat(artifact.getPixelWidth()).isEqualTo(1280);
    assertThat(artifact.getPixelHeight()).isEqualTo(720);
    assertThat(artifact.getStorageReference()).startsWith("quarantine:quarantine/writes/");
    byte[] screenshot =
        storage.readQuarantined(
            artifact.getStorageReference(), EvidenceRenderer.MAX_SCREENSHOT_BYTES);
    assertThat(screenshot).startsWith(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47});
    assertThat(checksum(screenshot)).isEqualTo(artifact.getOutputChecksum());
    var generation =
        objects.findAll().stream()
            .filter(item -> item.reference().equals(artifact.getStorageReference()))
            .findFirst()
            .orElseThrow();
    assertThat(generation.getState()).isEqualTo(QuarantineObject.State.REFERENCED);
    assertThat(generation.getEvidenceId()).isEqualTo(fetched.getId());
    assertThat(generation.getRetainedBytes()).isEqualTo(screenshot.length);
    assertThat(objects.artifactReferences(artifact.getStorageReference())).isEqualTo(1);
    verify(fetcher).fetch(url);
  }

  @Test
  void realEicarUploadIsRejectedBeforeObjectPublicationOrArtifactCreation() {
    byte[] content = infectedPdf();
    // A complete PDF embeds the canonical EICAR file as an exact EmbeddedFile stream.
    assertThat(scanner.scan(content).result()).isEqualTo(MalwareScanResult.INFECTED);
    assertThatThrownBy(
            () ->
                register.registerFile(
                    brand.getId(),
                    Set.of(workspace.getId()),
                    "infected.pdf",
                    "application/pdf",
                    content))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Evidence file failed the malware scan");
    assertThat(evidence.findAllByBrandId(brand.getId(), PageRequest.of(0, 10))).isEmpty();
    assertThat(artifacts.findAll())
        .noneMatch(item -> item.getSourceChecksum().equals(checksum(content)));
    var reservation =
        objects.findAll().stream()
            .filter(item -> item.getWorkspaceId().equals(workspace.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(reservation.getState()).isEqualTo(QuarantineObject.State.RESERVED);
    assertThatThrownBy(() -> storage.readQuarantined(reservation.reference(), content.length))
        .isInstanceOfSatisfying(
            QuarantineReadFailure.class,
            failure -> assertThat(failure.code()).isEqualTo(QuarantineReadFailure.Code.NOT_FOUND));
  }

  @Test
  void realEncryptedPdfIsRejectedBeforeStoragePublication() throws Exception {
    byte[] encrypted;
    try (var fixture = getClass().getResourceAsStream("/evidence/encrypted-aes256.pdf")) {
      assertThat(fixture).isNotNull();
      encrypted = fixture.readAllBytes();
    }
    assertThat(scanner.scan(encrypted).result()).isEqualTo(MalwareScanResult.INFECTED);
    assertThatThrownBy(
            () ->
                register.registerFile(
                    brand.getId(),
                    Set.of(workspace.getId()),
                    "encrypted.pdf",
                    "application/pdf",
                    encrypted))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Evidence file failed the malware scan");
    assertThat(evidence.findAllByBrandId(brand.getId(), PageRequest.of(0, 10))).isEmpty();
    var reservation =
        objects.findAll().stream()
            .filter(item -> item.getWorkspaceId().equals(workspace.getId()))
            .findFirst()
            .orElseThrow();
    assertThatThrownBy(() -> storage.readQuarantined(reservation.reference(), encrypted.length))
        .isInstanceOfSatisfying(
            QuarantineReadFailure.class,
            failure -> assertThat(failure.code()).isEqualTo(QuarantineReadFailure.Code.NOT_FOUND));
  }

  @Test
  void runtimeRoleCanReplaceIdentityCollectionsButCannotRewriteMetadataOrDeleteHistory() {
    var scope = Set.of(workspace.getId());
    var palette =
        new BrandIdentityContent.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5");
    var first =
        identityUseCase.create(
            brand.getId(),
            scope,
            new BrandIdentityContent(
                palette, List.of("Inter", "Roboto"), List.of("clear"), List.of("spam"), "First"));
    var edited =
        identityUseCase.update(
            first.id(),
            scope,
            0,
            new BrandIdentityContent(
                palette, List.of("Lato"), List.of("warm", "clear"), List.of("spam"), "Edited"));
    assertThat(edited.details().lockVersion()).isEqualTo(1);
    assertThat(identityUseCase.findById(first.id(), scope).content().fonts())
        .containsExactly("Lato");
    for (var sql :
        List.of(
            "delete from brand_identities where id=?",
            "update brand_identities set status='APPROVED' where id=?",
            "update brand_identities set version=99 where id=?",
            "update brand_identities set brand_id=brand_id where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, first.id()))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity_fonts where identity_id=?",
                Integer.class,
                first.id()))
        .isEqualTo(1);
    assertThat(brands.findById(brand.getId()).orElseThrow().getSignature().getSampleCaption())
        .isNull();
    var actor =
        ((CadencePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .userId();
    var approved = identityUseCase.approve(first.id(), scope, 1, actor);
    assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
    var branch =
        identityUseCase
            .update(
                first.id(),
                scope,
                approved.lockVersion(),
                new BrandIdentityContent(
                    palette, List.of("Inter"), List.of("clear"), List.of("spam"), "Branch"))
            .details();
    assertThat(branch.revisedFromId()).isEqualTo(first.id());
    assertThat(branch.approvedBy()).isNull();
    for (var sql :
        List.of(
            "update brand_identities set sample_caption='Tampered' where id=?",
            "update brand_identities set approved_at=now() where id=?",
            "update identity_fonts set item_value='Tampered' where identity_id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, first.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set revised_from_id=null where id=?", branch.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(identityUseCase.findById(first.id(), scope).content().sampleCaption())
        .isEqualTo("Edited");
  }

  @Test
  void runtimeRoleCanBuildDraftsButCannotRewriteProvenanceOrApprovedContent() {
    analyze.execute(brand.getId(), Set.of(workspace.getId()));
    var identity =
        identityUseCase.create(
            brand.getId(), Set.of(workspace.getId()), StrategyFixtures.identityContent());
    var actorId =
        ((CadencePrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal())
            .userId();
    identityUseCase.approve(identity.id(), Set.of(workspace.getId()), 0, actorId);
    when(strategyGenerator.generate(any())).thenReturn(StrategyFixtures.output());
    var first = buildStrategy.execute(brand.getId(), Set.of(workspace.getId()));
    var second = buildStrategy.execute(brand.getId(), Set.of(workspace.getId()));
    assertThat(first.version()).isEqualTo(1);
    assertThat(second.version()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select generation_identity_id from strategies where id=?", UUID.class, first.id()))
        .isEqualTo(identity.id());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategies set generation_identity_id=null where id=?", first.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from strategies where brand_id=?", Integer.class, brand.getId()))
        .isEqualTo(2);
    var draft = strategies.findById(first.id()).orElseThrow();
    var capturedDraft = draft;
    assertThatThrownBy(
            () ->
                capturedDraft.setCadence(new PublishingCadence(4, "UTC", List.of(LocalTime.NOON))))
        .isInstanceOf(IllegalStateException.class);
    manageStrategy.updatePlaybook(
        first.id(),
        Set.of(workspace.getId()),
        draft.getLockVersion(),
        new StrategyContent(
            List.of(new StrategyContent.Pillar("Revised", 100, "Human review")),
            4,
            "UTC",
            List.of(LocalTime.NOON)),
        com.cadence.support.StrategyFixtures.editPlaybook());
    draft = strategies.findById(first.id()).orElseThrow();
    var approved =
        manageStrategy.approve(
            first.id(),
            Set.of(workspace.getId()),
            draft.getLockVersion(),
            ((CadencePrincipal)
                    SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .userId());
    var revised =
        manageStrategy
            .updatePlaybook(
                first.id(),
                Set.of(workspace.getId()),
                approved.lockVersion(),
                new StrategyContent(
                    List.of(new StrategyContent.Pillar("Revised", 100, "Human review")),
                    4,
                    "UTC",
                    List.of(LocalTime.NOON)),
                com.cadence.support.StrategyFixtures.editPlaybook())
            .details();
    assertThat(revised.version()).isEqualTo(3);
    assertThat(revised.revisedFromId()).isEqualTo(first.id());
    assertThat(revised.generation()).isNull();
    assertThatThrownBy(
            () ->
                jdbc.update("update strategies set revised_from_id=null where id=?", revised.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategies set generation_provider='forged' where id=?", first.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("delete from strategies where id=?", first.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update strategy_content_pillars set pillar_name='forged' where strategy_id=?",
                    first.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("update strategies set status='DRAFT' where id=?", first.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(strategies.findById(first.id()).orElseThrow().getStatus())
        .isEqualTo(ApprovalStatus.APPROVED);
  }

  @Test
  void runtimeRoleCanReplaceExistingBrandAnalysisValueCollections() {
    var first = analyze.execute(brand.getId(), Set.of(workspace.getId()));
    assertThat(first.getFonts()).isNotEmpty();
    var second = analyze.execute(brand.getId(), Set.of(workspace.getId()));
    assertThat(second.getFonts()).containsExactlyElementsOf(first.getFonts());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_signature_fonts where brand_id = ?",
                Integer.class,
                brand.getId()))
        .isEqualTo(second.getFonts().size());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_signature_voice_up where brand_id = ?",
                Integer.class,
                brand.getId()))
        .isEqualTo(second.getVoiceUp().size());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_signature_voice_down where brand_id = ?",
                Integer.class,
                brand.getId()))
        .isEqualTo(second.getVoiceDown().size());
  }

  @Test
  void runtimeCannotDeleteAnalysisReceiptOrRewriteSourceColumns() {
    analyze.execute(brand.getId(), Set.of(workspace.getId()));
    var id =
        jdbc.queryForObject(
            "select id from brand_analysis_attempts where brand_id=?", UUID.class, brand.getId());
    long brandToken =
        jdbc.queryForObject(
            "select lock_version from brands where id=?", Long.class, brand.getId());
    var generated =
        identityUseCase.createFromAnalysis(
            brand.getId(), Set.of(workspace.getId()), id, brandToken);
    assertThat(generated.analysisProvenance().analysisId()).isEqualTo(id);
    assertThat(generated.content().checksum())
        .isEqualTo(generated.analysisProvenance().initialOutputChecksum());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set analysis_attempt_id=null where id=?",
                    generated.id()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .hasStackTraceContaining("permission denied");
    assertThatThrownBy(() -> jdbc.update("delete from brand_analysis_attempts where id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .hasStackTraceContaining("permission denied");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_analysis_attempts set source_checksum=? where id=?",
                    "a".repeat(64),
                    id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .hasStackTraceContaining("permission denied");
  }

  @Test
  void runtimeDatabaseLockWaitHasARealFiveSecondDeadline() throws Exception {
    assertThat(jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("10s");
    assertThat(jdbc.queryForObject("SHOW lock_timeout", String.class)).isEqualTo("5s");
    try (var blocker =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_ROLE, RUNTIME_SECRET)) {
      blocker.setAutoCommit(false);
      try (var statement =
          blocker.prepareStatement("SELECT id FROM workspaces WHERE id = ? FOR UPDATE")) {
        statement.setObject(1, workspace.getId());
        statement.executeQuery().close();
      }
      long start = System.nanoTime();
      try {
        assertThatThrownBy(
                () ->
                    jdbc.queryForObject(
                        "SELECT id FROM workspaces WHERE id = ? FOR UPDATE",
                        UUID.class,
                        workspace.getId()))
            .isInstanceOfSatisfying(
                org.springframework.dao.DataAccessException.class,
                failure ->
                    assertThat(failure.getRootCause())
                        .isInstanceOfSatisfying(
                            SQLException.class,
                            sql -> assertThat(sql.getSQLState()).isEqualTo("55P03")));
        assertThat(Duration.ofNanos(System.nanoTime() - start))
            .isBetween(Duration.ofSeconds(4), Duration.ofSeconds(8));
      } finally {
        blocker.rollback();
      }
    }
  }

  @Test
  void runtimeRoleCannotCreateSchemaObjectsModifyArtifactHistoryOrReadMigrationHistory()
      throws Exception {
    try (var connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_ROLE, RUNTIME_SECRET)) {
      for (String forbidden :
          List.of(
              "CREATE TABLE runtime_forbidden_fixture(id UUID)",
              "UPDATE evidence_artifacts SET renderer_version = 'forged' WHERE false",
              "DELETE FROM evidence_artifacts WHERE false",
              "SELECT * FROM flyway_schema_history LIMIT 1")) {
        assertThatThrownBy(
                () -> {
                  try (var statement = connection.createStatement()) {
                    statement.execute(forbidden);
                  }
                })
            .isInstanceOfSatisfying(
                SQLException.class,
                failure -> assertThat(failure.getSQLState()).isEqualTo("42501"));
      }
    }
  }

  private static void bootstrapDatabaseRuntime() throws SQLException {
    try (var admin = adminConnection();
        var statement = admin.createStatement()) {
      // PostgreSQL identifiers cannot be JDBC parameters. Server-side format quotes both name
      // and secret; neither generated credential is interpolated into Java SQL text.
      statement.execute(
          """
          CREATE FUNCTION pg_temp.bootstrap_evidence_runtime(role_name text, role_secret text)
          RETURNS void LANGUAGE plpgsql AS $$
          BEGIN
            EXECUTE format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT PASSWORD %L', role_name, role_secret);
            EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), role_name);
            EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', role_name);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO %I', current_user, role_name);
            EXECUTE format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', current_user, role_name);
          END;
          $$;
          """);
      try (var bootstrap =
          admin.prepareStatement("select pg_temp.bootstrap_evidence_runtime(?, ?)")) {
        bootstrap.setString(1, RUNTIME_ROLE);
        bootstrap.setString(2, RUNTIME_SECRET);
        bootstrap.execute();
      }
    }
  }

  private static synchronized void restrictRuntimePrivileges() throws Exception {
    if (runtimePrivilegesRestricted) return;
    if (!RUNTIME_ROLE.matches("fixture_runtime_[0-9a-f]{32}"))
      throw new IllegalStateException("Invalid fixture identity");
    String restrictions =
        Files.readString(Path.of("docker/postgres/runtime-permissions.sql"))
            .replace("cadence_runtime", RUNTIME_ROLE);
    try (var admin = adminConnection();
        var statement = admin.createStatement()) {
      statement.execute(restrictions);
    }
    runtimePrivilegesRestricted = true;
  }

  /** Administrative credentials exist only inside fixture bootstrap/privilege helpers. */
  private static Connection adminConnection() throws SQLException {
    return DriverManager.getConnection(
        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
  }

  private EvidenceScanResult latestScan(UUID id) {
    var results = scans.latestForEvidence(id, PageRequest.of(0, 1));
    assertThat(results).hasSize(1);
    return results.getFirst();
  }

  private EvidenceArtifact artifactFor(UUID id) {
    var results =
        artifacts.findAll().stream().filter(item -> item.getEvidenceId().equals(id)).toList();
    assertThat(results).hasSize(1);
    return results.getFirst();
  }

  private static void bootstrapMinio() throws Exception {
    try (var admin =
        S3Client.builder()
            .endpointOverride(URI.create(minioEndpoint()))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ROOT_USER, ROOT_SECRET)))
            .build()) {
      admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }
    minioCommand("mc", "alias", "set", "fixture", "http://127.0.0.1:9000", ROOT_USER, ROOT_SECRET);
    minioCommand("mc", "admin", "user", "add", "fixture", APP_USER, APP_SECRET);
    String policy =
        """
        {"Version":"2012-10-17","Statement":[
          {"Effect":"Allow","Action":["s3:ListBucket","s3:GetBucketPolicy","s3:GetBucketVersioning"],"Resource":["arn:aws:s3:::private-evidence"]},
          {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::private-evidence/quarantine/*"]}
        ]}
        """;
    MINIO.copyFileToContainer(Transferable.of(policy), "/tmp/runtime-policy.json");
    minioCommand(
        "mc",
        "admin",
        "policy",
        "create",
        "fixture",
        "evidence-runtime",
        "/tmp/runtime-policy.json");
    minioCommand(
        "mc", "admin", "policy", "attach", "fixture", "evidence-runtime", "--user", APP_USER);
  }

  private static void minioCommand(String... args) throws Exception {
    assertThat(MINIO.execInContainer(args).getExitCode())
        .withFailMessage("Private MinIO bootstrap failed")
        .isZero();
  }

  private static String minioEndpoint() {
    return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
  }

  private static ImageFromDockerfile rendererImage() {
    var image = new ImageFromDockerfile();
    for (String file :
        List.of(
            "Dockerfile",
            "requirements.txt",
            "server.py",
            "child.py",
            "smoke.py",
            "namespace_probe.py",
            "test_probe.py",
            "ephemeral.py",
            "test_ephemeral.py",
            "test_server.py")) image.withFileFromPath(file, RENDERER_DIRECTORY.resolve(file));
    return image;
  }

  private static String seccomp() {
    try {
      return Files.readString(RENDERER_DIRECTORY.resolve("seccomp.json"));
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Missing renderer seccomp profile");
    }
  }

  private static Instant loadedDatabaseTime() throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(CLAMAV.getHost(), CLAMAV.getMappedPort(3310)), 3000);
      socket.setSoTimeout(3000);
      socket.getOutputStream().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
      var response = new ByteArrayOutputStream();
      int value;
      while ((value = socket.getInputStream().read()) > 0 && response.size() < 512)
        response.write(value);
      assertThat(value).isZero();
      String[] parts = response.toString(StandardCharsets.US_ASCII).split("/", -1);
      assertThat(parts).hasSize(3);
      return LocalDateTime.parse(
              parts[2].replaceAll(" +", " "),
              DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss uuuu", Locale.ENGLISH))
          .toInstant(ZoneOffset.UTC);
    }
  }

  private static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] pdf(String text) {
    String stream = "BT /F1 12 Tf 50 700 Td (" + text + ") Tj ET";
    return writePdf(
        List.of(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream"));
  }

  private static byte[] infectedPdf() {
    String message =
        "BT /F1 12 Tf 50 700 Td (Harmless standard antivirus fixture attachment) Tj ET";
    return writePdf(
        List.of(
            "<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles << /Names [(eicar.txt) 6 0 R] >> >> >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length " + message.length() + " >>\nstream\n" + message + "\nendstream",
            "<< /Type /Filespec /F (eicar.txt) /EF << /F 7 0 R >> >>",
            "<< /Type /EmbeddedFile /Subtype /text#2Fplain /Length "
                + EICAR.length
                + " >>\nstream\n"
                + new String(EICAR, StandardCharsets.US_ASCII)
                + "\nendstream"));
  }

  private static byte[] writePdf(List<String> objects) {
    var output = new ByteArrayOutputStream();
    List<Integer> offsets = new ArrayList<>();
    output.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
    for (int index = 0; index < objects.size(); index++) {
      offsets.add(output.size());
      output.writeBytes(
          ((index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n")
              .getBytes(StandardCharsets.US_ASCII));
    }
    int xref = output.size();
    output.writeBytes(
        ("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n")
            .getBytes(StandardCharsets.US_ASCII));
    for (int offset : offsets)
      output.writeBytes(
          String.format(Locale.ROOT, "%010d 00000 n \n", offset)
              .getBytes(StandardCharsets.US_ASCII));
    output.writeBytes(
        ("trailer\n<< /Size "
                + (objects.size() + 1)
                + " /Root 1 0 R >>\nstartxref\n"
                + xref
                + "\n%%EOF\n")
            .getBytes(StandardCharsets.US_ASCII));
    return output.toByteArray();
  }
}
