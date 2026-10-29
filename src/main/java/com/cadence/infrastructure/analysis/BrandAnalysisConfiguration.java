package com.cadence.infrastructure.analysis;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.BrandAnalysisPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class BrandAnalysisConfiguration {
  @Bean
  BrandAnalysisPort brandAnalysisPort(
      Environment environment,
      @Value("${cadence.brand-analysis.adapter:disabled}") String adapter) {
    return switch (adapter) {
      case "disabled" ->
          command -> {
            throw new BrandAnalysisUnavailableException();
          };
      case "fake" -> {
        if (environment.acceptsProfiles(Profiles.of("prod"))
            || !environment.acceptsProfiles(Profiles.of("local", "test"))) {
          throw new IllegalStateException(
              "Fake brand analyzer requires local/test and forbids prod");
        }
        yield new DeterministicBrandAnalysisAdapter();
      }
      default -> throw new IllegalStateException("Unsupported brand analysis adapter");
    };
  }
}
