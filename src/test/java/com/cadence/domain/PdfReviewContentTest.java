package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class PdfReviewContentTest {
  private final Instant now = Instant.parse("2026-10-13T12:00:00Z");
  private BrandEvidence e;
  private EvidenceArtifact a;
  private EvidenceScanResult scan;
  private QuarantineObject q;
  private byte[] bytes;
  private String text;

  @BeforeEach
  void fixture() {
    UUID ws = UUID.randomUUID(),
        brand = UUID.randomUUID(),
        id = UUID.randomUUID(),
        object = UUID.randomUUID();
    var w = mock(Workspace.class);
    when(w.getId()).thenReturn(ws);
    when(w.getStatus()).thenReturn(WorkspaceStatus.ACTIVE);
    var b = mock(Brand.class);
    when(b.getId()).thenReturn(brand);
    when(b.getWorkspace()).thenReturn(w);
    when(b.getStatus()).thenReturn(BrandStatus.ACTIVE);
    bytes = "%PDF-1.7 source".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String checksum =
        EvidenceAdmission.checksum(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    text = "Reviewed 😀 text <script> is untrusted data";
    e = mock(BrandEvidence.class);
    when(e.getId()).thenReturn(id);
    when(e.getBrand()).thenReturn(b);
    when(e.getType()).thenReturn(EvidenceType.FILE);
    when(e.getStatus()).thenReturn(EvidenceStatus.PENDING);
    when(e.getMediaType()).thenReturn("application/pdf");
    when(e.getChecksum()).thenReturn(checksum);
    String key = "quarantine/writes/" + ws + "/" + object + ".pdf";
    when(e.getSourceReference()).thenReturn("quarantine:" + key);
    scan = mock(EvidenceScanResult.class);
    when(scan.getId()).thenReturn(UUID.randomUUID());
    when(scan.getEvidenceId()).thenReturn(id);
    when(scan.getVerdict()).thenReturn(ScanVerdict.CLEAN);
    when(scan.getChecksum()).thenReturn(checksum);
    when(scan.getScannedAt()).thenReturn(now.minusSeconds(10));
    when(scan.getScannerEngine()).thenReturn("test-engine");
    when(scan.getSignatureVersion()).thenReturn("test-signatures");
    a = mock(EvidenceArtifact.class);
    when(a.getId()).thenReturn(UUID.randomUUID());
    when(a.getEvidenceId()).thenReturn(id);
    when(a.getKind()).thenReturn(EvidenceArtifact.Kind.PDF_TEXT);
    when(a.getSourceChecksum()).thenReturn(checksum);
    when(a.getScanResult()).thenReturn(scan);
    when(a.getProducedAt()).thenReturn(now.minusSeconds(5));
    when(a.getExtractedText()).thenReturn(text);
    when(a.getOutputChecksum()).thenReturn(EvidenceAdmission.checksum(text));
    when(a.getRendererVersion()).thenReturn("renderer-v1");
    var job = mock(DurableJob.class);
    when(job.getStatus()).thenReturn(JobStatus.SUCCEEDED);
    when(a.getJob()).thenReturn(job);
    q = mock(QuarantineObject.class);
    when(q.getId()).thenReturn(object);
    when(q.getWorkspaceId()).thenReturn(ws);
    when(q.getBrandId()).thenReturn(brand);
    when(q.getEvidenceId()).thenReturn(id);
    when(q.reference()).thenReturn("quarantine:" + key);
    when(q.getObjectKey()).thenReturn(key);
    when(q.getRetainedBytes()).thenReturn((long) bytes.length);
    when(q.getState()).thenReturn(QuarantineObject.State.REFERENCED);
  }

  private PdfReviewContent content() {
    return PdfReviewContent.from(e, a, scan, q, now, Duration.ofHours(24));
  }

  @Test
  void exactTextAndPhysicalBytesPreserveValidatedProvenance() {
    var result = content();
    result.verifyBytes(bytes);
    assertThat(result.text()).isEqualTo(text);
    assertThat(result.contentChecksum()).isEqualTo(EvidenceAdmission.checksum(text));
    assertThat(result.originalScanId()).isEqualTo(scan.getId());
  }

  @Test
  void tombstoneQuotaReleaseWrongScopeAndKeyRejected() {
    when(q.getState()).thenReturn(QuarantineObject.State.TOMBSTONED);
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    when(q.getState()).thenReturn(QuarantineObject.State.REFERENCED);
    when(q.isQuotaReleased()).thenReturn(true);
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    when(q.isQuotaReleased()).thenReturn(false);
    when(q.getWorkspaceId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void foreignArtifactAndScanRejected() {
    when(a.getEvidenceId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    UUID evidenceId = e.getId();
    when(a.getEvidenceId()).thenReturn(evidenceId);
    when(scan.getEvidenceId()).thenReturn(UUID.randomUUID());
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void staleFutureAndNonCleanScansFailClosed() {
    when(scan.getScannedAt()).thenReturn(now.minus(Duration.ofDays(2)));
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    when(scan.getScannedAt()).thenReturn(now.plusSeconds(1));
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    when(scan.getScannedAt()).thenReturn(now.minusSeconds(10));
    when(scan.getVerdict()).thenReturn(ScanVerdict.INFECTED);
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void blankMalformedOversizedOrWrongDigestTextRejected() {
    for (var invalid : List.of(" ", "\uD800", "a\u0001", "x".repeat(16001))) {
      when(a.getExtractedText()).thenReturn(invalid);
      when(a.getOutputChecksum()).thenReturn(EvidenceAdmission.checksum(invalid));
      assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
    }
    when(a.getExtractedText()).thenReturn(text);
    when(a.getOutputChecksum()).thenReturn("0".repeat(64));
    assertThatThrownBy(this::content).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void exactExpiryAndInvalidPolicyRejected() {
    assertThatThrownBy(
            () ->
                PdfReviewContent.from(
                    e, a, scan, q, scan.getScannedAt().plusSeconds(86400), Duration.ofHours(24)))
        .isInstanceOf(IllegalStateException.class);
    for (var invalid : List.of(Duration.ZERO, Duration.ofDays(8)))
      assertThatThrownBy(() -> PdfReviewContent.from(e, a, scan, q, now, invalid))
          .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void incompleteCorruptOrOversizedBytesNeverVerify() {
    var c = content();
    assertThatThrownBy(() -> c.verifyBytes(null)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.verifyBytes(Arrays.copyOf(bytes, bytes.length - 1)))
        .isInstanceOf(IllegalStateException.class);
    var bad = bytes.clone();
    bad[0] = '!';
    assertThatThrownBy(() -> c.verifyBytes(bad)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> c.verifyBytes(new byte[PdfReviewContent.MAX_SOURCE_BYTES + 1]))
        .isInstanceOf(IllegalStateException.class);
  }
}
