package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgreSqlPostMigrationTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedLegacyKeepsExactHistoryWithoutInventingApproval() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("46")
        .load()
        .migrate();
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      s.execute(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
      s.execute(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
      c.setAutoCommit(false);
      s.execute(
          "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at) values ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002',1,'DRAFT',3,'UTC',now(),now())");
      s.execute(
          "insert into strategy_content_pillars values ('00000000-0000-0000-0000-000000000003',0,'Education',100,'Useful')");
      s.execute(
          "insert into strategy_best_publishing_times values ('00000000-0000-0000-0000-000000000003',0,'12:00:00')");
      for (String status :
          java.util.List.of("DRAFT", "APPROVED", "SCHEDULED", "PUBLISHED", "FAILED")) {
        var id = java.util.UUID.randomUUID();
        try (var q =
            c.prepareStatement(
                "insert into social_posts(id,strategy_id,title,caption,format,status,scheduled_at,created_at,updated_at) values (?,'00000000-0000-0000-0000-000000000003','Original','Historical caption','TEXT',?,'2026-07-01T12:00:00Z','2026-06-01T12:00:00Z','2026-06-02T12:00:00Z')")) {
          q.setObject(1, id);
          q.setString(2, status);
          q.executeUpdate();
        }
        try (var q =
            c.prepareStatement(
                "insert into social_post_hashtags(post_id,hashtag_order,hashtag) values (?,0,'#legacy')")) {
          q.setObject(1, id);
          q.executeUpdate();
        }
      }
      c.commit();
    }
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .load()
        .migrate();
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      try (var r =
          s.executeQuery(
              "select count(*) from social_posts old join content_posts p on p.id=old.id join post_revisions r on r.id=p.id where r.post_id=p.id and p.legacy and p.platform is null and p.source_checksum is null and p.source_approved_by is null and p.authored_by is null and r.status='LEGACY' and r.approved_by is null and r.authored_by is null and r.edited_by is null and r.title=old.title and r.caption=old.caption and r.format=old.format and r.legacy_status=old.status and r.legacy_scheduled_at=old.scheduled_at and r.created_at=old.created_at and r.updated_at=old.updated_at")) {
        assertThat(r.next()).isTrue();
        assertThat(r.getInt(1)).isEqualTo(5);
      }
      try (var r =
          s.executeQuery("select count(*) from post_revision_hashtags where hashtag='#legacy'")) {
        r.next();
        assertThat(r.getInt(1)).isEqualTo(5);
      }
      try (var r =
          s.executeQuery(
              "select count(*) from post_revisions where generation_batch_id is not null or generation_slot_id is not null")) {
        r.next();
        assertThat(r.getInt(1)).isZero();
      }
      try (var r = s.executeQuery("select count(*) from content_generation_batches")) {
        r.next();
        assertThat(r.getInt(1)).isZero();
      }
      try (var r = s.executeQuery("select count(*) from post_schedule_events")) {
        r.next();
        assertThat(r.getInt(1)).isZero();
      }
      for (String table :
          java.util.List.of(
              "media_assets", "post_revision_media", "csv_exports", "csv_export_items"))
        try (var r = s.executeQuery("select count(*) from " + table)) {
          r.next();
          assertThat(r.getInt(1)).isZero();
        }
      assertThatThrownBy(
              () ->
                  s.executeUpdate("update post_revisions set title='Forged' where status='LEGACY'"))
          .isInstanceOf(SQLException.class);
      assertThatThrownBy(() -> s.executeUpdate("delete from post_revision_hashtags"))
          .isInstanceOf(SQLException.class);
    }
  }
}
