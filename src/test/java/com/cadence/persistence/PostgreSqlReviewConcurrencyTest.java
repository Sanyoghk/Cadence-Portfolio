package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.cadence.application.exceptions.WorkspaceQuotaExceededException;
import com.cadence.application.repositories.*;
import com.cadence.application.services.EvidenceOwnershipLocks;
import com.cadence.application.usecases.*;
import com.cadence.domain.*;
import com.cadence.security.CadencePrincipal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "cadence.workspaces.max-per-account=3")
@AutoConfigureMockMvc
@ActiveProfiles("postgres-test")
class PostgreSqlReviewConcurrencyTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired WorkspaceManagementUseCase workspaceService;
  @Autowired BrandManagementUseCase brandService;
  @Autowired WorkspaceRepository workspaces;
  @Autowired BrandRepository brands;
  @Autowired CadenceUserRepository users;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired DurableJobRepository jobs;
  @Autowired EvidenceOwnershipLocks ownership;
  @Autowired DataSource datasource;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @Autowired MockMvc mvc;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.cadence.application.ports.EvidenceObjectStorage storage;

  @org.springframework.test.context.bean.override.mockito.MockitoBean
  com.cadence.application.ports.EvidenceMalwareScanner scanner;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void workspaceAdmissionRejectsAnInheritedRepeatableReadSnapshot() {
    var user =
        users.saveAndFlush(new CadenceUser(UUID.randomUUID().toString(), UserRole.OWNER, Set.of()));
    SecurityContextHolder.getContext().setAuthentication(owner(user.getId(), Set.of()));
    var transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
    assertThatThrownBy(
            () ->
                transaction.execute(
                    status -> workspaceService.create(user.getId(), "Rejected", "Creator")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("READ_COMMITTED");
    assertThat(users.countWorkspaceMemberships(user.getId())).isZero();
  }

  @Test
  void accountQuotaSerializesConcurrentCreationAndArchivingDoesNotResetIt() throws Exception {
    var first = workspaces.saveAndFlush(new Workspace("First", "Creator"));
    first.archive();
    workspaces.saveAndFlush(first);
    var second = workspaces.saveAndFlush(new Workspace("Second", "Creator"));
    var owner =
        users.saveAndFlush(
            new CadenceUser(UUID.randomUUID().toString(), UserRole.OWNER, Set.of(first, second)));
    var auth = owner(owner.getId(), Set.of(first.getId(), second.getId()));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      Callable<Boolean> create =
          () -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            try {
              assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
              workspaceService.create(owner.getId(), "Concurrent", "Creator");
              return true;
            } catch (WorkspaceQuotaExceededException expected) {
              return false;
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = executor.submit(create);
      var b = executor.submit(create);
      start.countDown();
      assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(users.countWorkspaceMemberships(owner.getId())).isEqualTo(3);
    mvc.perform(
            post("/api/v1/workspaces")
                .with(authentication(auth))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Excess\",\"industry\":\"Creator\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Cache-Control", "no-store"));
    assertThat(users.countWorkspaceMemberships(owner.getId())).isEqualTo(3);
  }

  @Test
  void databaseBudgetAlsoRejectsConcurrentDirectInsertsAndRollsBackCounter() throws Exception {
    List<Workspace> assigned = new ArrayList<>();
    for (int i = 0; i < 9; i++)
      assigned.add(workspaces.saveAndFlush(new Workspace("Assigned", "Creator")));
    var owner =
        users.saveAndFlush(new CadenceUser(UUID.randomUUID().toString(), UserRole.OWNER, assigned));
    var a = workspaces.saveAndFlush(new Workspace("Candidate A", "Creator"));
    var b = workspaces.saveAndFlush(new Workspace("Candidate B", "Creator"));
    var start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var left = executor.submit(() -> insertMembership(owner.getId(), a.getId(), start));
      var right = executor.submit(() -> insertMembership(owner.getId(), b.getId(), start));
      start.countDown();
      assertThat(List.of(left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(users.countWorkspaceMemberships(owner.getId())).isEqualTo(10);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE cadence_users SET workspace_membership_count=0 WHERE id=?",
                    owner.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

    assertThat(
            jdbc.queryForObject(
                "SELECT workspace_membership_count FROM cadence_users WHERE id=?",
                Integer.class,
                owner.getId()))
        .isEqualTo(10);
    try (var connection = datasource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM workspace_memberships WHERE user_id=? AND workspace_id=?")) {
        statement.setObject(1, owner.getId());
        statement.setObject(2, assigned.getFirst().getId());
        statement.executeUpdate();
      }
      connection.rollback();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT workspace_membership_count FROM cadence_users WHERE id=?",
                Integer.class,
                owner.getId()))
        .isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void brandMutationWaitsForWorkspaceArchiveAndRechecksState(boolean update) throws Exception {
    var workspace = workspaces.saveAndFlush(new Workspace("Archiving", "Creator"));
    var brand = brands.saveAndFlush(new Brand("Original", workspace));
    var auth = owner(UUID.randomUUID(), Set.of(workspace.getId()));
    try (var connection = datasource.getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "UPDATE workspaces SET status='ARCHIVED',lock_version=lock_version+1 WHERE id=?")) {
        statement.setObject(1, workspace.getId());
        statement.executeUpdate();
      }
      var attempt =
          executor.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(auth);
                try {
                  assertThatThrownBy(
                          () -> {
                            if (update)
                              brandService.update(
                                  brand.getId(),
                                  Set.of(workspace.getId()),
                                  0,
                                  "Changed",
                                  null,
                                  null,
                                  null);
                            else
                              brandService.create(
                                  workspace.getId(),
                                  Set.of(workspace.getId()),
                                  "Added",
                                  null,
                                  null,
                                  null);
                          })
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("archived");
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        awaitWorkspaceWaiter();
      } finally {
        connection.commit();
      }
      attempt.get(20, TimeUnit.SECONDS);
    }
    assertThat(brands.findById(brand.getId()).orElseThrow().getName()).isEqualTo("Original");
    assertThat(
            brands
                .findAllByWorkspaceId(
                    workspace.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .getTotalElements())
        .isEqualTo(1);
  }

  @Test
  void evidenceWorkerDoesNotLockBrandBeforeWaitingForWorkspace() throws Exception {
    var workspace = workspaces.saveAndFlush(new Workspace("Lock order", "Creator"));
    var brand = brands.saveAndFlush(new Brand("Brand", workspace));
    var item =
        evidence.saveAndFlush(
            new BrandEvidence(
                brand, EvidenceType.URL, "https://example.com/", "a".repeat(64), Instant.now()));
    var job =
        jobs.saveAndFlush(
            new DurableJob(
                "BRAND_EVIDENCE_INGEST",
                "evidence:" + item.getId(),
                "lock-order:" + item.getId(),
                3,
                Instant.now().minusSeconds(1)));
    try (var connection = datasource.getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      lock(connection, "workspaces", workspace.getId());
      var worker =
          executor.submit(
              () ->
                  new TransactionTemplate(manager)
                      .execute(
                          status -> {
                            assertThat(
                                    jobs.findUrlFetchCandidates(
                                        Instant.now(),
                                        org.springframework.data.domain.PageRequest.of(0, 1)))
                                .extracting(DurableJob::getId)
                                .containsExactly(job.getId());
                            return ownership.findLocked(item.getId()).orElseThrow().getId();
                          }));
      try {
        awaitWorkspaceWaiter();
        // A generation/brand operation holding Workspace must still be able to acquire Brand.
        lock(connection, "brands", brand.getId());
        lock(connection, "brand_evidence", item.getId());
      } finally {
        connection.commit();
      }
      assertThat(worker.get(20, TimeUnit.SECONDS)).isEqualTo(item.getId());
    }
  }

  private boolean insertMembership(UUID user, UUID workspace, CountDownLatch start)
      throws Exception {
    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
    try (var connection = datasource.getConnection();
        var statement =
            connection.prepareStatement(
                "INSERT INTO workspace_memberships(user_id,workspace_id) VALUES (?,?)")) {
      statement.setObject(1, user);
      statement.setObject(2, workspace);
      statement.executeUpdate();
      return true;
    } catch (SQLException failure) {
      assertThat(failure.getSQLState()).isEqualTo("23514");
      return false;
    }
  }

  private void lock(Connection connection, String table, UUID id) throws SQLException {
    String sql =
        switch (table) {
          case "workspaces" -> "SELECT id FROM workspaces WHERE id=? FOR UPDATE NOWAIT";
          case "brands" -> "SELECT id FROM brands WHERE id=? FOR UPDATE NOWAIT";
          case "brand_evidence" -> "SELECT id FROM brand_evidence WHERE id=? FOR UPDATE NOWAIT";
          default -> throw new IllegalArgumentException();
        };
    try (var statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      try (var rows = statement.executeQuery()) {
        assertThat(rows.next()).isTrue();
      }
    }
  }

  private void awaitWorkspaceWaiter() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    do {
      if (jdbc.queryForObject(
              "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query ILIKE '%workspaces%'",
              Integer.class)
          > 0) return;
      Thread.sleep(20);
    } while (System.nanoTime() < deadline);
    throw new AssertionError("Operation must wait for the workspace lock");
  }

  private Authentication owner(UUID id, Set<UUID> workspaces) {
    return UsernamePasswordAuthenticationToken.authenticated(
        new CadencePrincipal(id, "review", UserRole.OWNER, workspaces),
        null,
        List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
  }
}
