package com.cadence.application.services;

import com.cadence.domain.AiGenerationAttempt;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Component;

/** Process-local bounded counters. No content, identifiers or provider text in metric keys. */
@org.springframework.jmx.export.annotation.ManagedResource(
    objectName = "com.cadence:type=AiGenerationMetrics")
@Component
public class AiGenerationTelemetry {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AiGenerationTelemetry.class);
  private final EnumMap<AiGenerationAttempt.Outcome, LongAdder> completed =
      new EnumMap<>(AiGenerationAttempt.Outcome.class);

  private final LongAdder pollFailures = new LongAdder();

  public void pollFailed() {
    pollFailures.increment();
    LOG.warn("ai_worker_poll_failed code=WORKER_UNAVAILABLE");
  }

  @org.springframework.jmx.export.annotation.ManagedAttribute
  public long getPollFailures() {
    return pollFailures.sum();
  }

  public AiGenerationTelemetry() {
    for (var outcome : AiGenerationAttempt.Outcome.values())
      completed.put(outcome, new LongAdder());
  }

  public void completed(
      UUID job,
      UUID operation,
      int attempt,
      AiGenerationAttempt.Outcome outcome,
      AiGenerationAttempt.Reason reason) {
    completed.get(outcome).increment();
    LOG.info(
        "ai_attempt_completed job={} operation={} attempt={} outcome={} reason={}",
        job,
        operation,
        attempt,
        outcome,
        reason);
  }

  @org.springframework.jmx.export.annotation.ManagedOperation(
      description = "Process-local completion counters; fixed enum keys, no content or IDs")
  public Map<AiGenerationAttempt.Outcome, Long> completedCounts() {
    var result = new EnumMap<AiGenerationAttempt.Outcome, Long>(AiGenerationAttempt.Outcome.class);
    completed.forEach((key, value) -> result.put(key, value.sum()));
    return Collections.unmodifiableMap(result);
  }
}
