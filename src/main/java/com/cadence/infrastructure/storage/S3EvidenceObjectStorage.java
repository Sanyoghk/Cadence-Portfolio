package com.cadence.infrastructure.storage;

import com.cadence.application.ports.EvidenceObjectLifecycleStorage;
import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.QuarantineReadFailure;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclRequest;
import software.amazon.awssdk.services.s3.model.GetBucketAclResponse;
import software.amazon.awssdk.services.s3.model.GetBucketPolicyRequest;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.Permission;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.Type;

/**
 * Private, immutable, bounded S3 contract. No public URLs, client-selected buckets, object listing,
 * or automatic bucket creation. The caller owns durable lifecycle generation fencing.
 */
public final class S3EvidenceObjectStorage
    implements EvidenceObjectStorage, EvidenceObjectLifecycleStorage, AutoCloseable {
  private static final ObjectMapper JSON =
      new ObjectMapper(
              JsonFactory.builder()
                  .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                  .streamReadConstraints(
                      StreamReadConstraints.builder().maxNestingDepth(32).build())
                  .build())
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final S3Client client;
  private final String bucket;
  private final Duration timeout;
  private final Semaphore capacity;
  private final boolean minioFixture;
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofPlatform().daemon().name("evidence-s3-deadline").factory());

  public S3EvidenceObjectStorage(
      S3Client client, String bucket, Duration timeout, int concurrency) {
    this(client, bucket, timeout, concurrency, false);
  }

  public S3EvidenceObjectStorage(
      S3Client client, String bucket, Duration timeout, int concurrency, boolean minioFixture) {
    if (client == null
        || bucket == null
        || !bucket.matches("[a-z0-9][a-z0-9-]{1,61}[a-z0-9]")
        || timeout == null
        || timeout.compareTo(Duration.ofSeconds(1)) < 0
        || timeout.compareTo(Duration.ofSeconds(60)) > 0
        || concurrency < 1
        || concurrency > 64)
      throw new IllegalArgumentException("Invalid private S3 evidence configuration");
    this.client = client;
    this.bucket = bucket;
    this.timeout = timeout;
    this.capacity = new Semaphore(concurrency);
    this.minioFixture = minioFixture;
    try {
      verifyPrivateBucket(new Deadline(timeout));
    } catch (RuntimeException failure) {
      watchdog.shutdownNow();
      throw new IllegalStateException("Private S3 evidence bucket validation failed");
    }
  }

  @Override
  public String storeQuarantined(String objectKey, byte[] content) {
    return storeObject(validateKey(objectKey), content, "quarantine:");
  }

  private String storeObject(String key, byte[] content, String referencePrefix) {
    if (content == null || content.length == 0 || content.length > MAX_QUARANTINE_BYTES)
      throw new IllegalArgumentException("Invalid evidence object size");
    return bounded(
        deadline -> {
          byte[] owned = content.clone();
          verifyPrivateBucket(deadline);
          byte[] hash = hash(owned);
          try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .ifNoneMatch("*")
                    .contentType("application/octet-stream")
                    .cacheControl("no-store")
                    .metadata(Map.of("sha256", HexFormat.of().formatHex(hash)))
                    .checksumSHA256(Base64.getEncoder().encodeToString(hash))
                    .overrideConfiguration(deadline.configuration())
                    .build(),
                RequestBody.fromBytes(owned));
          } catch (S3Exception conflict) {
            if (conflict.statusCode() != 412) throw unavailable();
            // A successful competing write is idempotent only when the complete original bytes
            // match.
            byte[] existing;
            try {
              existing = readObject(key, owned.length, deadline);
            } catch (QuarantineReadFailure duplicateFailure) {
              if (duplicateFailure.code() == Code.SIZE_LIMIT
                  || duplicateFailure.code() == Code.INVALID_CONTENT
                  || duplicateFailure.code() == Code.CHECKSUM_MISMATCH)
                throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
              throw unavailable();
            }
            if (!MessageDigest.isEqual(owned, existing))
              throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
          }
          deadline.remaining();
          return referencePrefix + key;
        });
  }

  public void storeMedia(com.cadence.domain.MediaObjectKey key, byte[] content) {
    key.verify(content);
    storeObject(key.key(), content, "media:");
  }

  public byte[] readMedia(com.cadence.domain.MediaObjectKey key, int maxBytes) {
    if (maxBytes < 1 || maxBytes > MAX_QUARANTINE_BYTES)
      throw new QuarantineReadFailure(Code.SIZE_LIMIT);
    return bounded(
        deadline -> {
          verifyPrivateBucket(deadline);
          return readObject(key.key(), maxBytes, deadline);
        });
  }

  @Override
  public byte[] readQuarantined(String reference, int maxBytes) {
    String key = referenceKey(reference);
    if (maxBytes < 1 || maxBytes > MAX_QUARANTINE_BYTES)
      throw new QuarantineReadFailure(Code.SIZE_LIMIT);
    return bounded(
        deadline -> {
          verifyPrivateBucket(deadline);
          return readObject(key, maxBytes, deadline);
        });
  }

  @Override
  public void deleteQuarantined(String reference) {
    String key = referenceKey(reference);
    bounded(
        deadline -> {
          verifyPrivateBucket(deadline);
          client.deleteObject(
              DeleteObjectRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .overrideConfiguration(deadline.configuration())
                  .build());
          deadline.remaining();
          return null;
        });
  }

  private byte[] readObject(String key, int maxBytes, Deadline deadline) {
    ResponseInputStream<GetObjectResponse> stream;
    try {
      stream =
          client.getObject(
              GetObjectRequest.builder()
                  .bucket(bucket)
                  .key(key)
                  .overrideConfiguration(deadline.configuration())
                  .build());
    } catch (S3Exception failure) {
      if (failure.statusCode() == 404) throw new QuarantineReadFailure(Code.NOT_FOUND);
      throw unavailable();
    }
    // SDK API call timeout ends once response headers arrive. Abort the actual response body at
    // the remaining total operation deadline as well; a slow-drip server cannot hold a slot
    // forever.
    java.util.concurrent.ScheduledFuture<?> expiry;
    try {
      expiry =
          watchdog.schedule(stream::abort, deadline.remaining().toNanos(), TimeUnit.NANOSECONDS);
    } catch (RuntimeException failure) {
      stream.abort();
      throw unavailable();
    }
    try {
      Long size = stream.response().contentLength();
      if (size == null || size < 1) throw new QuarantineReadFailure(Code.INVALID_CONTENT);
      if (size > maxBytes) throw new QuarantineReadFailure(Code.SIZE_LIMIT);
      String expectedHash = stream.response().metadata().get("sha256");
      if (expectedHash == null || !expectedHash.matches("[a-f0-9]{64}"))
        throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
      byte[] content = stream.readNBytes(maxBytes + 1);
      deadline.remaining();
      if (content.length > maxBytes) throw new QuarantineReadFailure(Code.SIZE_LIMIT);
      if (content.length != size) throw new QuarantineReadFailure(Code.INVALID_CONTENT);
      if (!MessageDigest.isEqual(hash(content), HexFormat.of().parseHex(expectedHash)))
        throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
      return content;
    } catch (IOException failure) {
      stream.abort();
      throw unavailable();
    } finally {
      expiry.cancel(false);
      // Never drain a hostile oversized response during close(); abort releases its connection.
      stream.abort();
    }
  }

  private void verifyPrivateBucket(Deadline deadline) {
    client.headBucket(
        HeadBucketRequest.builder()
            .bucket(bucket)
            .overrideConfiguration(deadline.configuration())
            .build());
    try {
      String policy =
          client
              .getBucketPolicy(
                  GetBucketPolicyRequest.builder()
                      .bucket(bucket)
                      .overrideConfiguration(deadline.configuration())
                      .build())
              .policy();
      requireDenyOnlyPolicy(policy);
    } catch (S3Exception noPolicy) {
      if (noPolicy.statusCode() != 404
          || noPolicy.awsErrorDetails() == null
          || !"NoSuchBucketPolicy".equals(noPolicy.awsErrorDetails().errorCode()))
        throw unavailable();
    }
    var acl =
        client.getBucketAcl(
            GetBucketAclRequest.builder()
                .bucket(bucket)
                .overrideConfiguration(deadline.configuration())
                .build());
    String owner = acl.owner() == null ? null : acl.owner().id();
    if (owner == null || owner.isBlank()) {
      if (!minioFixture || !syntheticMinioPrivateAcl(acl)) throw unavailable();
    } else {
      for (var grant : acl.grants()) {
        if (grant.grantee() == null
            || grant.grantee().type() != Type.CANONICAL_USER
            || !owner.equals(grant.grantee().id())) throw unavailable();
      }
    }
    var versioning =
        client.getBucketVersioning(
            GetBucketVersioningRequest.builder()
                .bucket(bucket)
                .overrideConfiguration(deadline.configuration())
                .build());
    if (versioning.status() == BucketVersioningStatus.ENABLED
        || versioning.status() == BucketVersioningStatus.SUSPENDED
        || versioning.status() == BucketVersioningStatus.UNKNOWN_TO_SDK_VERSION)
      throw unavailable();
  }

  // MinIO has no bucket ACL feature and reports this exact synthetic private ACL. This
  // deliberately narrow fixture capability is forbidden by prod configuration validation.
  private static boolean syntheticMinioPrivateAcl(GetBucketAclResponse acl) {
    if (acl.owner() == null
        || !empty(acl.owner().id())
        || !empty(acl.owner().displayName())
        || acl.grants().size() != 1) return false;
    var grant = acl.grants().getFirst();
    var grantee = grant.grantee();
    return grant.permission() == Permission.FULL_CONTROL
        && grantee != null
        && grantee.type() == Type.CANONICAL_USER
        && empty(grantee.id())
        && empty(grantee.displayName())
        && empty(grantee.uri())
        && empty(grantee.emailAddress());
  }

  private static boolean empty(String value) {
    return value == null || value.isEmpty();
  }

  private static void requireDenyOnlyPolicy(String policy) {
    try {
      if (policy == null || policy.length() > 20_480) throw unavailable();
      JsonNode root = JSON.readTree(policy);
      if (root == null || !root.isObject()) throw unavailable();
      JsonNode statements = root.get("Statement");
      if (statements == null || !(statements.isArray() || statements.isObject()))
        throw unavailable();
      if (statements.isObject()) {
        if (!"Deny".equals(statements.path("Effect").asText())) throw unavailable();
      } else {
        for (JsonNode statement : statements)
          if (!statement.isObject() || !"Deny".equals(statement.path("Effect").asText()))
            throw unavailable();
      }
    } catch (IOException invalid) {
      throw unavailable();
    }
  }

  private <T> T bounded(Operation<T> operation) {
    if (!capacity.tryAcquire()) throw unavailable();
    try {
      return operation.run(new Deadline(timeout));
    } catch (QuarantineReadFailure failure) {
      throw failure;
    } catch (RuntimeException providerFailure) {
      throw unavailable();
    } finally {
      capacity.release();
    }
  }

  private static String referenceKey(String reference) {
    if (reference == null || !reference.startsWith("quarantine:"))
      throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    return validateKey(reference.substring("quarantine:".length()));
  }

  private static String validateKey(String key) {
    if (key == null || key.length() > 400 || !key.startsWith("quarantine/"))
      throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    for (String segment : key.split("/", -1)) {
      if (segment.length() > 128
          || !segment.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*")
          || segment
              .toUpperCase(Locale.ROOT)
              .matches("(?:CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\..*)?"))
        throw new QuarantineReadFailure(Code.INVALID_REFERENCE);
    }
    return key;
  }

  private static byte[] hash(byte[] content) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(content);
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  private static QuarantineReadFailure unavailable() {
    return new QuarantineReadFailure(Code.UNAVAILABLE);
  }

  @Override
  public void close() {
    watchdog.shutdownNow();
  }

  private interface Operation<T> {
    T run(Deadline deadline);
  }

  private static final class Deadline {
    private final long expires;

    Deadline(Duration duration) {
      expires = System.nanoTime() + duration.toNanos();
    }

    Duration remaining() {
      long nanos = expires - System.nanoTime();
      if (nanos < 1_000_000) throw unavailable();
      return Duration.ofNanos(nanos);
    }

    AwsRequestOverrideConfiguration configuration() {
      Duration available = remaining();
      return AwsRequestOverrideConfiguration.builder()
          .apiCallTimeout(available)
          .apiCallAttemptTimeout(available)
          .build();
    }
  }
}
