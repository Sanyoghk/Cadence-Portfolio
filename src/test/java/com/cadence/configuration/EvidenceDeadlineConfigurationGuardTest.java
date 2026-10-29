package com.cadence.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cadence.infrastructure.storage.EvidenceDeadlineConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EvidenceDeadlineConfigurationGuardTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(EvidenceDeadlineConfigurationGuard.class)
          .withPropertyValues("spring.profiles.active=s3,clamd,renderer");

  @Test
  void defaultsFitTheDurableLeaseAndReservation() {
    runner.run(c -> assertThat(c).hasNotFailed());
  }

  @Test
  void rejectsIoChainLongerThanLeaseOrReservationAndMalformedValues() {
    for (String property :
        java.util.List.of(
            "cadence.evidence.lifecycle.write-deadline=PT40S",
            "cadence.evidence.s3.operation-timeout=PT60S",
            "cadence.evidence.renderer.deadline=invalid"))
      runner.withPropertyValues(property).run(c -> assertThat(c).hasFailed());
  }
}
