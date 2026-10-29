package com.cadence.infrastructure.ingest;

import com.cadence.application.services.EvidenceArtifactWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@Profile("(local & renderer & !prod) | (prod & s3 & clamd & renderer)")
@ConditionalOnProperty(name = "cadence.evidence.artifacts-enabled", havingValue = "true")
public class EvidenceArtifactScheduling {
  private final EvidenceArtifactWorker worker;

  public EvidenceArtifactScheduling(EvidenceArtifactWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelayString = "${cadence.evidence.artifact-delay-ms:5000}")
  public void poll() {
    worker.runOnce();
  }
}
