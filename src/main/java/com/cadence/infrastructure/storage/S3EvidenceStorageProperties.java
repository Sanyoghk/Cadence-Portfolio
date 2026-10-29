package com.cadence.infrastructure.storage;

import java.net.URI;
import java.time.Duration;

/**
 * Explicit properties rather than a record: credentials must never appear in generated toString.
 */
public final class S3EvidenceStorageProperties {
  private String endpoint;
  private String region = "us-east-1";
  private String bucket;
  private String accessKey;
  private String secretKey;
  private boolean allowInsecureLocal;
  private boolean minioFixture;
  private Duration operationTimeout = Duration.ofSeconds(15);
  private Duration connectTimeout = Duration.ofSeconds(3);
  private int maxConcurrentOperations = 8;

  public void validate(boolean production) {
    URI uri;
    try {
      uri = URI.create(endpoint == null ? "" : endpoint);
    } catch (IllegalArgumentException invalid) {
      throw invalidConfiguration();
    }
    if (uri.getHost() == null
        || uri.getUserInfo() != null
        || uri.getQuery() != null
        || uri.getFragment() != null
        || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
        || uri.getPort() == 0
        || uri.getPort() > 65535
        || !("https".equals(uri.getScheme())
            || (!production && allowInsecureLocal && "http".equals(uri.getScheme())))
        || (production && allowInsecureLocal)
        || (production && minioFixture)
        || bucket == null
        || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
        || region == null
        || !region.matches("[a-z0-9][a-z0-9-]{0,62}")
        || invalidDuration(operationTimeout, 1, 60)
        || invalidDuration(connectTimeout, 1, 10)
        || connectTimeout.compareTo(operationTimeout) > 0
        || maxConcurrentOperations < 1
        || maxConcurrentOperations > 64
        || hasText(accessKey) != hasText(secretKey)
        || (hasText(accessKey) && (accessKey.length() > 256 || secretKey.length() > 512))) {
      throw invalidConfiguration();
    }
  }

  private static boolean invalidDuration(Duration duration, int minSeconds, int maxSeconds) {
    return duration == null
        || duration.compareTo(Duration.ofSeconds(minSeconds)) < 0
        || duration.compareTo(Duration.ofSeconds(maxSeconds)) > 0;
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private static IllegalArgumentException invalidConfiguration() {
    return new IllegalArgumentException("Invalid private S3 evidence configuration");
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String value) {
    endpoint = value;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String value) {
    region = value;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String value) {
    bucket = value;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String value) {
    accessKey = value;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String value) {
    secretKey = value;
  }

  public boolean isAllowInsecureLocal() {
    return allowInsecureLocal;
  }

  public void setAllowInsecureLocal(boolean value) {
    allowInsecureLocal = value;
  }

  public boolean isMinioFixture() {
    return minioFixture;
  }

  public void setMinioFixture(boolean value) {
    minioFixture = value;
  }

  public Duration getOperationTimeout() {
    return operationTimeout;
  }

  public void setOperationTimeout(Duration value) {
    operationTimeout = value;
  }

  public Duration getConnectTimeout() {
    return connectTimeout;
  }

  public void setConnectTimeout(Duration value) {
    connectTimeout = value;
  }

  public int getMaxConcurrentOperations() {
    return maxConcurrentOperations;
  }

  public void setMaxConcurrentOperations(int value) {
    maxConcurrentOperations = value;
  }
}
