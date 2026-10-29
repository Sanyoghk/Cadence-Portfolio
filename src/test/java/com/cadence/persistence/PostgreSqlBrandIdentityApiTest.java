package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.BrandIdentityApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles({"test", "postgres-test"})
class PostgreSqlBrandIdentityApiTest extends BrandIdentityApiContract {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @DynamicPropertySource
  static void db(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    p.add("spring.datasource.username", POSTGRES::getUsername);
    p.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired javax.sql.DataSource datasource;

  @Test
  void databaseRejectsForgedReceiptLinksAndOriginalContentEvenWithoutApplicationChecks()
      throws Exception {
    var analysisId = successfulAnalysis();
    var value =
        useCase.createFromAnalysis(
            brand.getId(), Set.of(workspace.getId()), analysisId, brand.getLockVersion());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set analysis_attempt_id=null where id=?", value.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var manual = useCase.create(brand.getId(), Set.of(workspace.getId()), content("Manual"));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set analysis_attempt_id=? where id=?",
                    analysisId,
                    manual.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (var forgedCaption : List.of("Forged", "Declared Māori \uD83D\uDE00 caption")) {
      var forgedId = UUID.randomUUID();
      var foreign = forgedCaption.equals("Declared Māori \uD83D\uDE00 caption");
      var other = brands.saveAndFlush(new com.cadence.domain.Brand("Other", workspace));
      assertThatThrownBy(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(manager)
                      .executeWithoutResult(
                          tx -> {
                            jdbc.update(
                                "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption,analysis_attempt_id) values(?,?,?,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5',?,?)",
                                forgedId,
                                foreign ? other.getId() : brand.getId(),
                                foreign ? 1 : 3,
                                forgedCaption,
                                analysisId);
                            for (var table :
                                List.of(
                                    "identity_fonts", "identity_voice_up", "identity_voice_down"))
                              jdbc.update(
                                  "insert into "
                                      + table
                                      + "(identity_id,item_order,item_value) select ?,item_order,item_value from "
                                      + table
                                      + " where identity_id=?",
                                  forgedId,
                                  value.id());
                          }))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    assertThat(
            useCase
                .findById(value.id(), Set.of(workspace.getId()))
                .analysisProvenance()
                .analysisId())
        .isEqualTo(analysisId);
  }

  @Test
  void databaseRejectsForgedApprovalActorsAuditContentAndLineage() throws Exception {
    var id = create();
    var outsider =
        users.saveAndFlush(
            new com.cadence.domain.CadenceUser(
                "outsider-" + UUID.randomUUID(), com.cadence.domain.UserRole.OWNER, Set.of()));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=now() where id=?",
                    outsider.getId(),
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=created_at-interval '1 second' where id=?",
                    actorId,
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set status='APPROVED',approved_by=?,approved_at=now(),sample_caption='Unreviewed' where id=?",
                    actorId,
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    SecurityContextHolder.getContext().setAuthentication(owner);
    var approved = useCase.approve(id, Set.of(workspace.getId()), 0, actorId);
    for (var sql :
        List.of(
            "update brand_identities set sample_caption='Tampered' where id=?",
            "update brand_identities set primary_color='#112233' where id=?",
            "update brand_identities set approved_at=now() where id=?",
            "update brand_identities set status='DRAFT',approved_by=null,approved_at=null where id=?",
            "update identity_fonts set item_value='Tampered' where identity_id=?",
            "delete from identity_voice_up where identity_id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var branch =
        useCase
            .update(id, Set.of(workspace.getId()), approved.lockVersion(), content("Branch"))
            .details();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_identities set revised_from_id=null where id=?", branch.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var other = brands.saveAndFlush(new com.cadence.domain.Brand("Other", workspace));
    for (var source : List.of(id, branch.id(), UUID.randomUUID())) {
      var targetBrand = source.equals(id) ? other.getId() : brand.getId();
      int version = source.equals(id) ? 1 : 3;
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption,revised_from_id) values(?,?,?,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Forged',?)",
                      UUID.randomUUID(),
                      targetBrand,
                      version,
                      source))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    assertThat(useCase.findById(id, Set.of(workspace.getId())).content().sampleCaption())
        .isEqualTo("Creator caption");
  }

  @Test
  void concurrentEditAndApprovalAcceptExactlyOneToken() throws Exception {
    var id = create();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var edit =
          executor.submit(
              () ->
                  attempt(
                      start,
                      () -> useCase.update(id, Set.of(workspace.getId()), 0, content("Edited"))));
      var approval =
          executor.submit(
              () ->
                  attempt(start, () -> useCase.approve(id, Set.of(workspace.getId()), 0, actorId)));
      start.countDown();
      assertThat(List.of(edit.get(15, TimeUnit.SECONDS), approval.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
    assertThat(identities.findById(id).orElseThrow().getLockVersion()).isEqualTo(1);
  }

  @Test
  void concurrentApprovedEditsAllocateDistinctBranchesAndLockedHistoryStaysImmutable()
      throws Exception {
    var id = create();
    SecurityContextHolder.getContext().setAuthentication(owner);
    useCase.approve(id, Set.of(workspace.getId()), 0, actorId);
    jdbc.update(
        "update brand_identities set status='LOCKED',lock_version=lock_version+1 where id=?", id);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Integer> branch =
          () -> {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            SecurityContextHolder.getContext().setAuthentication(owner);
            try {
              return useCase
                  .update(id, Set.of(workspace.getId()), 2, content("Branch"))
                  .details()
                  .version();
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = executor.submit(branch);
      var b = executor.submit(branch);
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(2, 3);
    }
    assertThat(useCase.findById(id, Set.of(workspace.getId())).status())
        .isEqualTo(com.cadence.domain.ApprovalStatus.LOCKED);
    assertThat(useCase.findById(id, Set.of(workspace.getId())).content().sampleCaption())
        .isEqualTo("Creator caption");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where revised_from_id=?", Integer.class, id))
        .isEqualTo(2);
  }

  private boolean attempt(CountDownLatch start, Runnable action) throws Exception {
    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
    SecurityContextHolder.getContext().setAuthentication(owner);
    try {
      action.run();
      return true;
    } catch (IllegalStateException conflict) {
      return false;
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"activity", "membership"})
  void approvalWaitsForAccountRevocationBeforeLockingParents(String kind) throws Exception {
    var id = create();
    try (var connection = datasource.getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var revoke =
          connection.prepareStatement(
              kind.equals("activity")
                  ? "update cadence_users set active=false,lock_version=lock_version+1 where id=?"
                  : "delete from workspace_memberships where user_id=?")) {
        revoke.setObject(1, actorId);
        revoke.executeUpdate();
      }
      var approval =
          executor.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  assertThatThrownBy(
                          () -> useCase.approve(id, Set.of(workspace.getId()), 0, actorId))
                      .isInstanceOf(
                          org.springframework.security.access.AccessDeniedException.class);
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
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
        assertThat(waiting).as("Account lock precedes Workspace and Identity locks").isTrue();
        for (var query :
            List.of(
                "select id from workspaces where id=? for update nowait",
                "select id from brand_identities where id=? for update nowait")) {
          try (var statement = connection.prepareStatement(query)) {
            statement.setObject(1, query.contains("workspaces") ? workspace.getId() : id);
            try (var rows = statement.executeQuery()) {
              assertThat(rows.next()).isTrue();
            }
          }
        }
      } finally {
        connection.commit();
      }
      approval.get(15, TimeUnit.SECONDS);
    }
    assertThat(identities.findById(id).orElseThrow().getStatus())
        .isEqualTo(com.cadence.domain.ApprovalStatus.DRAFT);
  }

  @Test
  void databaseRejectsIncompleteRootsWrongAllocationAndCollectionReparenting() throws Exception {
    var first = create();
    var second = create();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update identity_fonts set identity_id=? where identity_id=?", second, first))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (int version : List.of(3, 99)) {
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,?,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Incomplete')",
                      UUID.randomUUID(),
                      brand.getId(),
                      version))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Integer.class,
                brand.getId()))
        .isEqualTo(2);
  }

  @Test
  void enforcesLifetimeRevisionLimitInApplicationAndDatabase() throws Exception {
    try (var connection = datasource.getConnection()) {
      connection.setAutoCommit(false);
      try (var root =
              connection.prepareStatement(
                  "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,?,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Bounded')");
          var font = connection.prepareStatement("insert into identity_fonts values(?,0,'Inter')");
          var up =
              connection.prepareStatement("insert into identity_voice_up values(?,0,'clear')");
          var down =
              connection.prepareStatement("insert into identity_voice_down values(?,0,'spam')")) {
        for (int version = 1; version <= 1000; version++) {
          var id = UUID.randomUUID();
          root.setObject(1, id);
          root.setObject(2, brand.getId());
          root.setInt(3, version);
          root.executeUpdate();
          for (var collection : List.of(font, up, down)) {
            collection.setObject(1, id);
            collection.executeUpdate();
          }
        }
        connection.commit();
      }
    }
    SecurityContextHolder.getContext().setAuthentication(owner);
    var firstId =
        jdbc.queryForObject(
            "select id from brand_identities where brand_id=? order by version limit 1",
            UUID.class,
            brand.getId());
    useCase.approve(firstId, Set.of(workspace.getId()), 0, actorId);
    assertThatThrownBy(
            () -> useCase.update(firstId, Set.of(workspace.getId()), 1, content("Denied branch")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("limit");
    assertThatThrownBy(
            () -> useCase.create(brand.getId(), Set.of(workspace.getId()), content("Denied")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("limit");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into brand_identities(id,brand_id,version,status,created_at,updated_at,primary_color,secondary_color,accent_color,neutral_color,background_color,sample_caption) values(?,?,1001,'DRAFT',now(),now(),'#000000','#FFFFFF','#FF0000','#808080','#F5F5F5','Denied')",
                    UUID.randomUUID(),
                    brand.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from brand_identities where brand_id=?",
                Integer.class,
                brand.getId()))
        .isEqualTo(1000);
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"create", "update", "approve", "fromAnalysis"})
  void waitsForWorkspaceArchiveBeforeLockingIdentity(String operation) throws Exception {
    var analysisId = operation.equals("fromAnalysis") ? successfulAnalysis() : null;
    var id = create();
    try (var connection = datasource.getConnection();
        var executor = Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "update workspaces set status='ARCHIVED',lock_version=lock_version+1 where id=?")) {
        statement.setObject(1, workspace.getId());
        statement.executeUpdate();
      }
      var attempt =
          executor.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  assertThatThrownBy(
                          () -> {
                            if (operation.equals("create"))
                              useCase.create(
                                  brand.getId(), Set.of(workspace.getId()), content("Denied"));
                            else if (operation.equals("fromAnalysis"))
                              useCase.createFromAnalysis(
                                  brand.getId(),
                                  Set.of(workspace.getId()),
                                  analysisId,
                                  brand.getLockVersion());
                            else if (operation.equals("approve"))
                              useCase.approve(id, Set.of(workspace.getId()), 0, actorId);
                            else
                              useCase.update(id, Set.of(workspace.getId()), 0, content("Denied"));
                          })
                      .isInstanceOf(IllegalStateException.class)
                      .hasMessageContaining("archived");
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          if (jdbc.queryForObject(
                  "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%workspaces%'",
                  Integer.class)
              > 0) {
            waiting = true;
            break;
          }
          Thread.sleep(20);
        }
        assertThat(waiting).as("Workspace lock precedes Identity lock").isTrue();
        try (var statement =
            connection.prepareStatement(
                "select id from brand_identities where id=? for update nowait")) {
          statement.setObject(1, id);
          try (var row = statement.executeQuery()) {
            assertThat(row.next()).isTrue();
          }
        }
      } finally {
        connection.commit();
      }
      attempt.get(15, TimeUnit.SECONDS);
    }
    assertThat(identities.findById(id).orElseThrow().getLockVersion()).isZero();
  }

  @Test
  void databaseProtectsIdentityMetadataStatusCollectionsAndHistory() throws Exception {
    var id = create();
    for (var sql :
        List.of(
            "update brand_identities set status='APPROVED' where id=?",
            "update brand_identities set version=99 where id=?",
            "delete from brand_identities where id=?",
            "update brand_identities set primary_color='red' where id=?",
            "update brand_identities set sample_caption=' ' where id=?",
            "update brand_identities set sample_caption=chr(12288) where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (var sql :
        List.of(
            "delete from identity_fonts where identity_id=?",
            "update identity_voice_up set item_order=2 where identity_id=?",
            "update identity_voice_down set item_value='bad'||chr(133) where identity_id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, id))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from identity_fonts where identity_id=?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  void concurrentCreatesAllocateDistinctVersionsAndEditsAcceptOneToken() throws Exception {
    var scope = Set.of(workspace.getId());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Integer> create =
          () -> {
            assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
            SecurityContextHolder.getContext().setAuthentication(owner);
            try {
              return useCase.create(brand.getId(), scope, content("Created")).version();
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = executor.submit(create);
      var b = executor.submit(create);
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(1, 2);
      var id = identities.findTopByBrandIdOrderByVersionDesc(brand.getId()).orElseThrow().getId();
      var editStart = new CountDownLatch(1);
      Callable<Boolean> edit =
          () -> {
            assertThat(editStart.await(10, TimeUnit.SECONDS)).isTrue();
            SecurityContextHolder.getContext().setAuthentication(owner);
            try {
              useCase.update(id, scope, 0, content("Edited"));
              return true;
            } catch (IllegalStateException conflict) {
              return false;
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var c = executor.submit(edit);
      var d = executor.submit(edit);
      editStart.countDown();
      assertThat(List.of(c.get(15, TimeUnit.SECONDS), d.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    }
  }
}
