package com.cadence.infrastructure.ingest;

import com.cadence.application.services.UrlFetchWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Explicit opt-in until production egress/storage/compliance gates are met. */
@Configuration
@Profile("(local & !prod) | (prod & s3 & clamd)")
@EnableScheduling
@ConditionalOnProperty(name = "cadence.evidence.url-worker-enabled", havingValue = "true")
public class UrlFetchScheduling {
  private final UrlFetchWorker worker;

  public UrlFetchScheduling(UrlFetchWorker worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 5000)
  public void poll() {
    worker.runOnce();
  }
}
