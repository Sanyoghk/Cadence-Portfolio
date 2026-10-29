package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Same worker behavior is exercised on H2 locally and real PostgreSQL in mandatory CI. */
public abstract class FileScanWorkerContract {
  @Test
  void rejectedUrlWithMetadataDoesNotPoisonTheNextScanJob() throws Exception {
    BrandEvidence followingFile = item;
    useFetchedUrlFixture();
    when(scanner.scan(any()))
        .thenReturn(
            new MalwareScanReport(MalwareScanResult.INFECTED, "test-engine", "test-signatures"));
    assertThat(worker.runOnce()).isTrue();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getWebsiteMetadata()).isNotNull();
    assertThat(observations()).hasSize(1);
    var followingJob =
        queue.enqueue(
            "BRAND_EVIDENCE_INGEST",
            "evidence:" + followingFile.getId(),
            "following:" + followingFile.getId(),
            3,
            Instant.now());
    when(scanner.scan(any()))
        .thenReturn(
            new MalwareScanReport(MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    assertThat(worker.runOnce()).isTrue();
    assertThat(jobs.findById(followingJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.SUCCEEDED);
    assertThat(worker.runOnce()).isFalse();
  }

  @Autowired EvidenceMalwareScanWorker worker;
  @Autowired DurableJobQueue queue;
  @Autowired DurableJobRepository jobs;
  @Autowired BrandEvidenceRepository evidence;
  @Autowired EvidenceScanResultRepository results;
  @Autowired BrandRepository brands;
  @Autowired WorkspaceRepository workspaces;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager manager;
  @MockitoBean EvidenceMalwareScanner scanner;
  @MockitoBean EvidenceObjectStorage storage;
  BrandEvidence item;
  DurableJob job;
  byte[] bytes = {1, 2, 3};

  @BeforeEach
  void prepare() throws Exception {
    results.deleteAll();
    jobs.deleteAll();
    Workspace workspace = workspaces.save(new Workspace("Scan workspace", "Creator"));
    Brand brand = brands.save(new Brand("Scan brand", workspace));
    String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    item =
        evidence.saveAndFlush(
            BrandEvidence.quarantinedFile(
                brand,
                "quarantine:quarantine/file.pdf",
                checksum,
                Instant.now().minusSeconds(2),
                "file.pdf",
                "application/pdf"));
    job =
        queue.enqueue(
            "BRAND_EVIDENCE_INGEST",
            "evidence:" + item.getId(),
            "scan:" + item.getId(),
            3,
            Instant.now());
    when(storage.readQuarantined(anyString(), anyInt()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return bytes;
            });
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
  }

  @AfterEach
  void cleanScanAudit() {
    results.deleteAll();
    jobs.deleteAll();
  }

  DurableJob storedJob() {
    return jobs.findById(job.getId()).orElseThrow();
  }

  BrandEvidence stored() {
    return evidence.findById(item.getId()).orElseThrow();
  }

  List<EvidenceScanResult> observations() {
    return results.findByJobIdOrderByAttemptNumber(job.getId(), PageRequest.of(0, 10));
  }

  void useFetchedUrlFixture() throws Exception {
    results.deleteAll();
    jobs.deleteAll();
    String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    item =
        evidence.saveAndFlush(
            new BrandEvidence(
                item.getBrand(),
                EvidenceType.URL,
                "https://example.com/",
                "f".repeat(64),
                Instant.now().minusSeconds(5)));
    item.recordFetched("quarantine:quarantine/url/source", checksum, "text/plain", Instant.now());
    item.recordWebsiteMetadata(
        new WebsiteMetadata("Source", "Description", "Extracted text", "", "website-v2"),
        Instant.now());
    evidence.saveAndFlush(item);
    job =
        queue.enqueue(
            "EVIDENCE_MALWARE_SCAN",
            "evidence:" + item.getId(),
            "url-scan:" + item.getId(),
            3,
            Instant.now());
  }

  void retryNow() {
    jdbc.update(
        "update jobs set next_attempt_at = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        job.getId());
  }

  void expireLease() {
    jdbc.update(
        "update jobs set lease_expires_at = ? where id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
        job.getId());
  }

  @Test
  void cleanCompletesExistingJobAndPersistsBoundProvenanceWithoutApproval() {
    assertThat(worker.runOnce()).isTrue();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(observations())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.getChecksum()).isEqualTo(item.getChecksum());
              assertThat(result.getAttemptNumber()).isEqualTo(1);
              assertThat(result.getVerdict()).isEqualTo(ScanVerdict.CLEAN);
              assertThat(result.getScannerEngine()).isEqualTo("test-engine");
              assertThat(result.getSignatureVersion()).isEqualTo("test-signatures");
              assertThat(result.getLeaseOwner()).isNotBlank();
              assertThat(result.getSourceVersion()).isEqualTo(stored().getLockVersion());
              assertThat(result.getScannedAt()).isAfterOrEqualTo(item.getCapturedAt());
            });
    assertThat(worker.runOnce()).isFalse();
    verify(scanner, times(1)).scan(any());
  }

  @Test
  void cleanFetchedUrlUsesFetchedBytesAndPersistsBoundProvenance() throws Exception {
    useFetchedUrlFixture();
    assertThat(worker.runOnce()).isTrue();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThat(observations())
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.getChecksum()).isEqualTo(item.getFetchedChecksum());
              assertThat(result.getSourceVersion()).isEqualTo(stored().getLockVersion());
              assertThat(result.getVerdict()).isEqualTo(ScanVerdict.CLEAN);
              assertThat(result.getScannerEngine()).isEqualTo("test-engine");
              assertThat(result.getSignatureVersion()).isEqualTo("test-signatures");
              assertThat(result.getScannedAt()).isAfterOrEqualTo(item.getFetchedAt());
            });
    verify(storage)
        .readQuarantined(item.getFetchedReference(), EvidenceObjectStorage.MAX_QUARANTINE_BYTES);
  }

  @Test
  void infectedFetchedUrlIsTerminalAndRemainsQuarantined() throws Exception {
    useFetchedUrlFixture();
    doReturn(new MalwareScanReport(MalwareScanResult.INFECTED, "test-engine", "test-signatures"))
        .when(scanner)
        .scan(any());
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getFetchedReference()).startsWith("quarantine:");
    assertThat(observations())
        .extracting(EvidenceScanResult::getVerdict)
        .containsExactly(ScanVerdict.INFECTED);
  }

  @Test
  void infectionIsTerminalAndRetainsQuarantine() {
    doReturn(new MalwareScanReport(MalwareScanResult.INFECTED, "test-engine", "test-signatures"))
        .when(scanner)
        .scan(any());
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(stored().getSourceReference()).startsWith("quarantine:");
    assertThat(observations())
        .extracting(EvidenceScanResult::getVerdict)
        .containsExactly(ScanVerdict.INFECTED);
    assertThat(worker.runOnce()).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = MalwareScanResult.class,
      names = {"UNAVAILABLE", "ERROR"})
  void retriesScannerFailuresAndPreservesEveryObservedAttempt(MalwareScanResult verdict) {
    when(scanner.scan(any()))
        .thenReturn(new MalwareScanReport(verdict, "test-engine", "test-signatures"));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.PENDING);
    assertThat(worker.runOnce()).isFalse();
    when(scanner.scan(any()))
        .thenReturn(
            new MalwareScanReport(MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    retryNow();
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(observations())
        .extracting(EvidenceScanResult::getAttemptNumber)
        .containsExactly(1, 2);
    assertThat(observations())
        .extracting(EvidenceScanResult::getVerdict)
        .containsExactly(ScanVerdict.valueOf(verdict.name()), ScanVerdict.CLEAN);
  }

  @Test
  void boundsRetriesAndDoesNotStoreRawProviderErrors() {
    when(scanner.scan(any())).thenThrow(new IllegalStateException("token=private host=10.0.0.1"));
    for (int i = 0; i < 3; i++) {
      retryNow();
      worker.runOnce();
    }
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(storedJob().getAttempts()).isEqualTo(3);
    assertThat(storedJob().getLastError()).isEqualTo("Evidence scanning unavailable");
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(observations()).hasSize(3);
    assertThat(observations())
        .allSatisfy(
            result -> {
              assertThat(result.getScannerEngine()).isEqualTo("scanner-adapter");
              assertThat(result.getSignatureVersion()).isEqualTo("unavailable");
            });
    assertThat(worker.runOnce()).isFalse();
  }

  @Test
  void readFailureIsNotInventedAsAScanAndChecksumMismatchIsTerminal() {
    when(storage.readQuarantined(anyString(), anyInt())).thenReturn(new byte[] {9});
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(observations()).isEmpty();
    verifyNoInteractions(scanner);
  }

  @Test
  void storageOutageRetriesWithoutFabricatingProvenance() {
    when(storage.readQuarantined(anyString(), anyInt()))
        .thenThrow(new QuarantineReadFailure(QuarantineReadFailure.Code.UNAVAILABLE));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(observations()).isEmpty();
    verifyNoInteractions(scanner);
  }

  @Test
  void expiredResultCannotCommitAndNewAttemptCanRecover() {
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              expireLease();
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    worker.runOnce();
    assertThat(observations()).isEmpty();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.RUNNING);
    when(scanner.scan(any()))
        .thenReturn(
            new MalwareScanReport(MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.SUCCEEDED);
    assertThat(observations()).extracting(EvidenceScanResult::getAttemptNumber).containsExactly(2);
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
  void finalExpiredLeaseFailsWithoutFakeScan(boolean recoveredByQueue) {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              var running = jobs.findLockedById(job.getId()).orElseThrow();
              for (int n = 0; n < 3; n++) {
                Instant time = Instant.now().minusSeconds(20 - n * 3);
                running.claim(
                    "crashed",
                    time.isBefore(running.getNextAttemptAt()) ? running.getNextAttemptAt() : time,
                    Duration.ofMillis(1));
                if (n < 2) running.recoverExpiredLease(running.getLeaseExpiresAt());
              }
            });
    expireLease();
    if (recoveredByQueue) queue.claimNext("recovery", Duration.ofSeconds(1));
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(observations()).isEmpty();
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void lateSuccessCannotOverwriteANewerInfectedAttempt() {
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              expireLease();
              doReturn(
                      new MalwareScanReport(
                          MalwareScanResult.INFECTED, "test-engine", "test-signatures"))
                  .when(scanner)
                  .scan(any());
              worker.runOnce();
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(storedJob().getAttempts()).isEqualTo(2);
    assertThat(stored().getStatus()).isEqualTo(EvidenceStatus.FAILED);
    assertThat(observations())
        .extracting(EvidenceScanResult::getVerdict)
        .containsExactly(ScanVerdict.INFECTED);
  }

  @Test
  void archivedWorkspaceIsRejectedBeforeIoAndOnCompletion() {
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              Workspace workspace =
                  workspaces.findById(item.getBrand().getWorkspace().getId()).orElseThrow();
              workspace.archive();
              workspaces.saveAndFlush(workspace);
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(observations()).isEmpty();
  }

  @Test
  void archivedBrandIsRejectedBeforeIo() {
    Brand brand = brands.findById(item.getBrand().getId()).orElseThrow();
    brand.archive();
    brands.saveAndFlush(brand);
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void changedEvidenceVersionRejectsReceipt() {
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              jdbc.update(
                  "update brand_evidence set lock_version = lock_version + 1 where id = ?",
                  item.getId());
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    worker.runOnce();
    assertThat(storedJob().getStatus()).isEqualTo(JobStatus.DEAD);
    assertThat(observations()).isEmpty();
  }

  @Test
  void onlyOneWorkerScansConcurrentClaim() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              if (!release.await(5, TimeUnit.SECONDS))
                throw new AssertionError("Worker release timed out");
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
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
    assertThat(observations()).hasSize(1);
    verify(scanner, times(1)).scan(any());
  }

  @Test
  void inheritedTransactionIsRejectedBeforeClaim() {
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status ->
                assertThatThrownBy(worker::runOnce)
                    .isInstanceOf(IllegalTransactionStateException.class));
    assertThat(storedJob().getAttempts()).isZero();
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void unsupportedOrOrphanJobsAreNotClaimed() {
    jobs.deleteAll();
    queue.enqueue("OTHER", "evidence:" + item.getId(), "other", 3, Instant.now());
    queue.enqueue(
        "BRAND_EVIDENCE_INGEST", "evidence:" + UUID.randomUUID(), "orphan", 3, Instant.now());
    BrandEvidence text =
        evidence.save(
            new BrandEvidence(
                item.getBrand(), EvidenceType.TEXT, "text", "b".repeat(64), Instant.now()));
    queue.enqueue("BRAND_EVIDENCE_INGEST", "evidence:" + text.getId(), "text", 3, Instant.now());
    assertThat(worker.runOnce()).isFalse();
    verifyNoInteractions(storage, scanner);
  }
}
