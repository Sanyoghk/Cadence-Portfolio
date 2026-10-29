package com.cadence.configuration;

import static org.assertj.core.api.Assertions.*;

import com.cadence.infrastructure.storage.ProductionEvidenceConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionEvidenceGuardTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ProductionEvidenceConfigurationGuard.class);

  @Test
  void rejectsDevelopmentProfileAndPartialOrUnapprovedProviders() {
    for (String profiles :
        java.util.List.of(
            "prod,local",
            "prod,test",
            "prod,postgres-test",
            "prod,s3",
            "prod,clamd",
            "prod,s3,clamd")) {
      runner
          .withPropertyValues("spring.profiles.active=" + profiles)
          .run(c -> assertThat(c).hasFailed());
    }
    runner
        .withPropertyValues(
            "spring.profiles.active=prod", "cadence.evidence.url-worker-enabled=true")
        .run(c -> assertThat(c).hasFailed());
  }

  @Test
  void requiresEveryOperationalAcknowledgement() {
    for (String missing :
        java.util.List.of(
            "production-approved", "egress-policy-acknowledged", "retention-policy-acknowledged")) {
      runner
          .withPropertyValues(
              "spring.profiles.active=prod,s3,clamd",
              "cadence.evidence.production-approved=true",
              "cadence.evidence.egress-policy-acknowledged=true",
              "cadence.evidence.retention-policy-acknowledged=true",
              "cadence.evidence." + missing + "=false")
          .run(c -> assertThat(c).hasFailed());
    }
  }

  @Test
  void permitsExplicitApprovalOrDisabledProductionEvidence() {
    runner.withPropertyValues("spring.profiles.active=prod").run(c -> assertThat(c).hasNotFailed());
    runner
        .withPropertyValues(
            "spring.profiles.active=prod,s3,clamd",
            "cadence.evidence.production-approved=true",
            "cadence.evidence.egress-policy-acknowledged=true",
            "cadence.evidence.retention-policy-acknowledged=true")
        .run(c -> assertThat(c).hasNotFailed());
  }
}
