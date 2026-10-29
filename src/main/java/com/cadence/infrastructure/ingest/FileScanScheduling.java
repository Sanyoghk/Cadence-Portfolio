package com.cadence.infrastructure.ingest;

import com.cadence.application.services.EvidenceMalwareScanWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Development opt-in only until real scanner/storage and operation deadlines are verified. */
@Configuration
@EnableScheduling
@Profile("(local & !prod) | (prod & s3 & clamd)")
@ConditionalOnProperty(name = "cadence.evidence.file-scan-worker-enabled", havingValue = "true")
public class FileScanScheduling {
  private final EvidenceMalwareScanWorker worker;

  public FileScanScheduling(EvidenceMalwareScanWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 5000)
  public void poll() {
    worker.runOnce();
  }
}
