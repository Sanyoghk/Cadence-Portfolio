package com.cadence.infrastructure.strategy;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.exceptions.StrategyGenerationUnavailableException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class StrategyGenerationConfigurationTest {
  private final StrategyGenerationConfiguration configuration =
      new StrategyGenerationConfiguration();

  @ParameterizedTest
  @ValueSource(strings = {"prod", "prod,test", "prod,local", "postgres-test", "unknown"})
  void fakeCannotBeSelectedOutsideExplicitLocalTestOrAlongsideProd(String profiles) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profiles.split(","));
    assertThatThrownBy(() -> configuration.strategyGenerationService(environment, "fake"))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"local", "test"})
  void explicitDevelopmentProfilesMayUseDeterministicFixture(String profile) {
    var environment = new MockEnvironment();
    environment.setActiveProfiles(profile);
    var result =
        configuration
            .strategyGenerationService(environment, "fake")
            .generate(
                new com.cadence.application.ports.StrategyGenerationCommand(
                    UUID.randomUUID(),
                    0,
                    0,
                    " Brand ",
                    " Creator ",
                    new com.cadence.application.ports.StrategyGenerationCommand.Signature(
                        new com.cadence.application.ports.BrandAnalysisResult.Palette(
                            "#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
                        java.util.List.of("Inter"),
                        java.util.List.of("clear"),
                        java.util.List.of("spam"),
                        "Caption"),
                    java.time.Instant.now()));
    assertThat(result.provider()).isEqualTo("local-fake");
    assertThat(result.pillars()).hasSize(2);
    assertThat(result.warnings())
        .contains(
            com.cadence.application.ports.StrategyGenerationResult.Warning.DEMONSTRATION_ONLY);
  }

  @Test
  void disabledProductionFailsClosedAndUnknownAdapterDoesNotFallback() {
    var environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    assertThatThrownBy(
            () -> configuration.strategyGenerationService(environment, "disabled").generate(null))
        .isInstanceOf(StrategyGenerationUnavailableException.class);
    assertThatThrownBy(() -> configuration.strategyGenerationService(environment, "typo"))
        .isInstanceOf(IllegalStateException.class);
  }
}
