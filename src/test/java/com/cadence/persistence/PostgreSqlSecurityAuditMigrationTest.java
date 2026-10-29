package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgreSqlSecurityAuditMigrationTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV55HasNoFabricatedHistoryAndNewEventsRollbackAtomically() throws Exception {
    var schema = "audit_" + UUID.randomUUID().toString().replace("-", "");
    var flyway =
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .schemas(schema)
            .defaultSchema(schema);
    flyway.target("55").load().migrate();
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      c.setSchema(schema);
      var ws = UUID.randomUUID();
      var user = UUID.randomUUID();
      s.execute(
          "insert into workspaces(id,created_at,updated_at,name,industry,status) values('"
              + ws
              + "',now(),now(),'Audit','Creator','ACTIVE')");
      s.execute(
          "insert into cadence_users(id,created_at,updated_at,external_subject,role,active) values('"
              + user
              + "',now(),now(),'synthetic-audit-user','OWNER',true)");
      s.execute("insert into workspace_memberships values('" + user + "','" + ws + "')");
      // Real bootstrap grants future tables/sequences. Migration must close that window itself.
      s.execute("create role cadence_runtime nologin");
      s.execute("grant usage on schema " + schema + " to cadence_runtime");
      s.execute(
          "alter default privileges in schema "
              + schema
              + " grant select,insert,update on tables to cadence_runtime");
      s.execute(
          "alter default privileges in schema "
              + schema
              + " grant usage,select on sequences to cadence_runtime");
      assertThat(flyway.target("56").load().migrate().migrationsExecuted).isEqualTo(1);
      s.execute("set role cadence_runtime");
      for (var sql :
          java.util.List.of(
              "select * from security_audit_events",
              "insert into security_audit_events default values",
              "select nextval('security_audit_events_id_seq')"))
        assertThatThrownBy(() -> s.execute(sql)).isInstanceOf(SQLException.class);
      s.execute("reset role");
      assertThat(count(s)).isZero();
      c.setAutoCommit(false);
      s.execute("delete from workspace_memberships");
      assertThat(count(s)).isEqualTo(1);
      c.rollback();
      assertThat(count(s)).isZero();
      s.execute("update cadence_users set active=false where id='" + user + "'");
      c.commit();
      try (var r =
          s.executeQuery(
              "select action,actor_id,actor_origin,database_principal,result,resource_id,occurred_at,transaction_id from security_audit_events")) {
        assertThat(r.next()).isTrue();
        assertThat(r.getString(1)).isEqualTo("ACCOUNT_PERMISSIONS_CHANGE");
        assertThat(r.getObject(2)).isNull();
        assertThat(r.getString(3)).isEqualTo("DATABASE_PRINCIPAL");
        assertThat(r.getString(4)).isEqualTo(POSTGRES.getUsername());
        assertThat(r.getString(5)).isEqualTo("COMMITTED");
        assertThat(r.getObject(6)).isEqualTo(user);
        assertThat(r.getObject(7)).isNotNull();
        assertThat(r.getLong(8)).isPositive();
      }
      // Sequence capacity is a fail-closed safety bound, never wrap and overwrite audit history.
      s.execute("alter sequence security_audit_events_id_seq restart with 1000000");
      s.execute("update cadence_users set active=true where id='" + user + "'");
      c.commit();
      assertThatThrownBy(
              () -> s.execute("update cadence_users set active=false where id='" + user + "'"))
          .isInstanceOf(SQLException.class);
      c.rollback();
      try (var r = s.executeQuery("select active from cadence_users")) {
        r.next();
        assertThat(r.getBoolean(1)).isTrue();
      }
    }
  }

  private long count(Statement s) throws Exception {
    try (var r = s.executeQuery("select count(*) from security_audit_events")) {
      r.next();
      return r.getLong(1);
    }
  }
}
