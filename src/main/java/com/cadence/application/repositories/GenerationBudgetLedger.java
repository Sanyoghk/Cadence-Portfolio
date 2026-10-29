package com.cadence.application.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Bounded aggregate projection; caller holds the generation budget mutex. */
@Repository
public class GenerationBudgetLedger {
  public enum Scope {
    GLOBAL,
    ACCOUNT,
    WORKSPACE
  }

  public record Totals(BigDecimal spend, long queued, long running, long overruns, long retained) {}

  private final JdbcTemplate jdbc;

  public GenerationBudgetLedger(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Totals totals(
      Scope scope, String namespace, UUID actor, UUID workspace, Instant periodStart) {
    String predicate =
        switch (scope) {
          case GLOBAL -> "";
          case ACCOUNT -> " and actor_id=?";
          case WORKSPACE -> " and workspace_id=?";
        };
    Object[] args =
        scope == Scope.GLOBAL
            ? new Object[] {java.sql.Timestamp.from(periodStart), namespace}
            : new Object[] {
              java.sql.Timestamp.from(periodStart),
              namespace,
              scope == Scope.ACCOUNT ? actor : workspace
            };
    return jdbc.queryForObject(
        "select coalesce(sum(case when state in ('RESERVED','HELD_UNKNOWN') then reserved when state='SETTLED' and period_end>? then actual else 0 end),0), "
            + "coalesce(sum(case when state='RESERVED' then 1 else 0 end),0), "
            + "coalesce(sum(case when state='HELD_UNKNOWN' then 1 else 0 end),0), "
            + "coalesce(sum(case when actual>reserved then 1 else 0 end),0), count(*) "
            + "from generation_reservations where namespace=?"
            + predicate,
        (rs, row) ->
            new Totals(
                rs.getBigDecimal(1), rs.getLong(2), rs.getLong(3), rs.getLong(4), rs.getLong(5)),
        args);
  }

  public Optional<Instant> analysisDeadline(
      UUID request, UUID brand, String digest, long brandVersion, long workspaceVersion) {
    return jdbc
        .query(
            "select deadline from brand_analysis_attempts where id=? and brand_id=? and source_checksum=? and status='RUNNING' and brand_version=? and workspace_version=?",
            (rs, row) -> rs.getTimestamp(1).toInstant(),
            request,
            brand,
            digest,
            brandVersion,
            workspaceVersion)
        .stream()
        .findFirst();
  }
}
