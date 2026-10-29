package com.cadence.infrastructure.analysis;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.BrandAnalysisCommand;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class BrandAnalysisConfigurationTest {
  private final BrandAnalysisConfiguration configuration = new BrandAnalysisConfiguration();

  @ParameterizedTest
  @ValueSource(strings = {"prod", "prod,test", "prod,local", "postgres-test", "unknown"})
  void fakeCannotBeSelectedOutsideExplicitLocalTestOrAlongsideProd(String profiles) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profiles.split(","));
    assertThatThrownBy(() -> configuration.brandAnalysisPort(environment, "fake"))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "test"})
  void explicitDevelopmentProfilesMayUseDeterministicFixture(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    var result =
        configuration
            .brandAnalysisPort(environment, "fake")
            .analyze(new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, " Brand ", " Creator "));
    assertThat(result.provider()).isEqualTo("local-fake");
    assertThat(result.sampleCaption()).isEqualTo("Example brand caption");
    assertThat(result.warnings())
        .contains(com.cadence.application.ports.BrandAnalysisResult.Warning.DEMONSTRATION_ONLY);
  }

  @Test
  void disabledProductionFailsClosedAndUnknownAdapterDoesNotFallback() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    assertThatThrownBy(() -> configuration.brandAnalysisPort(environment, "disabled").analyze(null))
        .isInstanceOf(BrandAnalysisUnavailableException.class);
    assertThatThrownBy(() -> configuration.brandAnalysisPort(environment, "typo"))
        .isInstanceOf(IllegalStateException.class);
  }
}
