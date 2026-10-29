package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.repositories.*;
import com.cadence.application.services.*;
import com.cadence.domain.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

public abstract class EvidenceArtifactWorkerContract {
  @Autowired com.cadence.application.services.EvidenceOwnershipLocks ownership;
  @Autowired protected EvidenceArtifactWorker worker;
  @Autowired protected EvidenceArtifactRepository artifacts;
  @Autowired protected EvidenceScanResultRepository scans;
  @Autowired protected DurableJobRepository jobRepository;
  @Autowired protected DurableJobQueue queue;
  @Autowired protected BrandEvidenceRepository evidence;
  @Autowired protected BrandRepository brands;
  @Autowired protected WorkspaceRepository workspaces;
  @Autowired protected QuarantineObjectRepository objects;
  @Autowired protected PlatformTransactionManager manager;
  @MockitoSpyBean protected Clock clock;
  @MockitoBean protected EvidenceRenderer renderer;
  @MockitoBean protected EvidenceObjectStorage storage;
  @MockitoBean protected EvidenceMalwareScanner scanner;
  protected Workspace workspace;
  protected Brand brand;
  protected BrandEvidence source;
  protected DurableJob extractionJob;
  protected EvidenceScanResult observation;
  protected byte[] sourceBytes;
  protected byte[] png;
  protected Map<String, byte[]> stored;

  @BeforeEach
  void prepareArtifacts() throws Exception {
    // Negative tests deliberately leave retryable, pending or reclaimed jobs. Close those fixture
    // jobs through real domain transitions so the global queue cannot consume an earlier test's
    // source instead of this test's source. Audit rows and published artifacts remain intact.
    new TransactionTemplate(manager)
        .executeWithoutResult(
            status -> {
              for (DurableJob job : jobRepository.findAll()) {
                if (!"EVIDENCE_DOCUMENT_EXTRACT".equals(job.getType())
                    || job.getStatus() == JobStatus.SUCCEEDED
                    || job.getStatus() == JobStatus.DEAD) continue;
                Instant closeAt = clock.instant();
                if (job.getStatus() == JobStatus.RUNNING) {
                  closeAt = job.getLeaseExpiresAt().plusSeconds(1);
                  job.recoverExpiredLease(closeAt);
                  if (job.getStatus() == JobStatus.DEAD) continue;
                }
                if (closeAt.isBefore(job.getNextAttemptAt())) closeAt = job.getNextAttemptAt();
                String owner = "fixture-cleanup-" + UUID.randomUUID();
                job.claim(owner, closeAt, Duration.ofMinutes(2));
                job.fail(
                    owner,
                    job.getAttempts(),
                    closeAt,
                    "Fixture completed",
                    new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(1)),
                    true);
              }
            });
    workspace = workspaces.saveAndFlush(new Workspace("Artifact studio", "Creator"));
    brand = brands.saveAndFlush(new Brand("Artifact brand", workspace));
    sourceBytes = "%PDF-1.7 artifact fixture".getBytes(StandardCharsets.UTF_8);
    stored = new ConcurrentHashMap<>();
    var buffer = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB), "png", buffer);
    png = buffer.toByteArray();
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new EvidenceRenderer.DocumentText(
                  "Document text\nКириллица 😀", "isolated-v1");
            });
    when(renderer.screenshot(any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new EvidenceRenderer.Screenshot(png, 1280, 720, "isolated-v1");
            });
    when(storage.readQuarantined(anyString(), anyInt()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return sourceBytes.clone();
            });
    when(storage.storeQuarantined(anyString(), any()))
        .thenAnswer(
            call -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              String reference = "quarantine:" + call.getArgument(0);
              stored.put(reference, ((byte[]) call.getArgument(1)).clone());
              return reference;
            });
  }

  protected void prepareSource(boolean html, ScanVerdict verdict) {
    if (html)
      sourceBytes =
          "<!doctype html><title>Safe fixture</title><p>Body</p>".getBytes(StandardCharsets.UTF_8);
    source =
        new TransactionTemplate(manager)
            .execute(
                status -> {
                  Instant now = clock.instant();
                  BrandEvidence item =
                      html
                          ? evidence.saveAndFlush(
                              new BrandEvidence(
                                  brand,
                                  EvidenceType.URL,
                                  "https://example.com/",
                                  "a".repeat(64),
                                  now))
                          : evidence.saveAndFlush(
                              BrandEvidence.quarantinedFile(
                                  brand,
                                  "quarantine:quarantine/"
                                      + brand.getId()
                                      + "/"
                                      + checksum(sourceBytes)
                                      + ".pdf",
                                  checksum(sourceBytes),
                                  now,
                                  "source.pdf",
                                  "application/pdf"));
                  if (html) {
                    item.recordFetched(
                        "quarantine:quarantine/url/" + item.getId() + "/" + checksum(sourceBytes),
                        checksum(sourceBytes),
                        "text/html",
                        now);
                    evidence.flush();
                  }
                  return item;
                });
    observation = appendScan(verdict);
    extractionJob =
        queue.enqueue(
            "EVIDENCE_DOCUMENT_EXTRACT",
            "evidence:" + source.getId(),
            "artifact:" + source.getId(),
            3,
            clock.instant());
  }

  protected EvidenceScanResult appendScan(ScanVerdict verdict) {
    return new TransactionTemplate(manager)
        .execute(
            status -> {
              BrandEvidence item = ownership.findLocked(source.getId()).orElseThrow();
              Instant now = clock.instant();
              DurableJob job =
                  queue.enqueue(
                      "EVIDENCE_MALWARE_SCAN",
                      "evidence:" + item.getId(),
                      "scan-fixture:" + UUID.randomUUID(),
                      3,
                      now);
              String owner = UUID.randomUUID().toString();
              job.claim(owner, now, Duration.ofMinutes(2));
              jobRepository.flush();
              EvidenceScanResult scan =
                  scans.saveAndFlush(
                      new EvidenceScanResult(
                          item,
                          job,
                          EvidenceArtifact.sourceChecksum(item),
                          now,
                          verdict,
                          "test-engine",
                          "test-signatures",
                          now));
              job.succeed(owner, job.getAttempts(), now);
              return scan;
            });
  }

  protected List<EvidenceArtifact> sourceArtifacts() {
    return artifacts.findAll().stream()
        .filter(a -> a.getEvidenceId().equals(source.getId()))
        .toList();
  }

  @Test
  void scannedPdfProducesBoundedAppendOnlyTextWithExactProvenance() {
    prepareSource(false, ScanVerdict.CLEAN);
    assertThat(worker.runOnce()).isTrue();
    assertThat(sourceArtifacts())
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getKind()).isEqualTo(EvidenceArtifact.Kind.PDF_TEXT);
              assertThat(artifact.getScanResultId()).isEqualTo(observation.getId());
              assertThat(artifact.getSourceChecksum()).isEqualTo(checksum(sourceBytes));
              assertThat(artifact.getExtractedText()).isEqualTo("Document text\nКириллица 😀");
              assertThat(artifact.getOutputChecksum())
                  .isEqualTo(
                      checksum(artifact.getExtractedText().getBytes(StandardCharsets.UTF_8)));
              assertThat(artifact.getStorageReference()).isNull();
            });
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.SUCCEEDED);
    verify(storage, never()).storeQuarantined(anyString(), any());
    assertThat(worker.runOnce()).isFalse();
    verify(renderer).extractPdf(any());
  }

  @Test
  void scannedHtmlScreenshotOwnsAnIndependentPrivateQuotaGeneration() {
    prepareSource(true, ScanVerdict.CLEAN);
    assertThat(worker.runOnce()).isTrue();
    assertThat(sourceArtifacts())
        .singleElement()
        .satisfies(
            artifact -> {
              assertThat(artifact.getKind()).isEqualTo(EvidenceArtifact.Kind.SCREENSHOT);
              assertThat(artifact.getStorageReference())
                  .startsWith("quarantine:quarantine/writes/" + workspace.getId());
              assertThat(stored.get(artifact.getStorageReference())).isEqualTo(png);
              assertThat(artifact.getOutputChecksum()).isEqualTo(checksum(png));
              assertThat(artifact.getPixelWidth()).isEqualTo(1280);
              assertThat(artifact.getPixelHeight()).isEqualTo(720);
            });
    assertThat(objects.retainedBytes(workspace.getId())).isEqualTo(png.length);
    assertThat(objects.findAll().stream().filter(q -> q.getWorkspaceId().equals(workspace.getId())))
        .singleElement()
        .satisfies(q -> assertThat(q.getState()).isEqualTo(QuarantineObject.State.REFERENCED));
    verify(renderer).screenshot(any());
  }

  @Test
  void infectedOrUnavailableScanCannotReachStorageOrRenderer() {
    prepareSource(false, ScanVerdict.INFECTED);
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.DEAD);
    verifyNoInteractions(renderer, storage);
  }

  @Test
  void staleCleanObservationCannotAuthorizeNewRendering() {
    prepareSource(false, ScanVerdict.CLEAN);
    Instant afterFreshnessWindow = clock.instant().plus(Duration.ofDays(2));
    doReturn(afterFreshnessWindow).when(clock).instant();
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.DEAD);
    verifyNoInteractions(renderer, storage);
  }

  @Test
  void mismatchedStoredSourceIsRejectedBeforeRendererInvocation() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(storage.readQuarantined(anyString(), anyInt())).thenReturn(new byte[] {1, 2, 3});
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.DEAD);
    verifyNoInteractions(renderer);
  }

  @Test
  void rendererSourceMutationAndControlCharactersFailClosed() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              ((byte[]) call.getArgument(0))[0] = 0;
              return new EvidenceRenderer.DocumentText("replacement", "isolated-v1");
            });
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.FAILED);
    assertThat(sourceBytes[0]).isEqualTo((byte) '%');
  }

  @Test
  void rendererFailureIsSanitizedAndPreservesRetryableJob() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenThrow(new IllegalStateException("secret private endpoint"));
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    DurableJob failed = jobRepository.findById(extractionJob.getId()).orElseThrow();
    assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
    assertThat(failed.getLastError()).isEqualTo("Evidence rendering unavailable");
    assertThat(failed.getLastError()).doesNotContain("secret");
  }

  @Test
  void archiveOrNewScanDuringRenderingPreventsPublication() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              appendScan(ScanVerdict.ERROR);
              return new EvidenceRenderer.DocumentText("old result", "isolated-v1");
            });
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.DEAD);
  }

  @Test
  void sourceArchiveDuringRenderingPreventsPublication() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              new TransactionTemplate(manager)
                  .executeWithoutResult(
                      status -> brands.findById(brand.getId()).orElseThrow().archive());
              return new EvidenceRenderer.DocumentText("archived result", "isolated-v1");
            });
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.DEAD);
  }

  @Test
  void forgedScreenshotHeaderAndMismatchedStorageReferenceLeaveOnlyOrphanReservation() {
    prepareSource(true, ScanVerdict.CLEAN);
    byte[] forged = png.clone();
    java.nio.ByteBuffer.wrap(forged, 16, 4).putInt(1_000_000);
    when(renderer.screenshot(any()))
        .thenReturn(new EvidenceRenderer.Screenshot(forged, 1280, 720, "isolated-v1"));
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    verify(storage, never()).storeQuarantined(anyString(), any());
    assertThat(objects.retainedBytes(workspace.getId()))
        .isEqualTo(EvidenceRenderer.MAX_SCREENSHOT_BYTES);
  }

  @Test
  void wrongStorageGenerationCannotBecomeAnArtifact() {
    prepareSource(true, ScanVerdict.CLEAN);
    doReturn("quarantine:quarantine/writes/foreign/key.png")
        .when(storage)
        .storeQuarantined(anyString(), any());
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(objects.retainedBytes(workspace.getId()))
        .isEqualTo(EvidenceRenderer.MAX_SCREENSHOT_BYTES);
  }

  @Test
  void expiredAndReclaimedLeaseCannotCommitTheOldRendererResult() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              new TransactionTemplate(manager)
                  .executeWithoutResult(
                      status -> {
                        DurableJob job =
                            jobRepository.findLockedById(extractionJob.getId()).orElseThrow();
                        Instant afterExpiry = job.getLeaseExpiresAt().plusSeconds(1);
                        job.recoverExpiredLease(afterExpiry);
                        job.claim("replacement-worker", afterExpiry, Duration.ofMinutes(2));
                      });
              return new EvidenceRenderer.DocumentText("expired worker result", "isolated-v1");
            });
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    DurableJob job = jobRepository.findById(extractionJob.getId()).orElseThrow();
    assertThat(job.getLeaseOwner()).isEqualTo("replacement-worker");
    assertThat(job.getAttempts()).isEqualTo(2);
  }

  @Test
  void rendererTextWithControlCharactersCannotBecomeAnArtifact() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenReturn(new EvidenceRenderer.DocumentText("text\u0001forged", "isolated-v1"));
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.FAILED);
  }

  @Test
  void oversizedRendererTextFailsAtTheAdapterContractBoundary() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenAnswer(call -> new EvidenceRenderer.DocumentText("a".repeat(16001), "isolated-v1"));
    worker.runOnce();
    assertThat(sourceArtifacts()).isEmpty();
    assertThat(jobRepository.findById(extractionJob.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.FAILED);
  }

  @Test
  void emptyPdfTextIsAValidBoundedResultForAnImageOnlyDocument() {
    prepareSource(false, ScanVerdict.CLEAN);
    when(renderer.extractPdf(any()))
        .thenReturn(new EvidenceRenderer.DocumentText("", "isolated-v1"));
    worker.runOnce();
    assertThat(sourceArtifacts())
        .singleElement()
        .satisfies(a -> assertThat(a.getExtractedText()).isEmpty());
  }

  @Test
  void inheritedTransactionIsRejectedBeforeAnyIo() {
    prepareSource(false, ScanVerdict.CLEAN);
    assertThatThrownBy(() -> new TransactionTemplate(manager).execute(status -> worker.runOnce()))
        .isInstanceOf(IllegalTransactionStateException.class);
    verifyNoInteractions(renderer, storage);
  }

  @Test
  void concurrentWorkersRenderAndCommitOnlyOneArtifact() throws Exception {
    prepareSource(false, ScanVerdict.CLEAN);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(renderer.extractPdf(any()))
        .thenAnswer(
            call -> {
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return new EvidenceRenderer.DocumentText("one result", "isolated-v1");
            });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> worker.runOnce());
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      var b = executor.submit(() -> worker.runOnce());
      assertThat(b.get(10, TimeUnit.SECONDS)).isFalse();
      release.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(sourceArtifacts()).hasSize(1);
    verify(renderer).extractPdf(any());
  }

  @Test
  void duplicateJobsPublishOneArtifactAndRetainTheLosingScreenshotAsAnOrphan() throws Exception {
    prepareSource(true, ScanVerdict.CLEAN);
    DurableJob duplicate =
        queue.enqueue(
            "EVIDENCE_DOCUMENT_EXTRACT",
            "evidence:" + source.getId(),
            "duplicate-artifact:" + source.getId(),
            3,
            clock.instant());
    var rendered = new CountDownLatch(2);
    var firstRendered = new CountDownLatch(1);
    when(renderer.screenshot(any()))
        .thenAnswer(
            call -> {
              rendered.countDown();
              firstRendered.countDown();
              assertThat(rendered.await(10, TimeUnit.SECONDS)).isTrue();
              return new EvidenceRenderer.Screenshot(png, 1280, 720, "isolated-v1");
            });
    try (var executor = Executors.newFixedThreadPool(2)) {
      var a = executor.submit(() -> worker.runOnce());
      assertThat(firstRendered.await(10, TimeUnit.SECONDS)).isTrue();
      var b = executor.submit(() -> worker.runOnce());
      assertThat(a.get(20, TimeUnit.SECONDS)).isTrue();
      assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
    }
    assertThat(sourceArtifacts()).hasSize(1);
    var generations =
        objects.findAll().stream()
            .filter(q -> q.getWorkspaceId().equals(workspace.getId()))
            .toList();
    assertThat(generations).hasSize(2);
    assertThat(generations.stream().map(QuarantineObject::getState))
        .containsExactlyInAnyOrder(
            QuarantineObject.State.REFERENCED, QuarantineObject.State.RESERVED);
    assertThat(jobRepository.findById(duplicate.getId()).orElseThrow().getStatus())
        .isEqualTo(JobStatus.SUCCEEDED);
    assertThat(stored).hasSize(2);
  }

  protected static String checksum(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception failure) {
      throw new IllegalStateException(failure);
    }
  }
}
