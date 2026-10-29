package com.cadence.infrastructure.ingest.social;

import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods = false)
public class SocialEvidenceConfiguration {
  @Bean
  @ConfigurationProperties(prefix = "cadence.evidence.social")
  SocialEvidenceProperties socialEvidenceProperties() {
    return new SocialEvidenceProperties();
  }

  @Bean(destroyMethod = "close")
  JdkSocialProviderHttpTransport socialProviderHttpTransport() {
    return new JdkSocialProviderHttpTransport();
  }

  @Bean
  OfficialSocialEvidenceSource officialSocialEvidenceSource(
      SocialEvidenceProperties properties, SocialProviderHttpTransport transport, Clock clock) {
    properties.validate();
    SocialHttpRuntimePolicy.require(properties.isEnabled());
    return new OfficialSocialEvidenceSource(properties, transport, clock);
  }
}
