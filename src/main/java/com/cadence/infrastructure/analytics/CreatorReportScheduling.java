package com.cadence.infrastructure.analytics;

import com.cadence.application.services.CreatorReportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cadence.reports.worker-enabled", havingValue = "true")
public class CreatorReportScheduling {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CreatorReportScheduling.class);
  private final CreatorReportService worker;
  private long nextPollNanos = System.nanoTime();

  public CreatorReportScheduling(CreatorReportService worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 1000)
  public synchronized void tick() {
    if (System.nanoTime() - nextPollNanos < 0) return;
    try {
      worker.runOnce();
    } catch (RuntimeException failure) {
      nextPollNanos = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
      LOG.warn("Creator report poll failed; bounded retry scheduled");
    }
  }
}
