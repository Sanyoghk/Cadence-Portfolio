package com.cadence.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("clamd")
@EnableConfigurationProperties(ClamdScannerProperties.class)
public class ClamdScannerConfiguration {
  @Bean(destroyMethod = "close")
  ClamdEvidenceMalwareScanner clamdEvidenceMalwareScanner(ClamdScannerProperties properties) {
    return new ClamdEvidenceMalwareScanner(properties);
  }
}
