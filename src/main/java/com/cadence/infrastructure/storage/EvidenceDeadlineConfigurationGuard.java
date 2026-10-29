package com.cadence.infrastructure.storage;

import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** A caller cannot retain a lease or reservation shorter than the configured bounded I/O chain. */
@Component
public final class EvidenceDeadlineConfigurationGuard {
  public EvidenceDeadlineConfigurationGuard(Environment environment) {
    Set<String> profiles = Set.copyOf(Arrays.asList(environment.getActiveProfiles()));
    if (!(profiles.contains("s3") || profiles.contains("clamd") || profiles.contains("renderer")))
      return;
    Duration storage =
        profiles.contains("s3")
            ? duration(environment, "s3.operation-timeout", "PT15S")
            : Duration.ZERO;
    Duration scanner =
        profiles.contains("clamd")
            ? duration(environment, "clamd.deadline", "PT30S")
            : Duration.ZERO;
    Duration renderer =
        profiles.contains("renderer")
            ? duration(environment, "renderer.deadline", "PT20S")
            : Duration.ZERO;
    Duration write = duration(environment, "lifecycle.write-deadline", "PT2M");
    Duration margin = Duration.ofSeconds(10);
    Duration scanChain = storage.plus(scanner).plus(margin);
    Duration artifactChain = storage.multipliedBy(2).plus(renderer).plus(margin);
    Duration fetchChain = storage.plus(Duration.ofSeconds(30)).plus(margin);
    Duration lease = Duration.ofMinutes(2);
    if (scanChain.compareTo(lease) >= 0
        || artifactChain.compareTo(lease) >= 0
        || fetchChain.compareTo(lease) >= 0
        || write.compareTo(scanChain) <= 0
        || write.compareTo(artifactChain) <= 0
        || write.compareTo(fetchChain) <= 0)
      throw new IllegalStateException(
          "Evidence deadlines must fit write reservations and worker leases");
  }

  private static Duration duration(Environment environment, String name, String fallback) {
    try {
      return DurationStyle.detectAndParse(
          environment.getProperty("cadence.evidence." + name, fallback));
    } catch (RuntimeException invalid) {
      throw new IllegalStateException("Invalid evidence deadline configuration");
    }
  }
}
