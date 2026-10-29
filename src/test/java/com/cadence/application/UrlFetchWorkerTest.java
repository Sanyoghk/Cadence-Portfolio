package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class UrlFetchWorkerTest {
  @Autowired UrlFetchWorker worker;
  @MockitoSpyBean DurableJobQueue queue;
  @Autowired DurableJobRepository jobs;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired BrandRepository brands;
  @Autowired WorkspaceRepository workspaces;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactionManager;
  @MockitoBean UrlEvidenceFetcher fetcher;
  @MockitoBean EvidenceObjectStorage storage;
  BrandEvidence item;
  DurableJob job;

  @BeforeEach
  void prepare() {
    jobs.deleteAll();
    Workspace workspace = workspaces.save(new Workspace("Fetch test", "Creator"));
    Brand brand = brands.save(new Brand("Fetch brand", workspace));
    item =
        evidence.save(
            new BrandEvidence(
                brand,
                EvidenceType.URL,
                "https://example.com/",
                EvidenceAdmission.checksum("URL\nhttps://example.com/"),
                Instant.now()));
    job = enqueue(item, 3);
    when(fetcher.fetch(anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new UrlEvidenceFetcher.FetchedUrl("hello".getBytes(), "text/plain");
            });
    when(storage.storeQuarantined(anyString(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return "quarantine:" + invocation.getArgument(0);
            });
  }

  private DurableJob enqueue(BrandEvidence item, int attempts) {
    return queue.enqueue(
        "BRAND_EVIDENCE_INGEST",
        "evidence:" + item.getId(),
        UUID.randomUUID().toString(),
        attempts,
        Instant.now());
  }

  private BrandEvidence stored() {
    return evidence.findById(item.getId()).orElseThrow();
  }

  private DurableJob storedJob() {
    return jobs.findById(job.getId()).orElseThrow();
  }

  private DurableJob scanJob() {
    return jobs.findByIdempotencyKey(
            "evidence-scan:" + item.getId() + ":" + stored().getFetchedChecksum())
        .orElseThrow();
  }

  private void retryNow() {
    jdbc.update(
        "update jobs set next_attempt_at = ? where id = ?",
        Instant.now().minusSeconds(1),
        job.getId());
  }

  @Test
  void inheritedTransactionIsRejectedBeforeExternalIoOrJobClaim() {
    assertThatThrownBy(
            () -> new TransactionTemplate(transactionManager).execute(status -> worker.runOnce()))
        .isInstanceOf(IllegalTransactionStateException.class);
    verifyNoInteractions(fetcher, storage);
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(storedJob().getAttempts()).isZero();
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
  }

  @Test
  void atomicallyStoresProvenanceAndCompletesOnceIncludingDuplicateJobs() {
    assertThat(worker.runOnce()).isTrue();
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThat(stored().getFetchedChecksum()).hasSize(64);
    assertThat(stored().getChecksum())
        .isEqualTo(EvidenceAdmission.checksum("URL\nhttps://example.com/"));
    assertThat(stored().getFetchedAt()).isNotNull();
    assertThat(stored().getWebsiteMetadata().text()).isEqualTo("hello");
    assertThat(stored().getExtractedAt()).isNotNull();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(scanJob().getType()).isEqualTo("EVIDENCE_MALWARE_SCAN");
    assertThat(scanJob().getPayloadReference()).isEqualTo("evidence:" + item.getId());
    assertThat(scanJob().getStatus()).isEqualTo(JobStatus.PENDING);
    assertThat(worker.runOnce()).isFalse();
    DurableJob duplicate = enqueue(item, 3);
    worker.runOnce();
    assertThat(jobs.findById(duplicate.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.SUCCEEDED);
    assertThat(
            jobs.findAll().stream()
                .filter(candidate -> candidate.getType().equals("EVIDENCE_MALWARE_SCAN")))
        .hasSize(1);
    verify(fetcher, times(1)).fetch(anyString());
    verify(storage, times(1)).storeQuarantined(anyString(), any());
  }

  @Test
  void retriesTransientFailureWithoutMakingEvidenceTerminalAndThenSucceeds() {
    when(fetcher.fetch(anyString()))
        .thenThrow(new UrlFetchFailure(UrlFetchFailure.Code.TIMEOUT))
        .thenReturn(new UrlEvidenceFetcher.FetchedUrl("hello".getBytes(), "text/plain"));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(worker.runOnce()).isFalse();
    retryNow();
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(storedJob().getAttempts()).isEqualTo(2);
  }

  @Test
  void mismatchedStorageReferenceCannotCompleteFetch() {
    doReturn("quarantine:quarantine/url/another-tenant/source")
        .when(storage)
        .storeQuarantined(anyString(), any());
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(storedJob().getLastError()).isEqualTo("Evidence fetch or storage unavailable");
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(stored().getFetchedReference()).isNull();
    assertThat(stored().getWebsiteMetadata()).isNull();
  }

  @Test
  void scanEnqueueFailureCannotCommitFetchedEvidenceOrSuccessfulFetchJob() {
    doThrow(new IllegalStateException("enqueue fixture"))
        .when(queue)
        .enqueue(eq("EVIDENCE_MALWARE_SCAN"), anyString(), anyString(), anyInt(), any());
    assertThatThrownBy(worker::runOnce).isInstanceOf(IllegalStateException.class);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(stored().getFetchedReference()).isNull();
    assertThat(stored().getWebsiteMetadata()).isNull();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.RUNNING);
    assertThat(
            jobs.findAll().stream()
                .filter(candidate -> candidate.getType().equals("EVIDENCE_MALWARE_SCAN")))
        .isEmpty();
  }

  @Test
  void permanentlyRejectsUnsafeTargetsAndReturnsOnlySafeMessages() {
    when(fetcher.fetch(anyString()))
        .thenThrow(new UrlFetchFailure(UrlFetchFailure.Code.UNSAFE_TARGET));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getWarning()).isEqualTo("Source could not be fetched");
    assertThat(worker.runOnce()).isFalse();
    verifyNoInteractions(storage);
  }

  @Test
  void exhaustsBoundedRetriesAndRedactsUnexpectedProviderErrors() {
    when(fetcher.fetch(anyString()))
        .thenThrow(new IllegalStateException("password=secret at 10.0.0.1"));
    for (int i = 0; i < 3; i++) {
      retryNow();
      worker.runOnce();
    }
    assertThat(storedJob().getAttempts()).isEqualTo(3);
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(storedJob().getLastError()).isEqualTo("Evidence fetch or storage unavailable");
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    worker.runOnce();
    verify(fetcher, times(3)).fetch(anyString());
  }

  @Test
  void recoversFinalExpiredLeaseWithoutLeavingEvidencePending() {
    jobs.deleteAll();
    job = enqueue(item, 1);
    queue.claimNext("crashed", java.time.Duration.ofSeconds(1));
    jdbc.update(
        "update jobs set lease_expires_at = ? where id = ?",
        Instant.now().minusSeconds(1),
        job.getId());
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    verifyNoInteractions(fetcher);
  }

  @Test
  void rejectsStaleCompletionAndRecoversTheLease() {
    when(fetcher.fetch(anyString()))
        .thenAnswer(
            invocation -> {
              jdbc.update(
                  "update jobs set lease_expires_at = ? where id = ?",
                  Instant.now().minusSeconds(1),
                  job.getId());
              return new UrlEvidenceFetcher.FetchedUrl("old".getBytes(), "text/plain");
            });
    worker.runOnce();
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(stored().getWebsiteMetadata()).isNull();
    when(fetcher.fetch(anyString()))
        .thenReturn(new UrlEvidenceFetcher.FetchedUrl("new".getBytes(), "text/plain"));
    worker.runOnce();
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThat(storedJob().getAttempts()).isEqualTo(2);
    assertThat(stored().getWebsiteMetadata().text()).isEqualTo("new");
  }

  @Test
  void onlyOneConcurrentWorkerFetchesTheJob() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    when(fetcher.fetch(anyString()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Test release timed out");
              return new UrlEvidenceFetcher.FetchedUrl("hello".getBytes(), "text/plain");
            });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(worker::runOnce);
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(executor.submit(worker::runOnce).get(5, TimeUnit.SECONDS)).isFalse();
      } finally {
        release.countDown();
      }
      assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
    }
    verify(fetcher, times(1)).fetch(anyString());
  }

  @Test
  void ignoresTextAndFileJobs() {
    jobs.deleteAll();
    BrandEvidence text =
        evidence.save(
            new BrandEvidence(
                item.getBrand(), EvidenceType.TEXT, "hello", "b".repeat(64), Instant.now()));
    DurableJob textJob = enqueue(text, 3);
    assertThat(worker.runOnce()).isFalse();
    assertThat(jobs.findById(textJob.getId()).orElseThrow().getAttempts()).isZero();
    verifyNoInteractions(fetcher);
  }

  @Test
  void neverMarksJobSuccessfulIfEvidenceBecameFailedDuringFetch() {
    when(fetcher.fetch(anyString()))
        .thenAnswer(
            invocation -> {
              BrandEvidence concurrent = evidence.findById(item.getId()).orElseThrow();
              concurrent.failUrlFetch();
              evidence.saveAndFlush(concurrent);
              return new UrlEvidenceFetcher.FetchedUrl("hello".getBytes(), "text/plain");
            });
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getFetchedReference()).isNull();
  }

  @Test
  void storesBomDecodedMetadataWithVersionAndQueuesScanOfOriginalBytes() {
    byte[] body =
        "<title>Бренд</title><p>Привет 😀</p>".getBytes(java.nio.charset.StandardCharsets.UTF_16);
    when(fetcher.fetch(anyString()))
        .thenReturn(new UrlEvidenceFetcher.FetchedUrl(body, "text/html"));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThat(stored().getWebsiteMetadata().title()).isEqualTo("Бренд");
    assertThat(stored().getWebsiteMetadata().text()).isEqualTo("Привет 😀");
    assertThat(stored().getWebsiteMetadata().version()).isEqualTo("website-v2");
    assertThat(stored().getWebsiteMetadata().warnings()).contains("UTF16BE_BOM");
    assertThat(stored().getFetchedChecksum())
        .isEqualTo(java.util.HexFormat.of().formatHex(sha256(body)));
    assertThat(scanJob().getStatus()).isEqualTo(JobStatus.PENDING);
    verify(storage).storeQuarantined(anyString(), org.mockito.AdditionalMatchers.aryEq(body));
  }

  private byte[] sha256(byte[] body) {
    try {
      return java.security.MessageDigest.getInstance("SHA-256").digest(body);
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  @Test
  void extractionFailureIsPermanentAndCannotCommitPartialMetadata() {
    when(fetcher.fetch(anyString()))
        .thenReturn(new UrlEvidenceFetcher.FetchedUrl(new byte[] {(byte) 0xff}, "text/html"));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(storedJob().getLastError()).isEqualTo("Website metadata could not be extracted");
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getWebsiteMetadata()).isNull();
    assertThat(stored().getFetchedReference()).isNull();
    verifyNoInteractions(storage);
  }

  @Test
  void rejectsArchivedBrandsBeforeNetwork() {
    Brand brand = brands.findById(item.getBrand().getId()).orElseThrow();
    brand.archive();
    brands.save(brand);
    worker.runOnce();
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    verifyNoInteractions(fetcher);
  }
}
