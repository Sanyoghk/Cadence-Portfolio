package com.cadence.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("renderer")
@EnableConfigurationProperties(RendererProperties.class)
public class RendererConfiguration {
  @Bean(destroyMethod = "close")
  RendererEvidenceAdapter rendererEvidenceAdapter(RendererProperties properties) {
    return new RendererEvidenceAdapter(properties);
  }
}
