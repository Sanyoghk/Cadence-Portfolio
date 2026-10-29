package com.cadence.infrastructure.analysis;

import com.cadence.application.services.AiGenerationService;
import com.cadence.application.services.AiGenerationTelemetry;
import java.time.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cadence.ai-jobs.worker-enabled", havingValue = "true")
public class AiGenerationScheduling {
  private final AiGenerationService worker;
  private final AiGenerationTelemetry telemetry;
  private final Clock clock;
  private Instant nextPoll = Instant.MIN;
  private int failures;

  public AiGenerationScheduling(
      AiGenerationService worker, AiGenerationTelemetry telemetry, Clock clock) {
    this.worker = worker;
    this.telemetry = telemetry;
    this.clock = clock;
  }

  @Scheduled(fixedDelay = 1000)
  public synchronized void tick() {
    if (clock.instant().isBefore(nextPoll)) return;
    try {
      worker.runOnce();
      failures = 0;
      nextPoll = Instant.MIN;
    } catch (RuntimeException failure) {
      failures = Math.min(5, failures + 1);
      nextPoll = clock.instant().plusSeconds(Math.min(30, 1L << failures));
      // SQL/provider exception text may contain user data; never delegate it to scheduler logging.
      telemetry.pollFailed();
    }
  }
}
