package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.QuarantineReadFailure;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

/** Real S3 wire contract, including least-privilege runtime credentials and anonymous rejection. */
@Testcontainers(disabledWithoutDocker = true)
class MinioEvidenceObjectStorageTest {
  private static final String ROOT_USER = "fixture-" + UUID.randomUUID();
  private static final String ROOT_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
  private static final String APP_USER = "app-" + UUID.randomUUID();
  private static final String APP_SECRET = UUID.randomUUID() + "-" + UUID.randomUUID();
  private static final String BUCKET = "private-evidence";

  @Container
  static final GenericContainer<?> MINIO =
      new GenericContainer<>(com.cadence.support.TestContainerImages.minio())
          .withEnv("MINIO_ROOT_USER", ROOT_USER)
          .withEnv("MINIO_ROOT_PASSWORD", ROOT_SECRET)
          .withEnv("MINIO_BROWSER", "off")
          .withExposedPorts(9000)
          .waitingFor(
              Wait.forHttp("/minio/health/ready")
                  .forPort(9000)
                  .withStartupTimeout(Duration.ofMinutes(3)));

  private static S3Client admin;
  private static S3Client runtime;
  private static S3EvidenceObjectStorage storage;

  @BeforeAll
  static void setup() throws Exception {
    String endpoint = endpoint();
    admin =
        S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(ROOT_USER, ROOT_SECRET)))
            .forcePathStyle(true)
            .build();
    admin.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    command("mc", "alias", "set", "fixture", "http://127.0.0.1:9000", ROOT_USER, ROOT_SECRET);
    command("mc", "admin", "user", "add", "fixture", APP_USER, APP_SECRET);
    String policy =
        """
        {"Version":"2012-10-17","Statement":[
          {"Effect":"Allow","Action":["s3:ListBucket","s3:GetBucketPolicy","s3:GetBucketVersioning"],"Resource":["arn:aws:s3:::private-evidence"]},
          {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject","s3:DeleteObject"],"Resource":["arn:aws:s3:::private-evidence/quarantine/*"]},
          {"Effect":"Allow","Action":["s3:PutObject","s3:GetObject"],"Resource":["arn:aws:s3:::private-evidence/media/*"]}
        ]}
        """;
    MINIO.copyFileToContainer(Transferable.of(policy), "/tmp/runtime-policy.json");
    command(
        "mc",
        "admin",
        "policy",
        "create",
        "fixture",
        "evidence-runtime",
        "/tmp/runtime-policy.json");
    command("mc", "admin", "policy", "attach", "fixture", "evidence-runtime", "--user", APP_USER);
    var properties = new S3EvidenceStorageProperties();
    properties.setEndpoint(endpoint);
    properties.setBucket(BUCKET);
    properties.setAccessKey(APP_USER);
    properties.setSecretKey(APP_SECRET);
    properties.setAllowInsecureLocal(true);
    properties.setMinioFixture(true);
    properties.setOperationTimeout(Duration.ofSeconds(15));
    runtime =
        new S3EvidenceStorageConfiguration()
            .evidenceS3Client(
                properties,
                new MockEnvironment().withProperty("spring.profiles.active", "test,s3"));
    verifyRuntimeBucketContract();
    storage = new S3EvidenceObjectStorage(runtime, BUCKET, Duration.ofSeconds(15), 8, true);
  }

  private static void verifyRuntimeBucketContract() {
    String operation = "HeadBucket";
    try {
      runtime.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
      operation = "GetBucketPolicy";
      try {
        runtime.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(BUCKET).build());
      } catch (S3Exception absent) {
        assertThat(absent.statusCode()).as("Private bucket policy response status").isEqualTo(404);
        assertThat(absent.awsErrorDetails().errorCode())
            .as("Private bucket missing policy code")
            .isEqualTo("NoSuchBucketPolicy");
      }
      operation = "GetBucketAcl";
      var acl = runtime.getBucketAcl(GetBucketAclRequest.builder().bucket(BUCKET).build());
      assertThat(acl.owner()).as("Private bucket ACL owner").isNotNull();
      assertThat(acl.owner().id()).as("Synthetic MinIO owner ID").isNullOrEmpty();
      assertThat(acl.grants()).as("Synthetic MinIO private ACL").hasSize(1);
      for (Grant grant : acl.grants()) {
        assertThat(grant.grantee().type())
            .as("Private bucket ACL grant type")
            .isEqualTo(Type.CANONICAL_USER);
        assertThat(grant.grantee().id()).as("Synthetic MinIO canonical ID").isNullOrEmpty();
        assertThat(grant.permission()).isEqualTo(Permission.FULL_CONTROL);
      }
      operation = "GetBucketVersioning";
      var versioning =
          runtime.getBucketVersioning(GetBucketVersioningRequest.builder().bucket(BUCKET).build());
      assertThat(versioning.status()).as("Private bucket versioning state").isNull();
    } catch (S3Exception failure) {
      throw new AssertionError(
          "MinIO runtime " + operation + " failed with status " + failure.statusCode());
    }
  }

  @AfterAll
  static void teardown() {
    if (storage != null) storage.close();
    if (runtime != null) runtime.close();
    if (admin != null) admin.close();
  }

  @Test
  void syntheticAclRequiresExplicitLocalFixtureCapabilityAndPublicAclIsUnsupported() {
    assertThatThrownBy(
            () -> new S3EvidenceObjectStorage(runtime, BUCKET, Duration.ofSeconds(15), 1))
        .hasMessage("Private S3 evidence bucket validation failed");
    assertThatThrownBy(
            () ->
                admin.putBucketAcl(
                    PutBucketAclRequest.builder()
                        .bucket(BUCKET)
                        .acl(BucketCannedACL.PUBLIC_READ)
                        .build()))
        .isInstanceOfSatisfying(
            S3Exception.class,
            failure ->
                assertThat(failure.awsErrorDetails().errorCode()).isEqualTo("NotImplemented"));
  }

  @Test
  void retainedMediaIsPrivateImmutableBoundedAndCannotBeDeletedByRuntime() throws Exception {
    for (var fixture : com.cadence.domain.DemoMediaFixture.values()) {
      byte[] bytes;
      try (var in =
          MinioEvidenceObjectStorageTest.class.getResourceAsStream(
              "/demo-media/" + fixture.resource())) {
        bytes = in.readAllBytes();
      }
      var key =
          new com.cadence.domain.MediaObjectKey(
              UUID.randomUUID(), UUID.randomUUID(), fixture.checksum(), fixture.format());
      storage.storeMedia(key, bytes);
      storage.storeMedia(key, bytes);
      assertThat(storage.readMedia(key, bytes.length)).containsExactly(bytes);
      expectCode(Code.SIZE_LIMIT, () -> storage.readMedia(key, bytes.length - 1));
      assertThatThrownBy(() -> storage.readQuarantined("quarantine:" + key.key(), bytes.length))
          .isInstanceOf(QuarantineReadFailure.class);
      assertThatThrownBy(() -> storage.deleteQuarantined("media:" + key.key()))
          .isInstanceOf(QuarantineReadFailure.class);
      assertThatThrownBy(
              () ->
                  runtime.deleteObject(
                      DeleteObjectRequest.builder().bucket(BUCKET).key(key.key()).build()))
          .isInstanceOfSatisfying(
              S3Exception.class, e -> assertThat(e.statusCode()).isEqualTo(403));
      var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create(endpoint() + "/" + BUCKET + "/" + key.key()))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.discarding());
      assertThat(response.statusCode()).isEqualTo(403);
    }
  }

  @Test
  void storesAndReadsCompleteOriginalBytesAndDeletesIdempotently() {
    String key = key();
    byte[] content = {0, 1, 2, 3, 4, 5};
    String reference = storage.storeQuarantined(key, content);
    assertThat(reference).isEqualTo("quarantine:" + key);
    assertThat(storage.readQuarantined(reference, content.length)).containsExactly(content);
    storage.deleteQuarantined(reference);
    storage.deleteQuarantined(reference);
    expectCode(Code.NOT_FOUND, () -> storage.readQuarantined(reference, content.length));
  }

  @Test
  void simultaneousIdenticalWritesAreIdempotentDifferentWritesNeverOverwrite() throws Exception {
    String key = key();
    byte[] content = {9, 8, 7};
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var writes =
          executor.invokeAll(
              List.of(
                  () -> storage.storeQuarantined(key, content),
                  () -> storage.storeQuarantined(key, content),
                  () -> storage.storeQuarantined(key, content)));
      for (var result : writes) assertThat(result.get()).isEqualTo("quarantine:" + key);
    }
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.storeQuarantined(key, new byte[] {1, 2, 3}));
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.storeQuarantined(key, new byte[] {9, 8}));
    assertThat(storage.readQuarantined("quarantine:" + key, 3)).containsExactly(content);
  }

  @Test
  void boundedReadAndMetadataTamperingAreRejected() {
    String key = key();
    String reference = storage.storeQuarantined(key, new byte[] {1, 2, 3});
    expectCode(Code.SIZE_LIMIT, () -> storage.readQuarantined(reference, 2));
    admin.putObject(
        PutObjectRequest.builder()
            .bucket(BUCKET)
            .key(key)
            .metadata(Map.of("sha256", "a".repeat(64)))
            .build(),
        RequestBody.fromBytes(new byte[] {1, 2, 3}));
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.readQuarantined(reference, 3));
  }

  @Test
  void bucketAndObjectsRejectAnonymousRequestsAndRuntimeCannotChangePrivacy() throws Exception {
    String key = key();
    storage.storeQuarantined(key, new byte[] {1});
    try (HttpClient anonymous = HttpClient.newHttpClient()) {
      for (String path : List.of("/" + BUCKET, "/" + BUCKET + "/" + key)) {
        var response =
            anonymous.send(
                HttpRequest.newBuilder(URI.create(endpoint() + path))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(403);
      }
    }
    assertThatThrownBy(
            () ->
                runtime.putBucketPolicy(
                    PutBucketPolicyRequest.builder().bucket(BUCKET).policy(publicPolicy()).build()))
        .isInstanceOfSatisfying(
            S3Exception.class, failure -> assertThat(failure.statusCode()).isEqualTo(403));
    assertThatThrownBy(
            () ->
                runtime.createBucket(
                    CreateBucketRequest.builder().bucket("unauthorized-bucket").build()))
        .isInstanceOfSatisfying(
            S3Exception.class, failure -> assertThat(failure.statusCode()).isEqualTo(403));
    assertThatThrownBy(
            () ->
                runtime.putObject(
                    PutObjectRequest.builder().bucket(BUCKET).key("outside-prefix").build(),
                    RequestBody.fromBytes(new byte[] {1})))
        .isInstanceOfSatisfying(
            S3Exception.class, failure -> assertThat(failure.statusCode()).isEqualTo(403));
  }

  @Test
  void bucketPolicyDriftFailsClosedAndRestoringPrivacyRecovers() {
    admin.putBucketPolicy(
        PutBucketPolicyRequest.builder().bucket(BUCKET).policy(publicPolicy()).build());
    try {
      expectCode(Code.UNAVAILABLE, () -> storage.storeQuarantined(key(), new byte[] {1}));
      assertThatThrownBy(
              () -> new S3EvidenceObjectStorage(runtime, BUCKET, Duration.ofSeconds(15), 1, true))
          .hasMessage("Private S3 evidence bucket validation failed");
    } finally {
      admin.deleteBucketPolicy(DeleteBucketPolicyRequest.builder().bucket(BUCKET).build());
    }
    assertThat(storage.storeQuarantined(key(), new byte[] {1})).startsWith("quarantine:");
  }

  @Test
  void versionedBucketCannotSilentlyRetainDeletedSensitiveVersions() {
    String versioned = "versioned-evidence";
    admin.createBucket(CreateBucketRequest.builder().bucket(versioned).build());
    admin.putBucketVersioning(
        PutBucketVersioningRequest.builder()
            .bucket(versioned)
            .versioningConfiguration(
                VersioningConfiguration.builder().status(BucketVersioningStatus.ENABLED).build())
            .build());
    assertThatThrownBy(
            () -> new S3EvidenceObjectStorage(admin, versioned, Duration.ofSeconds(15), 1, true))
        .hasMessage("Private S3 evidence bucket validation failed");
  }

  private static void command(String... arguments) throws Exception {
    var result = MINIO.execInContainer(arguments);
    assertThat(result.getExitCode())
        .withFailMessage("Private MinIO fixture bootstrap failed")
        .isZero();
  }

  private static String key() {
    return "quarantine/"
        + UUID.randomUUID()
        + "/"
        + "b".repeat(64)
        + "-"
        + UUID.randomUUID()
        + ".pdf";
  }

  private static String endpoint() {
    return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
  }

  private static String publicPolicy() {
    return "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":\"*\",\"Action\":\"s3:GetObject\",\"Resource\":\"arn:aws:s3:::private-evidence/*\"}]}";
  }

  private static void expectCode(Code code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            QuarantineReadFailure.class, failure -> assertThat(failure.code()).isEqualTo(code));
  }
}
