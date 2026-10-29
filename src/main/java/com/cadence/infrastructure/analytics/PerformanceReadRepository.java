package com.cadence.infrastructure.analytics;

import com.cadence.domain.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded scalar projection; no JPA raw receipt graphs or dynamic SQL fragments. */
@Repository
public class PerformanceReadRepository implements com.cadence.application.ports.PerformanceReader {
  public static final String SQL =
      """
 SELECT o.*,p.platform,p.strategy_id,p.source_revision,p.source_checksum,p.source_approved_by,p.source_approved_at,
 i.raw_checksum,i.origin,i.actor_id AS ingested_by,a.id AS attribution_id,a.attribution_number,a.revision_id,a.revision_version,a.content_checksum,
 r.format,a.pillar_id,a.pillar_name,a.pillar_origin,a.actor_id AS attributed_by,a.attributed_at
 FROM metric_observations o JOIN content_posts p ON p.id=o.post_id
 JOIN brands b ON b.id=p.brand_id JOIN metric_imports i ON i.id=o.import_id
 LEFT JOIN metric_attributions a ON a.observation_id=o.id AND NOT EXISTS(SELECT 1 FROM metric_attributions newer_context WHERE newer_context.observation_id=o.id AND newer_context.attribution_number>a.attribution_number) LEFT JOIN post_revisions r ON r.id=a.revision_id
 WHERE p.brand_id=? AND b.workspace_id=? AND o.window_from<? AND o.window_until>?
 AND (? IS NULL OR p.platform=?)
 AND NOT EXISTS(SELECT 1 FROM metric_observations newer WHERE newer.post_id=o.post_id AND newer.observed_at=o.observed_at AND newer.revision_number>o.revision_number)
 ORDER BY o.post_id,o.observed_at,o.revision_number LIMIT 1001
 """;
  private final JdbcTemplate jdbc;

  public PerformanceReadRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<PerformanceSample> read(UUID brand, UUID workspace, PerformanceQuery q) {
    return jdbc.query(
        SQL,
        p -> {
          // JdbcTemplate applies the transaction timeout before binding parameters.
          int remaining = p.getQueryTimeout();
          p.setQueryTimeout(remaining > 0 ? Math.min(remaining, 3) : 3);
          p.setFetchSize(100);
          p.setObject(1, brand);
          p.setObject(2, workspace);
          p.setObject(3, q.until().atOffset(ZoneOffset.UTC));
          p.setObject(4, q.from().atOffset(ZoneOffset.UTC));
          p.setString(5, q.platform() == null ? null : q.platform().name());
          p.setString(6, q.platform() == null ? null : q.platform().name());
        },
        (r, n) ->
            new PerformanceSample(
                id(r, "id"),
                id(r, "post_id"),
                PlaybookContent.Channel.valueOf(r.getString("platform")),
                time(r, "window_from"),
                time(r, "window_until"),
                time(r, "observed_at"),
                time(r, "ingested_at"),
                r.getInt("revision_number"),
                r.getString("value_checksum"),
                id(r, "import_id"),
                r.getString("raw_checksum"),
                r.getString("origin"),
                id(r, "ingested_by"),
                id(r, "strategy_id"),
                r.getInt("source_revision"),
                r.getString("source_checksum"),
                id(r, "source_approved_by"),
                time(r, "source_approved_at"),
                id(r, "attribution_id"),
                r.getObject("attribution_number", Integer.class),
                id(r, "revision_id"),
                r.getObject("revision_version", Long.class),
                r.getString("content_checksum"),
                r.getString("format") == null ? null : PostFormat.valueOf(r.getString("format")),
                r.getString("pillar_id"),
                r.getString("pillar_name"),
                r.getString("pillar_origin"),
                id(r, "attributed_by"),
                time(r, "attributed_at"),
                r.getObject("reach", Long.class),
                r.getObject("engagement", Long.class),
                r.getObject("saves", Long.class),
                r.getObject("signups", Long.class),
                r.getObject("profile_to_site_clicks", Long.class)));
  }

  private static UUID id(ResultSet r, String n) throws SQLException {
    return r.getObject(n, UUID.class);
  }

  private static Instant time(ResultSet r, String n) throws SQLException {
    var t = r.getTimestamp(n);
    return t == null ? null : t.toInstant();
  }
}
