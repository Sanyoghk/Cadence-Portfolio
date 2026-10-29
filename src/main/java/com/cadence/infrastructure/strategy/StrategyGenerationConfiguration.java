package com.cadence.infrastructure.strategy;

import com.cadence.application.exceptions.StrategyGenerationUnavailableException;
import com.cadence.application.ports.StrategyGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.env.*;

@Configuration
public class StrategyGenerationConfiguration {
  @Bean
  StrategyGenerationService strategyGenerationService(
      Environment environment,
      @Value("${cadence.strategy-generation.adapter:disabled}") String adapter) {
    return switch (adapter) {
      case "disabled" ->
          command -> {
            throw new StrategyGenerationUnavailableException();
          };
      case "fake" -> {
        if (environment.acceptsProfiles(Profiles.of("prod"))
            || !environment.acceptsProfiles(Profiles.of("local", "test")))
          throw new IllegalStateException(
              "Fake strategy generator requires local/test and forbids prod");
        yield new DeterministicStrategyGenerationAdapter();
      }
      default -> throw new IllegalStateException("Unsupported strategy generation adapter");
    };
  }
}
