package com.cadence.infrastructure.ingest.social;

import com.cadence.application.repositories.EvidenceProviderCapacityRepository;
import com.cadence.domain.EvidenceProviderCapacity;
import com.cadence.domain.SocialEvidenceProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;

/** H2 create-drop fixture seed only; PostgreSQL and production must use V21 migration. */
@Configuration(proxyBeanMethods = false)
@Profile("(local | test) & !prod")
public class SocialProviderCapacityFixtureConfiguration {
  @Bean
  ApplicationRunner seedLocalSocialProviderCapacity(EvidenceProviderCapacityRepository slots) {
    return args -> {
      for (SocialEvidenceProvider provider : SocialEvidenceProvider.values())
        if (!slots.existsById(provider)) slots.saveAndFlush(new EvidenceProviderCapacity(provider));
    };
  }
}
