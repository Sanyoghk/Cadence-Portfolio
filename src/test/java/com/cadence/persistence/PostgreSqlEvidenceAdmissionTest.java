package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import com.cadence.support.EvidenceAdmissionApiContract;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;

@ActiveProfiles("postgres-test")
class PostgreSqlEvidenceAdmissionTest extends EvidenceAdmissionApiContract {
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
  void changedContextCannotBypassWithdrawalThroughRawSql() {
    var a = admit();
    reviews.withdraw(source.getId(), a.id(), Set.of(workspace.getId()), actor.getId());
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(() -> insertReview(UUID.randomUUID(), 1))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
  }

  private void insertReview(UUID id, long brandVersion) {
    jdbc.update(
        "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,?,0,?,?,?,?,now(),'text-review-v1',now(),now())",
        id,
        source.getId(),
        brandVersion,
        source.getChecksum(),
        com.cadence.domain.EvidenceAdmission.checksum(text),
        text,
        actor.getId());
  }

  @Test
  void reReviewWaitsForWithdrawalAndRejectsAfterCommit() throws Exception {
    var old = admit();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,now(),now(),now())")) {
        s.setObject(1, UUID.randomUUID());
        s.setObject(2, old.id());
        s.setObject(3, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return reviews.admit(
                      source.getId(),
                      Set.of(workspace.getId()),
                      actor.getId(),
                      0,
                      1,
                      0,
                      com.cadence.domain.EvidenceAdmission.checksum(text));
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertAccountBlocked();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(8, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1);
    }
  }

  @Test
  void withdrawalAfterConcurrentReReviewRevokesBothReceipts() throws Exception {
    var old = admit();
    var next = UUID.randomUUID();
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,1,0,?,?,?,?,now(),'text-review-v1',now(),now())")) {
        s.setObject(1, next);
        s.setObject(2, source.getId());
        s.setString(3, source.getChecksum());
        s.setString(4, com.cadence.domain.EvidenceAdmission.checksum(text));
        s.setString(5, text);
        s.setObject(6, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return reviews.withdraw(
                      source.getId(), old.id(), Set.of(workspace.getId()), actor.getId());
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        assertAccountBlocked();
      } finally {
        c.commit();
      }
      assertThat(future.get(8, TimeUnit.SECONDS).admissionId()).isEqualTo(old.id());
      assertThatThrownBy(
              () ->
                  reviews.snapshot(source.getId(), next, Set.of(workspace.getId()), actor.getId()))
          .isInstanceOf(IllegalStateException.class);
      assertThat(admissions.countForEvidence(source.getId())).isEqualTo(2);
    }
  }

  private void assertAccountBlocked() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
    while (System.nanoTime() < deadline) {
      if (jdbc.queryForObject(
              "select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query ilike '%cadence_users%'",
              Integer.class)
          > 0) return;
      Thread.sleep(20);
    }
    fail("Operation must wait for Account lock before parent locks");
  }

  @Test
  void immutableHistoryLimitBlocksFurtherReviewInApplicationAndDatabase() throws Exception {
    try (var c = datasource.getConnection()) {
      c.setAutoCommit(false);
      var lockQueries =
          List.of(
              "select id from cadence_users where id=? for update",
              "select id from workspaces where id=? for update",
              "select id from brands where id=? for update",
              "select id from brand_evidence where id=? for update");
      var ids = List.of(actor.getId(), workspace.getId(), brand.getId(), source.getId());
      for (int i = 0; i < lockQueries.size(); i++) {
        try (var statement = c.prepareStatement(lockQueries.get(i))) {
          statement.setObject(1, ids.get(i));
          statement.executeQuery().close();
        }
      }
      try (var update = c.prepareStatement("update brands set lock_version=? where id=?");
          var insert =
              c.prepareStatement(
                  "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,?,0,?,?,?,?,clock_timestamp(),'text-review-v1',clock_timestamp(),clock_timestamp())")) {
        for (int i = 1; i <= com.cadence.domain.EvidenceAdmission.MAX_REVIEWS_PER_EVIDENCE; i++) {
          update.setInt(1, i);
          update.setObject(2, brand.getId());
          update.executeUpdate();
          insert.setObject(1, UUID.randomUUID());
          insert.setObject(2, source.getId());
          insert.setInt(3, i);
          insert.setString(4, source.getChecksum());
          insert.setString(5, com.cadence.domain.EvidenceAdmission.checksum(text));
          insert.setString(6, text);
          insert.setObject(7, actor.getId());
          insert.executeUpdate();
        }
      }
      c.commit();
    }
    assertThat(admissions.countForEvidence(source.getId())).isEqualTo(1000);
    jdbc.update("update brands set lock_version=lock_version+1 where id=?", brand.getId());
    assertThatThrownBy(
            () ->
                reviews.admit(
                    source.getId(),
                    Set.of(workspace.getId()),
                    actor.getId(),
                    0,
                    1001,
                    0,
                    com.cadence.domain.EvidenceAdmission.checksum(text)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Evidence review history limit reached");
    assertThatThrownBy(() -> insertReview(UUID.randomUUID(), 1001))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseForbidsReparentingEvidenceOrBrandToAnotherVersionZeroTenant() {
    var a = admit();
    var otherWorkspace =
        workspaces.saveAndFlush(new com.cadence.domain.Workspace("Other", "Creator"));
    var otherBrand = brands.saveAndFlush(new com.cadence.domain.Brand("Other", otherWorkspace));
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brand_evidence set brand_id=? where id=?",
                    otherBrand.getId(),
                    source.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update brands set workspace_id=? where id=?",
                    otherWorkspace.getId(),
                    brand.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            reviews
                .snapshot(source.getId(), a.id(), Set.of(workspace.getId()), actor.getId())
                .text())
        .isEqualTo(text);
  }

  @Test
  void withdrawalSqlGuardsActorTimeAndImmutableHistory() {
    var a = admit();
    String insert =
        "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,?::timestamptz,now(),now())";
    var foreign =
        users.saveAndFlush(
            new com.cadence.domain.CadenceUser(
                "foreign-" + UUID.randomUUID(), com.cadence.domain.UserRole.OWNER, Set.of()));
    for (var time : List.of("2000-01-01T00:00:00Z", "2099-01-01T00:00:00Z"))
      assertThatThrownBy(() -> jdbc.update(insert, UUID.randomUUID(), a.id(), actor.getId(), time))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    a.id(),
                    foreign.getId(),
                    java.time.Instant.now().toString()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    var w = reviews.withdraw(source.getId(), a.id(), Set.of(workspace.getId()), actor.getId());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update evidence_admission_withdrawals set withdrawn_by=? where id=?",
                    foreign.getId(),
                    w.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> jdbc.update("delete from evidence_admission_withdrawals where id=?", w.id()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    insert,
                    UUID.randomUUID(),
                    a.id(),
                    actor.getId(),
                    java.time.Instant.now().toString()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(admissions.findById(a.id()).orElseThrow().getReviewedText()).isEqualTo(text);
  }

  @Test
  void snapshotWaitsForConcurrentWithdrawalAndThenRejectsCommittedRevocation() throws Exception {
    var a = admit();
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,now(),now(),now())")) {
        s.setObject(1, UUID.randomUUID());
        s.setObject(2, a.id());
        s.setObject(3, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return reviews.snapshot(
                      source.getId(), a.id(), Set.of(workspace.getId()), actor.getId());
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
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
        assertThat(waiting).as("Snapshot waits for withdrawal's account lock").isTrue();
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(8, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
    }
  }

  @Test
  void databaseRejectsWhitespaceOnlyAdmissionEvenWithMatchingForgedSourceDigest() {
    String blank = " \t\n\u3000";
    jdbc.update(
        "update brand_evidence set source_reference=?,checksum=? where id=?",
        blank,
        com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + blank),
        source.getId());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,0,0,?,?,?,?,now(),'text-review-v1',now(),now())",
                    UUID.randomUUID(),
                    source.getId(),
                    com.cadence.domain.EvidenceAdmission.checksum("TEXT\n" + blank),
                    com.cadence.domain.EvidenceAdmission.checksum(blank),
                    blank,
                    actor.getId()))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void restrictedRuntimeCanInsertValidReviewButCannotRewriteOrDeleteIt() throws Exception {
    var sql =
        "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,0,0,0,?,?,?,?,now(),'text-review-v1',now(),now())";
    var id = UUID.randomUUID();
    // SET ROLE belongs to a dedicated connection; never return a changed role to Hikari's pool.
    try (var c =
        java.sql.DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var s = c.createStatement()) {
        s.execute("create role cadence_runtime nologin");
        s.execute("grant usage on schema public to cadence_runtime");
        s.execute("grant select,insert,update on all tables in schema public to cadence_runtime");
      }
      org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(
          c,
          new org.springframework.core.io.FileSystemResource(
              "docker/postgres/runtime-permissions.sql"));
      try (var s = c.createStatement()) {
        s.execute("set role cadence_runtime");
      }
      try (var s = c.prepareStatement(sql)) {
        s.setObject(1, id);
        s.setObject(2, source.getId());
        s.setString(3, source.getChecksum());
        s.setString(4, com.cadence.domain.EvidenceAdmission.checksum(text));
        s.setString(5, text);
        s.setObject(6, actor.getId());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      try (var s =
          c.prepareStatement(
              "insert into evidence_admission_withdrawals(id,admission_id,withdrawn_by,withdrawn_at,created_at,updated_at) values(?,?,?,now(),now(),now())")) {
        s.setObject(1, UUID.randomUUID());
        s.setObject(2, id);
        s.setObject(3, actor.getId());
        assertThat(s.executeUpdate()).isEqualTo(1);
      }
      for (var query :
          List.of(
              "update evidence_admissions set reviewed_text='Forged' where id=?",
              "delete from evidence_admissions where id=?",
              "update evidence_admission_withdrawals set withdrawn_at=now() where admission_id=?",
              "delete from evidence_admission_withdrawals where admission_id=?")) {
        try (var s = c.prepareStatement(query)) {
          s.setObject(1, id);
          assertThatThrownBy(s::executeUpdate)
              .isInstanceOf(java.sql.SQLException.class)
              .satisfies(
                  e -> assertThat(((java.sql.SQLException) e).getSQLState()).isEqualTo("42501"));
        }
      }
    }
    assertThat(admissions.findById(id).orElseThrow().getReviewedText()).isEqualTo(text);
  }

  @Test
  void admissionWaitsForAccountRevocationAndRejectsItAfterCommit() throws Exception {
    revoke("activity");
  }

  @Test
  void admissionWaitsForMembershipRevocationAndRejectsItAfterCommit() throws Exception {
    revoke("membership");
  }

  private void revoke(String kind) throws Exception {
    try (var c = datasource.getConnection();
        var pool = Executors.newSingleThreadExecutor()) {
      c.setAutoCommit(false);
      try (var s = c.prepareStatement("select id from cadence_users where id=? for update")) {
        s.setObject(1, actor.getId());
        s.executeQuery().close();
      }
      try (var s =
          c.prepareStatement(
              kind.equals("activity")
                  ? "update cadence_users set active=false,lock_version=lock_version+1 where id=?"
                  : "delete from workspace_memberships where user_id=?")) {
        s.setObject(1, actor.getId());
        s.executeUpdate();
      }
      var future =
          pool.submit(
              () -> {
                SecurityContextHolder.getContext().setAuthentication(owner);
                try {
                  return admit();
                } finally {
                  SecurityContextHolder.clearContext();
                }
              });
      try {
        boolean waiting = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
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
        assertThat(waiting).as("Admission waits for account before taking parent locks").isTrue();
        try (var s = c.prepareStatement("select id from workspaces where id=? for update nowait")) {
          s.setObject(1, workspace.getId());
          assertThat(s.executeQuery().next()).isTrue();
        }
      } finally {
        c.commit();
      }
      assertThatThrownBy(() -> future.get(15, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    assertThat(admissions.existsByEvidenceIdAndSourceVersion(source.getId(), 0)).isFalse();
  }

  @Test
  void databaseRejectsHistoryRewriteAndForgedContentVersionsReviewerAndChecksums() {
    var admitted = admit();
    for (var sql :
        List.of(
            "update evidence_admissions set reviewed_text='Forged' where id=?",
            "update evidence_admissions set reviewed_by=reviewed_by where id=?",
            "delete from evidence_admissions where id=?"))
      assertThatThrownBy(() -> jdbc.update(sql, admitted.id()))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    for (var fault : List.of("content", "checksum", "version", "actor", "time", "policy")) {
      var item = textSource("Independent " + fault);
      assertThatThrownBy(
              () ->
                  jdbc.update(
                      "insert into evidence_admissions(id,evidence_id,source_version,brand_version,workspace_version,source_checksum,content_checksum,reviewed_text,reviewed_by,reviewed_at,policy_version,created_at,updated_at) values(?,?,?,0,0,?,?,?,?,?, ?,now(),now())",
                      UUID.randomUUID(),
                      item.getId(),
                      fault.equals("version") ? 99 : 0,
                      item.getChecksum(),
                      fault.equals("checksum")
                          ? "0".repeat(64)
                          : com.cadence.domain.EvidenceAdmission.checksum(
                              item.getSourceReference()),
                      fault.equals("content") ? "Forged" : item.getSourceReference(),
                      fault.equals("actor") ? UUID.randomUUID() : actor.getId(),
                      java.sql.Timestamp.from(
                          fault.equals("time")
                              ? java.time.Instant.now().plusSeconds(100)
                              : java.time.Instant.now()),
                      fault.equals("policy") ? "unknown" : "text-review-v1"))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }

  @Test
  void concurrentAdmissionCreatesExactlyOneSnapshot() throws Exception {
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Boolean> call =
          () -> {
            SecurityContextHolder.getContext().setAuthentication(owner);
            try {
              start.await();
              admit();
              return true;
            } catch (IllegalStateException conflict) {
              return false;
            } finally {
              SecurityContextHolder.clearContext();
            }
          };
      var a = pool.submit(call);
      var b = pool.submit(call);
      start.countDown();
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(true, false);
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from evidence_admissions where evidence_id=?",
                  Integer.class,
                  source.getId()))
          .isEqualTo(1);
    }
  }
}
