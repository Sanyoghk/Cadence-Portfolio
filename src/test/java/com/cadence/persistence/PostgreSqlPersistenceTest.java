package com.cadence.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadence.application.repositories.PostMetricRepository;
import com.cadence.application.repositories.RecommendationRepository;
import com.cadence.application.repositories.SocialPostRepository;
import com.cadence.application.repositories.StrategyRepository;
import com.cadence.domain.Brand;
import com.cadence.domain.BrandSignature;
import com.cadence.domain.ColorPalette;
import com.cadence.domain.ContentPillar;
import com.cadence.domain.PostFormat;
import com.cadence.domain.PostMetric;
import com.cadence.domain.PublishingCadence;
import com.cadence.domain.Recommendation;
import com.cadence.domain.SocialPost;
import com.cadence.domain.Strategy;
import com.cadence.domain.Workspace;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-test")
@org.springframework.context.annotation.Import(com.cadence.security.CadenceIdentityService.class)
class PostgreSqlPersistenceTest {

  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private EntityManager entityManager;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private StrategyRepository strategies;

  @Autowired private SocialPostRepository posts;

  @Autowired private PostMetricRepository metrics;

  @Autowired private RecommendationRepository recommendations;

  @Autowired private com.cadence.security.CadenceIdentityService identities;

  @Autowired private com.cadence.application.repositories.CadenceUserRepository users;

  @Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentFirstAuthenticationRecoversCommittedIdentityWithoutMergingClaims()
      throws Exception {
    String subject = "concurrent-auth-" + java.util.UUID.randomUUID();
    var first = java.util.UUID.randomUUID();
    var second = java.util.UUID.randomUUID();
    for (var id : List.of(first, second))
      jdbcTemplate.update(
          "insert into workspaces (id,created_at,updated_at,name,industry,status) values (?,now(),now(),'Auth','Creator','ACTIVE')",
          id);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try (var lock =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      lock.setAutoCommit(false);
      lock.createStatement().execute("lock table cadence_users in share mode");
      var futures =
          new java.util.ArrayList<
              java.util.concurrent.Future<com.cadence.security.CadencePrincipal>>();
      for (int index = 0; index < 2; index++) {
        var workspaceId = index == 0 ? first : second;
        String role = index == 0 ? "OWNER" : "VIEWER";
        var now = java.time.Instant.now();
        var jwt =
            org.springframework.security.oauth2.jwt.Jwt.withTokenValue("test-only")
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("cadence_role", role)
                .claim("cadence_workspace_ids", List.of(workspaceId.toString()))
                .build();
        futures.add(executor.submit(() -> identities.resolve(jwt)));
      }
      var deadline = java.time.Instant.now().plusSeconds(15);
      int waiting = 0;
      while (java.time.Instant.now().isBefore(deadline)) {
        waiting =
            jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like 'insert into cadence_users%'",
                Integer.class);
        if (waiting == 2) break;
        Thread.sleep(25);
      }
      assertThat(waiting)
          .as("both requests observed absence and reached competing inserts")
          .isEqualTo(2);
      lock.commit();
      var one = futures.get(0).get(15, java.util.concurrent.TimeUnit.SECONDS);
      var two = futures.get(1).get(15, java.util.concurrent.TimeUnit.SECONDS);
      var committed = users.findByExternalSubject(subject).orElseThrow();
      assertThat(one).isEqualTo(two);
      assertThat(one.userId()).isEqualTo(committed.getId());
      assertThat(one.role()).isEqualTo(committed.getRole());
      assertThat(one.workspaceIds())
          .containsExactlyElementsOf(
              committed.getWorkspaces().stream().map(Workspace::getId).toList())
          .hasSize(1);
      assertThat(
              jdbcTemplate.queryForObject(
                  "select count(*) from cadence_users where external_subject=?",
                  Integer.class,
                  subject))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
      executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);
      jdbcTemplate.update(
          "delete from workspace_memberships where user_id in (select id from cadence_users where external_subject=?)",
          subject);
      jdbcTemplate.update("delete from cadence_users where external_subject=?", subject);
      for (var id : List.of(first, second))
        jdbcTemplate.update("delete from workspaces where id=?", id);
    }
  }

  @Test
  void migratesEmptyDatabaseAndValidatesJpaMappings() {
    Integer migrationCount =
        jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success", Integer.class);
    Integer mappedTableCount =
        jdbcTemplate.queryForObject(
            """
            select count(*) from information_schema.tables
            where table_schema = 'public'
              and table_name in ('workspaces', 'brands', 'strategies', 'social_posts',
                                 'post_metrics', 'recommendations', 'jobs', 'cadence_users',
                                 'brand_evidence', 'evidence_scan_results',
                                 'quarantine_write_intents', 'quarantine_objects', 'evidence_artifacts',
                                 'evidence_provider_calls', 'evidence_social_origins', 'provider_capacity_slots', 'brand_analysis_attempts', 'brand_identities')
            """,
            Integer.class);

    assertThat(migrationCount).isEqualTo(56);
    assertThat(mappedTableCount).isEqualTo(18);
  }

  @Test
  @org.springframework.transaction.annotation.Transactional(
      propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void rejectsForeignQuarantineKeysAndPartialIntentCompletion() {
    var workspaceId = java.util.UUID.randomUUID();
    var brandId = java.util.UUID.randomUUID();
    jdbcTemplate.update(
        "insert into workspaces (id,created_at,updated_at,name,industry,status) values (?,now(),now(),'Intent','Creator','ACTIVE')",
        workspaceId);
    jdbcTemplate.update(
        "insert into brands (id,created_at,updated_at,name,workspace_id) values (?,now(),now(),'Intent',?)",
        brandId,
        workspaceId);
    String key = "quarantine/" + brandId + "/" + "a".repeat(64) + ".pdf";
    var intentId = java.util.UUID.randomUUID();
    jdbcTemplate.update(
        "insert into quarantine_write_intents (id,brand_id,object_key,started_at) values (?,?,?,now())",
        intentId,
        brandId,
        key);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "insert into quarantine_write_intents (id,brand_id,object_key,started_at) values (?,?,?,now())",
                    java.util.UUID.randomUUID(),
                    brandId,
                    "quarantine/" + java.util.UUID.randomUUID() + "/" + "a".repeat(64) + ".pdf"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update quarantine_write_intents set registered_at = now() where id = ?",
                    intentId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from quarantine_write_intents where id = ? and registered_at is null and evidence_id is null",
                Integer.class,
                intentId))
        .isEqualTo(1);
    var evidenceId = java.util.UUID.randomUUID();
    jdbcTemplate.update(
        """
        insert into brand_evidence (id,created_at,updated_at,brand_id,type,status,source_reference,checksum,captured_at,original_file_name,media_type)
        values (?,now(),now(),?,'FILE','PENDING',?,?,now(),'source.pdf','application/pdf')
        """,
        evidenceId,
        brandId,
        "quarantine:" + key,
        "a".repeat(64));
    var wrongKeyIntent = java.util.UUID.randomUUID();
    jdbcTemplate.update(
        "insert into quarantine_write_intents (id,brand_id,object_key,started_at) values (?,?,?,now())",
        wrongKeyIntent,
        brandId,
        "quarantine/" + brandId + "/" + "b".repeat(64) + ".pdf");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update quarantine_write_intents set registered_at = now(), evidence_id = ? where id = ?",
                    evidenceId,
                    wrongKeyIntent))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbcTemplate.update(
        "update quarantine_write_intents set registered_at = now(), evidence_id = ? where id = ?",
        evidenceId,
        intentId);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update quarantine_write_intents set registered_at = now() where id = ?",
                    intentId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    jdbcTemplate.update(
        "delete from quarantine_write_intents where id in (?, ?)", intentId, wrongKeyIntent);
    jdbcTemplate.update("delete from brand_evidence where id = ?", evidenceId);
    jdbcTemplate.update("delete from brands where id = ?", brandId);
    jdbcTemplate.update("delete from workspaces where id = ?", workspaceId);
  }

  @Test
  void roundTripsEveryMappedAggregateAndOrderedValueCollection() {
    Workspace workspace = new Workspace("Acme Studio", "Software");
    entityManager.persist(workspace);

    Brand brand = new Brand("Acme Cloud", workspace);
    brand.updateSignature(
        new BrandSignature(
            new ColorPalette("#111111", "#222222", "#333333", "#EEEEEE", "#FFFFFF"),
            List.of("Inter", "Roboto"),
            List.of("clear", "warm"),
            List.of("vague", "cold"),
            "A sample caption"));
    entityManager.persist(brand);

    Strategy strategy =
        new Strategy(
            brand,
            1,
            new PublishingCadence(
                3, "Asia/Almaty", List.of(LocalTime.of(9, 0), LocalTime.of(18, 30))),
            List.of(
                new ContentPillar("Education", 60, "Teach the audience"),
                new ContentPillar("Product", 40, "Show the product")));
    entityManager.persist(strategy);

    SocialPost post =
        new SocialPost(
            strategy,
            "Launch",
            "Meet the new product",
            List.of("#launch", "#product"),
            PostFormat.CAROUSEL);
    entityManager.persist(post);

    PostMetric metric = new PostMetric(post, 1000, 120, 25, 8, 35);
    entityManager.persist(metric);

    Recommendation recommendation =
        new Recommendation(strategy, "Publish more educational posts", 0.85, "+10% reach");
    entityManager.persist(recommendation);

    entityManager.flush();
    var brandId = brand.getId();
    var strategyId = strategy.getId();
    var postId = post.getId();
    var metricId = metric.getId();
    var recommendationId = recommendation.getId();
    entityManager.clear();

    Brand storedBrand = entityManager.find(Brand.class, brandId);
    Strategy storedStrategy = entityManager.find(Strategy.class, strategyId);
    SocialPost storedPost = entityManager.find(SocialPost.class, postId);

    assertThat(storedBrand.getSignature().getFonts()).containsExactly("Inter", "Roboto");
    assertThat(storedStrategy.getContentPillars())
        .extracting(ContentPillar::getName)
        .containsExactly("Education", "Product");
    assertThat(storedStrategy.getCadence().getBestTimes())
        .containsExactly(LocalTime.of(9, 0), LocalTime.of(18, 30));
    assertThat(storedPost.getHashtags()).containsExactly("#launch", "#product");
    assertThat(entityManager.find(PostMetric.class, metricId).getReach()).isEqualTo(1000);
    assertThat(entityManager.find(Recommendation.class, recommendationId).getConfidence())
        .isEqualTo(0.85);

    PageRequest page = PageRequest.of(0, 10);
    assertThat(strategies.findAllByBrandWorkspaceId(workspace.getId(), page)).hasSize(1);
    assertThat(posts.findAllByStrategyBrandWorkspaceId(workspace.getId(), page)).hasSize(1);
    assertThat(metrics.findAllByPostStrategyBrandWorkspaceId(workspace.getId(), page)).hasSize(1);
    assertThat(recommendations.findAllByStrategyBrandWorkspaceId(workspace.getId(), page))
        .hasSize(1);
  }

  @Test
  void persistsFetchedEvidenceAndSelectsUrlJobsOnPostgres() {
    Workspace workspace = new Workspace("Fetch workspace", "Creator");
    entityManager.persist(workspace);
    Brand brand = new Brand("Fetch brand", workspace);
    entityManager.persist(brand);
    var evidence =
        new com.cadence.domain.BrandEvidence(
            brand,
            com.cadence.domain.EvidenceType.URL,
            "https://example.com/",
            "a".repeat(64),
            java.time.Instant.now());
    entityManager.persist(evidence);
    var job =
        new com.cadence.domain.DurableJob(
            "BRAND_EVIDENCE_INGEST",
            "evidence:" + evidence.getId(),
            "fetch:" + evidence.getId(),
            3,
            java.time.Instant.now().minusSeconds(1));
    entityManager.persist(job);
    entityManager.flush();
    assertThat(jobs.findUrlFetchCandidates(java.time.Instant.now(), PageRequest.of(0, 1)))
        .hasSize(1);
    assertThat(jobs.findEvidenceJobInWorkspaces(job.getId(), java.util.Set.of(workspace.getId())))
        .isPresent();
    assertThat(
            jobs.findEvidenceJobInWorkspaces(
                job.getId(), java.util.Set.of(java.util.UUID.randomUUID())))
        .isEmpty();
    var unsupported =
        new com.cadence.domain.DurableJob(
            "UNSUPPORTED",
            "evidence:" + evidence.getId(),
            "unsupported:" + evidence.getId(),
            3,
            java.time.Instant.now());
    entityManager.persist(unsupported);
    entityManager.flush();
    assertThat(
            jobs.findEvidenceJobInWorkspaces(
                unsupported.getId(), java.util.Set.of(workspace.getId())))
        .isEmpty();
    evidence.recordFetched(
        "quarantine:source", "b".repeat(64), "text/html", java.time.Instant.now());
    evidence.recordWebsiteMetadata(
        new com.cadence.domain.WebsiteMetadata("Title", "Description", "Text", "", "website-v1"),
        java.time.Instant.now());
    entityManager.flush();
    entityManager.clear();
    assertThat(
            entityManager
                .find(com.cadence.domain.BrandEvidence.class, evidence.getId())
                .getWebsiteMetadata()
                .title())
        .isEqualTo("Title");
    assertThat(
            entityManager
                .find(com.cadence.domain.BrandEvidence.class, evidence.getId())
                .getFetchedChecksum())
        .isEqualTo("b".repeat(64));
  }

  @Autowired private com.cadence.application.repositories.DurableJobRepository jobs;

  @Test
  void upgradesPopulatedV10AndRejectsForgedOrMutableScanProvenance() {
    String schema = "upgrade_v11";
    org.flywaydb.core.Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("10")
        .load()
        .migrate();
    var source =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var sql = new JdbcTemplate(source);
    var workspaceId = java.util.UUID.randomUUID();
    var brandId = java.util.UUID.randomUUID();
    var evidenceId = java.util.UUID.randomUUID();
    var jobId = java.util.UUID.randomUUID();
    sql.update(
        "insert into workspaces (id,created_at,updated_at,name,industry,status) values (?,now(),now(),'Scan','Creator','ACTIVE')",
        workspaceId);
    sql.update(
        "insert into brands (id,created_at,updated_at,name,workspace_id) values (?,now(),now(),'Scan',?)",
        brandId,
        workspaceId);
    sql.update(
        """
        insert into brand_evidence (id,created_at,updated_at,brand_id,type,status,source_reference,checksum,captured_at,original_file_name,media_type)
        values (?,now(),now(),?,'FILE','PENDING','quarantine:quarantine/file.pdf',?,now()-interval '1 minute','file.pdf','application/pdf')
        """,
        evidenceId,
        brandId,
        "a".repeat(64));
    sql.update(
        """
        insert into jobs (id,created_at,updated_at,type,payload_reference,idempotency_key,status,attempts,max_attempts,next_attempt_at,lease_owner,lease_expires_at)
        values (?,now(),now(),'BRAND_EVIDENCE_INGEST',?,'upgrade-scan','RUNNING',1,3,now(),'worker',now()+interval '2 minutes')
        """,
        jobId,
        "evidence:" + evidenceId);
    org.flywaydb.core.Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .load()
        .migrate();
    assertThat(sql.queryForObject("select count(*) from evidence_scan_results", Integer.class))
        .isZero();
    assertThat(
            sql.queryForObject(
                "select status from brand_evidence where id=?", String.class, evidenceId))
        .isEqualTo("PENDING");
    assertThat(
            sql.queryForList(
                "select version from flyway_schema_history where success and type='SQL' order by installed_rank",
                String.class))
        .containsExactly(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16",
            "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30",
            "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44",
            "45", "46", "47", "48", "49", "50", "51", "52", "53", "54", "55", "56");
    String insert =
        """
        insert into evidence_scan_results (id,created_at,updated_at,evidence_id,job_id,attempt_number,lease_owner,source_version,checksum,scanned_at,verdict,scanner_engine,signature_version)
        values (?,now(),now(),?,?,? ,?,0,?,now(),?,'test-engine','test-signatures')
        """;
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "worker",
                    "b".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    2,
                    "worker",
                    "a".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "other",
                    "a".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    java.util.UUID.randomUUID(),
                    jobId,
                    1,
                    "worker",
                    "a".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "worker",
                    "a".repeat(64),
                    "UNKNOWN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "worker",
                    "a".repeat(64),
                    null))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    String insertWithScannerMetadata = insert.replace("'test-engine','test-signatures'", "?,?");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insertWithScannerMetadata,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "worker",
                    "a".repeat(64),
                    "CLEAN",
                    "test-engine",
                    "bad\nsignature"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var resultId = java.util.UUID.randomUUID();
    assertThat(
            sql.update(insert, resultId, evidenceId, jobId, 1, "worker", "a".repeat(64), "CLEAN"))
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    1,
                    "worker",
                    "a".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    "update evidence_scan_results set verdict='INFECTED' where id=?", resultId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> sql.update("delete from jobs where id=?", jobId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    sql.update(
        "update jobs set lease_expires_at=now()-interval '1 second',attempts=2 where id=?", jobId);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    insert,
                    java.util.UUID.randomUUID(),
                    evidenceId,
                    jobId,
                    2,
                    "worker",
                    "a".repeat(64),
                    "CLEAN"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void upgradesPopulatedV9WithoutInventingMetadata() {
    String schema = "upgrade_v10";
    org.flywaydb.core.Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("9")
        .load()
        .migrate();
    var dataSource =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var sql = new JdbcTemplate(dataSource);
    var workspaceId = java.util.UUID.randomUUID();
    var brandId = java.util.UUID.randomUUID();
    var evidenceId = java.util.UUID.randomUUID();
    sql.update(
        "insert into workspaces (id,created_at,updated_at,name,industry,status) values (?,now(),now(),'Upgrade','Creator','ACTIVE')",
        workspaceId);
    sql.update(
        "insert into brands (id,created_at,updated_at,name,workspace_id) values (?,now(),now(),'Upgrade',?)",
        brandId,
        workspaceId);
    sql.update(
        """
        insert into brand_evidence (id,created_at,updated_at,brand_id,type,status,source_reference,
          checksum,captured_at,fetched_reference,fetched_checksum,fetched_media_type,fetched_at)
        values (?,now(),now(),?,'URL','FETCHED','https://example.com',?,now(),'quarantine:existing',?,'text/html',now())
        """,
        evidenceId,
        brandId,
        "a".repeat(64),
        "b".repeat(64));
    org.flywaydb.core.Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .target("10")
        .load()
        .migrate();
    assertThat(
            sql.queryForObject(
                "select fetched_reference from brand_evidence where id = ?",
                String.class,
                evidenceId))
        .isEqualTo("quarantine:existing");
    assertThat(
            sql.queryForObject(
                "select extraction_version from brand_evidence where id = ?",
                String.class,
                evidenceId))
        .isNull();
    // Flyway also records schema creation; assert the actual SQL migration versions.
    assertThat(
            sql.queryForList(
                "select version from flyway_schema_history where success and type = 'SQL' order by installed_rank",
                String.class))
        .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    "update brand_evidence set extraction_version = 'partial' where id = ?",
                    evidenceId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                sql.update(
                    """
        update brand_evidence set extracted_title='',extracted_description='',extracted_text='',
          extraction_warnings='',extraction_version='v1',extracted_at=fetched_at - interval '1 second' where id=?
        """,
                    evidenceId))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsPartialExtractionSnapshot() {
    Workspace workspace = new Workspace("Metadata workspace", "Creator");
    entityManager.persist(workspace);
    Brand brand = new Brand("Metadata brand", workspace);
    entityManager.persist(brand);
    var item =
        new com.cadence.domain.BrandEvidence(
            brand,
            com.cadence.domain.EvidenceType.URL,
            "https://example.com",
            "d".repeat(64),
            java.time.Instant.now());
    item.recordFetched("quarantine:test", "e".repeat(64), "text/html", java.time.Instant.now());
    entityManager.persist(item);
    entityManager.flush();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update brand_evidence set extracted_title = 'partial' where id = ?",
                    item.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsFetchedStatusWithoutQuarantineMetadata() {
    Workspace workspace = new Workspace("Constraint workspace", "Creator");
    entityManager.persist(workspace);
    Brand brand = new Brand("Constraint brand", workspace);
    entityManager.persist(brand);
    var evidence =
        new com.cadence.domain.BrandEvidence(
            brand,
            com.cadence.domain.EvidenceType.URL,
            "https://example.com/",
            "c".repeat(64),
            java.time.Instant.now());
    entityManager.persist(evidence);
    entityManager.flush();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update brand_evidence set status = 'FETCHED' where id = ?", evidence.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }
}
