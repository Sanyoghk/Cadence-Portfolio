package com.cadence.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "cadence.evidence.lifecycle.max-workspace-bytes=100",
      "cadence.evidence.lifecycle.max-workspace-objects=2",
      "cadence.evidence.lifecycle.max-workspace-writes-per-hour=3",
      "cadence.evidence.lifecycle.write-deadline=PT10S",
      "cadence.evidence.lifecycle.orphan-retention=PT10S",
      "cadence.evidence.lifecycle.deletion-drain=PT10S",
      "cadence.evidence.lifecycle.tombstone-sweep=PT10S",
      "cadence.evidence.lifecycle.cleanup-batch=100"
    })
@ActiveProfiles("test")
@Import(QuarantineLifecycleContract.Fixtures.class)
class QuarantineLifecycleTest extends QuarantineLifecycleContract {}
