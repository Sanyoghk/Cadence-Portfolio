package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.QuarantineReadFailure;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3EvidenceObjectStorageTest {
  private static final String KEY = "quarantine/brand/" + "a".repeat(64) + "-generation.pdf";
  private static final String REFERENCE = "quarantine:" + KEY;
  private final S3Client client = mock(S3Client.class);
  private S3EvidenceObjectStorage storage;

  @BeforeEach
  void setup() {
    privateBucket(client);
    storage = new S3EvidenceObjectStorage(client, "private-evidence", Duration.ofSeconds(2), 1);
    clearInvocations(client);
  }

  @AfterEach
  void close() {
    storage.close();
  }

  static void privateBucket(S3Client client) {
    doThrow(
            S3Exception.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucketPolicy").build())
                .build())
        .when(client)
        .getBucketPolicy(any(GetBucketPolicyRequest.class));
    when(client.getBucketAcl(any(GetBucketAclRequest.class)))
        .thenReturn(
            GetBucketAclResponse.builder()
                .owner(Owner.builder().id("owner").build())
                .grants(
                    Grant.builder()
                        .grantee(Grantee.builder().type(Type.CANONICAL_USER).id("owner").build())
                        .permission(Permission.FULL_CONTROL)
                        .build())
                .build());
    when(client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
        .thenReturn(GetBucketVersioningResponse.builder().build());
  }

  @Test
  void immutableWriteSendsConditionAndChecksumAndReturnsOnlyInternalReference() throws Exception {
    byte[] content = {1, 2, 3};
    assertThat(storage.storeQuarantined(KEY, content)).isEqualTo(REFERENCE);
    ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
    verify(client).putObject(request.capture(), any(RequestBody.class));
    assertThat(request.getValue().ifNoneMatch()).isEqualTo("*");
    assertThat(request.getValue().bucket()).isEqualTo("private-evidence");
    assertThat(request.getValue().acl()).isNull();
    assertThat(request.getValue().metadata()).containsEntry("sha256", sha(content));
    assertThat(request.getValue().contentType()).isEqualTo("application/octet-stream");
    assertThat(request.getValue().overrideConfiguration().orElseThrow().apiCallTimeout())
        .hasValueSatisfying(
            duration -> assertThat(duration).isLessThanOrEqualTo(Duration.ofSeconds(2)));
  }

  @Test
  void duplicateSameBytesIsIdempotentButDifferentBytesCannotOverwrite() throws Exception {
    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenThrow(S3Exception.builder().statusCode(412).build());
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(invocation -> response(new byte[] {1, 2, 3}));
    assertThat(storage.storeQuarantined(KEY, new byte[] {1, 2, 3})).isEqualTo(REFERENCE);
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.storeQuarantined(KEY, new byte[] {4, 5, 6}));
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.storeQuarantined(KEY, new byte[] {1, 2}));
    expectCode(
        Code.CHECKSUM_MISMATCH, () -> storage.storeQuarantined(KEY, new byte[] {1, 2, 3, 4}));
    verify(client, times(4)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
  }

  @Test
  void readRejectsOversizedEmptyTruncatedAndChecksumTamperedObjects() throws Exception {
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(invocation -> response(new byte[] {1, 2, 3}));
    assertThat(storage.readQuarantined(REFERENCE, 3)).containsExactly(1, 2, 3);
    expectCode(Code.SIZE_LIMIT, () -> storage.readQuarantined(REFERENCE, 2));
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(invocation -> response(new byte[0]));
    expectCode(Code.INVALID_CONTENT, () -> storage.readQuarantined(REFERENCE, 3));
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(
            invocation ->
                stream(
                    GetObjectResponse.builder()
                        .contentLength(3L)
                        .metadata(Map.of("sha256", sha(new byte[] {1})))
                        .build(),
                    new byte[] {1}));
    expectCode(Code.INVALID_CONTENT, () -> storage.readQuarantined(REFERENCE, 3));
    when(client.getObject(any(GetObjectRequest.class)))
        .thenAnswer(
            invocation ->
                stream(
                    GetObjectResponse.builder()
                        .contentLength(1L)
                        .metadata(Map.of("sha256", "a".repeat(64)))
                        .build(),
                    new byte[] {1}));
    expectCode(Code.CHECKSUM_MISMATCH, () -> storage.readQuarantined(REFERENCE, 3));
  }

  @Test
  void malformedReferencesAndLimitsFailBeforeProviderCalls() {
    for (String reference :
        List.of(
            "https://s3.example/private/file",
            "quarantine:../outside",
            "quarantine:quarantine/../outside",
            "quarantine:quarantine//empty",
            "quarantine:quarantine/CON",
            "quarantine:quarantine/encoded%2Fkey",
            "quarantine:quarantine/back\\slash")) {
      expectCode(Code.INVALID_REFERENCE, () -> storage.readQuarantined(reference, 3));
      expectCode(Code.INVALID_REFERENCE, () -> storage.deleteQuarantined(reference));
    }
    expectCode(Code.SIZE_LIMIT, () -> storage.readQuarantined(REFERENCE, 0));
    expectCode(Code.SIZE_LIMIT, () -> storage.readQuarantined(REFERENCE, 10 * 1024 * 1024 + 1));
    assertThatThrownBy(() -> storage.storeQuarantined(KEY, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> storage.storeQuarantined(KEY, null))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(client);
  }

  @Test
  void privacyChangesFailClosedForReadsWritesAndDeletion() {
    doReturn(
            GetBucketPolicyResponse.builder()
                .policy("{\"Statement\":{\"Effect\":\"Allow\",\"Principal\":\"*\"}}")
                .build())
        .when(client)
        .getBucketPolicy(any(GetBucketPolicyRequest.class));
    expectCode(Code.UNAVAILABLE, () -> storage.readQuarantined(REFERENCE, 3));
    expectCode(Code.UNAVAILABLE, () -> storage.storeQuarantined(KEY, new byte[] {1}));
    expectCode(Code.UNAVAILABLE, () -> storage.deleteQuarantined(REFERENCE));
    verify(client, never()).getObject(any(GetObjectRequest.class));
    verify(client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void invalidPolicyPublicAclCrossAccountAclAndVersioningRejectedAtStartup() {
    for (String policy :
        List.of(
            "bad",
            "null",
            "{}",
            "{\"Statement\":[{}]}",
            "{\"Statement\":\"invalid\"}",
            "{\"Statement\":{\"Effect\":\"Allow\",\"Effect\":\"Deny\"}}",
            "{\"Statement\":{\"Effect\":\"Deny\"}} {}")) {
      privateBucket(client);
      doReturn(GetBucketPolicyResponse.builder().policy(policy).build())
          .when(client)
          .getBucketPolicy(any(GetBucketPolicyRequest.class));
      invalidStartup();
    }
    for (Grantee grantee :
        List.of(
            Grantee.builder()
                .type(Type.GROUP)
                .uri("http://acs.amazonaws.com/groups/global/AllUsers")
                .build(),
            Grantee.builder().type(Type.CANONICAL_USER).id("other-account").build())) {
      privateBucket(client);
      when(client.getBucketAcl(any(GetBucketAclRequest.class)))
          .thenReturn(
              GetBucketAclResponse.builder()
                  .owner(Owner.builder().id("owner").build())
                  .grants(Grant.builder().grantee(grantee).permission(Permission.READ).build())
                  .build());
      invalidStartup();
    }
    for (BucketVersioningStatus status :
        List.of(BucketVersioningStatus.ENABLED, BucketVersioningStatus.SUSPENDED)) {
      privateBucket(client);
      when(client.getBucketVersioning(any(GetBucketVersioningRequest.class)))
          .thenReturn(GetBucketVersioningResponse.builder().status(status).build());
      invalidStartup();
    }
  }

  @Test
  void syntheticMinioAclRequiresExplicitCapabilityAndExactPrivateShape() {
    var canonical = Grantee.builder().type(Type.CANONICAL_USER).build();
    var privateGrant =
        Grant.builder().grantee(canonical).permission(Permission.FULL_CONTROL).build();
    when(client.getBucketAcl(any(GetBucketAclRequest.class)))
        .thenReturn(
            GetBucketAclResponse.builder()
                .owner(Owner.builder().id("").displayName("").build())
                .grants(privateGrant)
                .build());
    invalidStartup();
    try (var fixture =
        new S3EvidenceObjectStorage(client, "private-evidence", Duration.ofSeconds(2), 1, true)) {
      assertThat(fixture.storeQuarantined(KEY, new byte[] {1})).isEqualTo(REFERENCE);
      when(client.getBucketAcl(any(GetBucketAclRequest.class)))
          .thenReturn(
              GetBucketAclResponse.builder()
                  .owner(Owner.builder().build())
                  .grants(privateGrant, privateGrant)
                  .build());
      expectCode(Code.UNAVAILABLE, () -> fixture.storeQuarantined(KEY, new byte[] {1}));
    }
    for (Grant grant :
        List.of(
            Grant.builder().grantee(canonical).permission(Permission.READ).build(),
            Grant.builder()
                .grantee(Grantee.builder().type(Type.GROUP).build())
                .permission(Permission.FULL_CONTROL)
                .build(),
            Grant.builder()
                .grantee(Grantee.builder().type(Type.CANONICAL_USER).id("other-account").build())
                .permission(Permission.FULL_CONTROL)
                .build())) {
      when(client.getBucketAcl(any(GetBucketAclRequest.class)))
          .thenReturn(
              GetBucketAclResponse.builder().owner(Owner.builder().build()).grants(grant).build());
      assertThatThrownBy(
              () ->
                  new S3EvidenceObjectStorage(
                      client, "private-evidence", Duration.ofSeconds(2), 1, true))
          .hasMessage("Private S3 evidence bucket validation failed");
    }
  }

  @Test
  void denyOnlyBucketPolicyIsAllowed() {
    doReturn(
            GetBucketPolicyResponse.builder()
                .policy("{\"Statement\":[{\"Effect\":\"Deny\"}]}")
                .build())
        .when(client)
        .getBucketPolicy(any(GetBucketPolicyRequest.class));
    assertThat(storage.storeQuarantined(KEY, new byte[] {1})).isEqualTo(REFERENCE);
  }

  @Test
  void providerErrorsAreSanitizedMissingObjectsDistinguishedAndDeleteIdempotent() {
    when(client.getObject(any(GetObjectRequest.class)))
        .thenThrow(S3Exception.builder().statusCode(404).build());
    expectCode(Code.NOT_FOUND, () -> storage.readQuarantined(REFERENCE, 3));
    doThrow(SdkClientException.create("secret-access-key internal-host"))
        .when(client)
        .getObject(any(GetObjectRequest.class));
    assertThatThrownBy(() -> storage.readQuarantined(REFERENCE, 3))
        .isInstanceOf(QuarantineReadFailure.class)
        .hasMessage("Quarantine read failed: UNAVAILABLE")
        .hasNoCause();
    storage.deleteQuarantined(REFERENCE);
    storage.deleteQuarantined(REFERENCE);
    verify(client, times(2)).deleteObject(any(DeleteObjectRequest.class));
  }

  @Test
  void streamingDeadlineAbortsSlowBodyAndReturnsCapacity() throws Exception {
    CountDownLatch aborted = new CountDownLatch(1);
    InputStream stalled =
        new InputStream() {
          @Override
          public int read() throws IOException {
            try {
              if (!aborted.await(5, TimeUnit.SECONDS))
                throw new IOException("test deadline not reached");
              throw new IOException("connection aborted");
            } catch (InterruptedException interrupted) {
              Thread.currentThread().interrupt();
              throw new IOException("interrupted");
            }
          }
        };
    doReturn(
            new ResponseInputStream<>(
                GetObjectResponse.builder()
                    .contentLength(1L)
                    .metadata(Map.of("sha256", sha(new byte[] {1})))
                    .build(),
                AbortableInputStream.create(stalled, aborted::countDown)))
        .when(client)
        .getObject(any(GetObjectRequest.class));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var read = executor.submit(() -> storage.readQuarantined(REFERENCE, 1));
      assertThatThrownBy(() -> read.get(4, TimeUnit.SECONDS))
          .hasCauseInstanceOf(QuarantineReadFailure.class);
      assertThat(aborted.getCount()).isZero();
      assertThat(storage.storeQuarantined(KEY, new byte[] {1})).isEqualTo(REFERENCE);
    } finally {
      aborted.countDown();
    }
  }

  @Test
  void concurrencyLimitRejectsImmediatelyAndReleasesSlotAfterFailure() throws Exception {
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
        .thenAnswer(
            invocation -> {
              started.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new IllegalStateException("test timeout");
              throw SdkClientException.create("provider failure");
            });
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> storage.storeQuarantined(KEY, new byte[] {1}));
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      expectCode(Code.UNAVAILABLE, () -> storage.readQuarantined(REFERENCE, 3));
      release.countDown();
      assertThatThrownBy(() -> first.get(2, TimeUnit.SECONDS))
          .hasCauseInstanceOf(QuarantineReadFailure.class);
      doReturn(PutObjectResponse.builder().build())
          .when(client)
          .putObject(any(PutObjectRequest.class), any(RequestBody.class));
      assertThat(storage.storeQuarantined(KEY, new byte[] {1})).isEqualTo(REFERENCE);
    } finally {
      release.countDown();
    }
  }

  private void invalidStartup() {
    assertThatThrownBy(
            () -> new S3EvidenceObjectStorage(client, "private-evidence", Duration.ofSeconds(2), 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Private S3 evidence bucket validation failed")
        .hasNoCause();
  }

  private static void expectCode(Code code, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            QuarantineReadFailure.class, failure -> assertThat(failure.code()).isEqualTo(code));
  }

  private static String sha(byte[] bytes) throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  private static ResponseInputStream<GetObjectResponse> response(byte[] bytes) throws Exception {
    return stream(
        GetObjectResponse.builder()
            .contentLength((long) bytes.length)
            .metadata(Map.of("sha256", sha(bytes)))
            .build(),
        bytes);
  }

  private static ResponseInputStream<GetObjectResponse> stream(
      GetObjectResponse response, byte[] bytes) {
    return new ResponseInputStream<>(
        response, AbortableInputStream.create(new ByteArrayInputStream(bytes)));
  }
}
