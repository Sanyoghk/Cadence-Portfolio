package com.cadence.infrastructure.ingest;

import com.cadence.application.services.QuarantineCleanupWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@Profile("(local & !prod) | (prod & s3 & clamd)")
@ConditionalOnProperty(name = "cadence.evidence.cleanup-enabled", havingValue = "true")
public class QuarantineCleanupScheduling {
  private final QuarantineCleanupWorker worker;

  public QuarantineCleanupScheduling(QuarantineCleanupWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${cadence.evidence.cleanup-delay-ms:60000}")
  public void poll() {
    worker.runOnce();
  }
}
