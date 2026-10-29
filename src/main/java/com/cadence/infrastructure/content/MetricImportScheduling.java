package com.cadence.infrastructure.content;

import com.cadence.application.services.MetricImportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cadence.metrics-import.worker-enabled", havingValue = "true")
public class MetricImportScheduling {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(MetricImportScheduling.class);
  private final MetricImportService worker;
  private long nextPollNanos = System.nanoTime();

  public MetricImportScheduling(MetricImportService worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 1000)
  public synchronized void tick() {
    if (System.nanoTime() - nextPollNanos < 0) return;
    try {
      worker.runOnce();
    } catch (RuntimeException failure) {
      nextPollNanos = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
      LOG.warn("Metrics import poll failed; bounded retry scheduled");
    }
  }
}
