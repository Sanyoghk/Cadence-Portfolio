package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class PostgreSqlAiCompletionMigrationTest {
  @org.junit.jupiter.api.extension.RegisterExtension
  static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV43AttemptKeepsUnknownCompletionAndOriginalWitness() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("43")
        .load()
        .migrate();
    UUID workspace = UUID.randomUUID(),
        brand = UUID.randomUUID(),
        actor = UUID.randomUUID(),
        job = UUID.randomUUID(),
        operation = UUID.randomUUID(),
        attempt = UUID.randomUUID();
    Map<String, Object> before;
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var j =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      j.update(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values(?,'Existing','Creator','ACTIVE',now(),now())",
          workspace);
      j.update(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values(?,?,'Legacy','ACTIVE',now(),now())",
          brand,
          workspace);
      j.update(
          "insert into cadence_users(id,external_subject,role,active,created_at,updated_at) values(?,'migration-owner','OWNER',true,now(),now())",
          actor);
      j.update(
          "insert into workspace_memberships(user_id,workspace_id) values(?,?)", actor, workspace);
      c.setAutoCommit(false);
      j.update(
          "insert into jobs(id,created_at,updated_at,type,payload_reference,idempotency_key,status,max_attempts,next_attempt_at) values(?,now(),now(),'AI_GENERATION',?,'migration-key','PENDING',3,now())",
          job,
          "generation:" + operation);
      String snapshot =
          "{\"schemaVersion\":1,\"analysis\":{\"brandVersion\":0,\"workspaceVersion\":0,\"brandName\":\"Legacy\",\"industry\":\"Creator\",\"sources\":[]}}";
      j.update(
          "insert into ai_generation_operations(id,created_at,updated_at,actor_id,workspace_id,brand_id,kind,key_digest,request_digest,snapshot,snapshot_checksum,submitted_at,deadline,job_id,status) values(?,now(),now(),?,?,?,'DECLARED',repeat('a',64),repeat('b',64),?,encode(sha256(convert_to(?,'UTF8')),'hex'),now(),now()+interval '60 seconds',?,'QUEUED')",
          operation,
          actor,
          workspace,
          brand,
          snapshot,
          snapshot,
          job);
      c.commit();
      j.update("update ai_generation_operations set status='RUNNING' where id=?", operation);
      j.update(
          "update jobs set status='RUNNING',attempts=1,lease_owner='legacy-worker',lease_expires_at=now()+interval '20 seconds' where id=?",
          job);
      j.update(
          "insert into ai_generation_attempts(id,created_at,updated_at,operation_id,attempt_number,worker_id,started_at,lease_expires_at) select ?,now(),now(),?,1,'legacy-worker',now(),lease_expires_at from jobs where id=?",
          attempt,
          operation,
          job);
      c.commit();
      j.update("update ai_generation_attempts set dispatched_at=now() where id=?", attempt);
      c.commit();
      j.update(
          "update jobs set status='DEAD',completed_at=now(),lease_owner=null,lease_expires_at=null where id=?",
          job);
      j.update(
          "update ai_generation_operations set status='OUTCOME_UNKNOWN',completed_at=now() where id=?",
          operation);
      c.commit();
      before = j.queryForMap("select * from ai_generation_attempts where id=?", attempt);
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      var j =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.SingleConnectionDataSource(c, true));
      assertThat(j.queryForObject("select count(*) from generation_reservations", Long.class))
          .isZero();
      var after = j.queryForMap("select * from ai_generation_attempts where id=?", attempt);
      assertThat(((Number) after.remove("completion_schema")).intValue()).isZero();
      assertThat(after.remove("finished_at")).isNull();
      assertThat(after.remove("outcome")).isNull();
      assertThat(after.remove("reason")).isNull();
      assertThat(after).isEqualTo(before);
      assertThatThrownBy(
              () ->
                  j.update(
                      "update ai_generation_attempts set finished_at=now(),outcome='OUTCOME_UNKNOWN',reason='PROVIDER_OUTCOME_UNKNOWN' where id=?",
                      attempt))
          .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
  }
}
