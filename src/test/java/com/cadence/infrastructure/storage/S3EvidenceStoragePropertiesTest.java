package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class S3EvidenceStoragePropertiesTest {
  @Test
  void productionRequiresTlsAndCompleteExplicitCredentialsOrDefaultChain() {
    var properties = valid();
    assertThatCode(() -> properties.validate(true)).doesNotThrowAnyException();
    properties.setAccessKey("access");
    assertThatThrownBy(() -> properties.validate(true))
        .hasMessage("Invalid private S3 evidence configuration");
    properties.setSecretKey("secret");
    assertThatCode(() -> properties.validate(true)).doesNotThrowAnyException();
    assertThat(properties.toString()).doesNotContain("access", "secret");
    properties.setEndpoint("http://minio:9000");
    properties.setAllowInsecureLocal(true);
    assertThatCode(() -> properties.validate(false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> properties.validate(true))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void syntheticMinioAclCapabilityIsExplicitAndForbiddenInProductionBeforeClientCreation() {
    var properties = valid();
    assertThat(properties.isMinioFixture()).isFalse();
    properties.setMinioFixture(true);
    assertThatCode(() -> properties.validate(false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> properties.validate(true))
        .isInstanceOf(IllegalArgumentException.class);
    new ApplicationContextRunner()
        .withUserConfiguration(S3EvidenceStorageConfiguration.class)
        .withPropertyValues(
            "spring.profiles.active=prod,s3",
            "cadence.evidence.s3.endpoint=https://storage.example",
            "cadence.evidence.s3.bucket=private-evidence",
            "cadence.evidence.s3.minio-fixture=true")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsUntrustedEndpointShapesBucketsLimitsAndDurations() {
    for (String endpoint :
        List.of(
            "https://user:password@minio.internal",
            "https://minio.internal?key=secret",
            "https://minio.internal/path",
            "file:///tmp",
            "https://minio.internal#fragment",
            "https://minio.internal:0")) {
      var properties = valid();
      properties.setEndpoint(endpoint);
      assertThatThrownBy(() -> properties.validate(true))
          .hasMessage("Invalid private S3 evidence configuration")
          .hasNoCause();
    }
    var bucket = valid();
    bucket.setBucket("../public");
    assertThatThrownBy(() -> bucket.validate(true)).isInstanceOf(IllegalArgumentException.class);
    var concurrent = valid();
    concurrent.setMaxConcurrentOperations(100);
    assertThatThrownBy(() -> concurrent.validate(true))
        .isInstanceOf(IllegalArgumentException.class);
    var timing = valid();
    timing.setOperationTimeout(Duration.ZERO);
    assertThatThrownBy(() -> timing.validate(true)).isInstanceOf(IllegalArgumentException.class);
    timing.setOperationTimeout(Duration.ofSeconds(1));
    timing.setConnectTimeout(Duration.ofSeconds(3));
    assertThatThrownBy(() -> timing.validate(true)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void incompleteS3ProfileFailsDuringStartupBeforeCreatingClient() {
    new ApplicationContextRunner()
        .withUserConfiguration(S3EvidenceStorageConfiguration.class)
        .withPropertyValues("spring.profiles.active=prod,s3")
        .run(context -> assertThat(context).hasFailed());
  }

  private static S3EvidenceStorageProperties valid() {
    var properties = new S3EvidenceStorageProperties();
    properties.setEndpoint("https://minio.internal:9000");
    properties.setBucket("private-evidence");
    return properties;
  }
}
