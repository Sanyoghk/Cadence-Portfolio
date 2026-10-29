package com.cadence.application.services;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Operational bounds: referenced evidence is retained until a separate deletion policy exists. */
@Component
public record QuarantineLifecyclePolicy(
    @Value("${cadence.evidence.lifecycle.max-workspace-bytes:536870912}") long maxWorkspaceBytes,
    @Value("${cadence.evidence.lifecycle.max-workspace-objects:1000}") int maxWorkspaceObjects,
    @Value("${cadence.evidence.lifecycle.max-workspace-writes-per-hour:100}")
        int maxWorkspaceWritesPerHour,
    @Value("${cadence.evidence.lifecycle.write-deadline:PT2M}") Duration writeDeadline,
    @Value("${cadence.evidence.lifecycle.orphan-retention:PT24H}") Duration orphanRetention,
    @Value("${cadence.evidence.lifecycle.deletion-drain:PT5M}") Duration deletionDrain,
    @Value("${cadence.evidence.lifecycle.tombstone-sweep:PT1H}") Duration tombstoneSweep,
    @Value("${cadence.evidence.lifecycle.cleanup-batch:20}") int cleanupBatch) {
  public QuarantineLifecyclePolicy {
    if (maxWorkspaceBytes < 1
        || maxWorkspaceBytes > 1_099_511_627_776L
        || maxWorkspaceObjects < 1
        || maxWorkspaceObjects > 1_000_000
        || maxWorkspaceWritesPerHour < 1
        || maxWorkspaceWritesPerHour > 100_000
        || !bounded(writeDeadline, Duration.ofSeconds(1), Duration.ofMinutes(10))
        || !bounded(orphanRetention, Duration.ofSeconds(1), Duration.ofDays(365))
        || !bounded(deletionDrain, writeDeadline, Duration.ofDays(1))
        || !bounded(tombstoneSweep, Duration.ofSeconds(1), Duration.ofDays(7))
        || cleanupBatch < 1
        || cleanupBatch > 100)
      throw new IllegalArgumentException("Invalid quarantine lifecycle configuration");
  }

  private static boolean bounded(Duration value, Duration min, Duration max) {
    return value != null && min != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
  }
}
