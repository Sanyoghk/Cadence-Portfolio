package com.cadence.persistence;

import static org.assertj.core.api.Assertions.*;

import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgreSqlCreatorReportMigrationTest {
  @RegisterExtension static final PostgreSqlTestDatabase POSTGRES = new PostgreSqlTestDatabase();

  @Test
  void populatedV54PreservesLegacyAndAddsEmptyReportHistory() throws Exception {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .target("54")
        .load()
        .migrate();
    String before;
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      s.execute(
          "insert into workspaces(id,name,industry,status,created_at,updated_at) values('00000000-0000-0000-0000-000000000001','Studio','Creator','ACTIVE',now(),now())");
      s.execute(
          "insert into brands(id,workspace_id,name,status,created_at,updated_at) values('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Brand','ACTIVE',now(),now())");
      c.setAutoCommit(false);
      s.execute(
          "insert into strategies(id,brand_id,version,status,posts_per_week,publishing_timezone,created_at,updated_at) values('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002',1,'DRAFT',3,'UTC',now(),now())");
      s.execute(
          "insert into strategy_content_pillars values('00000000-0000-0000-0000-000000000003',0,'Education',100,'Useful')");
      s.execute(
          "insert into strategy_best_publishing_times values('00000000-0000-0000-0000-000000000003',0,'12:00:00')");
      s.execute(
          "insert into social_posts(id,strategy_id,title,caption,format,status,created_at,updated_at) values('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000003','Legacy','Legacy caption','TEXT','DRAFT',now(),now())");
      s.execute(
          "insert into post_metrics(id,post_id,reach,engagement,saves,signups,profile_to_site_clicks,created_at,updated_at) values('00000000-0000-0000-0000-000000000005','00000000-0000-0000-0000-000000000004',100,20,0,1,3,now(),now())");
      s.execute(
          "insert into recommendations(id,strategy_id,text,confidence,expected_lift,created_at,updated_at) values('00000000-0000-0000-0000-000000000006','00000000-0000-0000-0000-000000000003','Legacy suggestion',0.85,'Legacy claim',now(),now())");
      c.commit();
      try (var r = s.executeQuery("select to_jsonb(m)::text from recommendations m")) {
        r.next();
        before = r.getString(1);
      }
    }
    assertThat(
            Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("55")
                .load()
                .migrate()
                .migrationsExecuted)
        .isEqualTo(1);
    try (var c =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var s = c.createStatement()) {
      try (var r = s.executeQuery("select to_jsonb(m)::text from recommendations m")) {
        r.next();
        assertThat(r.getString(1)).isEqualTo(before);
        assertThat(r.next()).isFalse();
      }
      for (var table :
          java.util.List.of(
              "creator_reports",
              "recommendation_keys",
              "recommendation_insights",
              "recommendation_resolutions",
              "recommendation_metric_links",
              "metric_imports",
              "metric_observations",
              "metric_import_results",
              "metric_attributions"))
        try (var r = s.executeQuery("select count(*) from " + table)) {
          r.next();
          assertThat(r.getInt(1)).isZero();
        }
    }
  }
}
