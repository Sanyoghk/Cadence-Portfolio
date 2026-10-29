package com.cadence.infrastructure.storage;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@Profile("s3")
public class S3EvidenceStorageConfiguration {
  @Bean
  @ConfigurationProperties(prefix = "cadence.evidence.s3")
  S3EvidenceStorageProperties s3EvidenceStorageProperties() {
    return new S3EvidenceStorageProperties();
  }

  @Bean(destroyMethod = "close")
  S3Client evidenceS3Client(S3EvidenceStorageProperties properties, Environment environment) {
    properties.validate(environment.matchesProfiles("prod"));
    var builder =
        S3Client.builder()
            .endpointOverride(URI.create(properties.getEndpoint()))
            .region(Region.of(properties.getRegion()))
            .forcePathStyle(true)
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .maxConnections(properties.getMaxConcurrentOperations())
                    .connectionTimeout(properties.getConnectTimeout())
                    .connectionAcquisitionTimeout(properties.getConnectTimeout())
                    .socketTimeout(properties.getOperationTimeout()))
            .overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apiCallTimeout(properties.getOperationTimeout())
                    .apiCallAttemptTimeout(properties.getOperationTimeout())
                    .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build())
                    .build());
    if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank())
      builder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
    // Without an explicit key pair the SDK owns its default credentials chain and shutdown.
    return builder.build();
  }

  @Bean(destroyMethod = "close")
  S3EvidenceObjectStorage s3EvidenceObjectStorage(
      S3Client client, S3EvidenceStorageProperties properties) {
    return new S3EvidenceObjectStorage(
        client,
        properties.getBucket(),
        properties.getOperationTimeout(),
        properties.getMaxConcurrentOperations(),
        properties.isMinioFixture());
  }
}
