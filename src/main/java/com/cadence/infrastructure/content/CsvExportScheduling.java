package com.cadence.infrastructure.content;

import com.cadence.application.services.CsvExportService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "cadence.csv-export.worker-enabled", havingValue = "true")
public class CsvExportScheduling {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(CsvExportScheduling.class);
  private final CsvExportService worker;
  private long nextPollNanos = System.nanoTime();

  public CsvExportScheduling(CsvExportService worker) {
    this.worker = worker;
  }

  @Scheduled(fixedDelay = 1000)
  public synchronized void tick() {
    if (System.nanoTime() - nextPollNanos < 0) return;
    try {
      worker.runOnce();
    } catch (RuntimeException failure) {
      // Never log SQL/user content. Back off on infrastructure failure.
      nextPollNanos = System.nanoTime() + java.time.Duration.ofSeconds(30).toNanos();
      LOG.warn("CSV export poll failed; bounded retry scheduled");
    }
  }
}
