package com.cadence.infrastructure.analysis;

import com.cadence.application.repositories.GenerationBudgetMutexRepository;
import com.cadence.domain.GenerationBudgetMutex;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
@Profile("(local | test) & !prod")
public class GenerationBudgetFixtureConfiguration {
  @Bean
  ApplicationRunner seedGenerationBudgetFixture(GenerationBudgetMutexRepository mutex) {
    return args -> {
      if (!mutex.existsById(1)) mutex.saveAndFlush(GenerationBudgetMutex.singleton());
    };
  }
}
