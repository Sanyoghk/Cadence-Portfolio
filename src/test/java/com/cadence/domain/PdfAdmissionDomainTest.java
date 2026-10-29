package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PdfAdmissionDomainTest {
  private final Instant now = Instant.parse("2026-10-14T12:00:00Z");

  private BrandEvidence source() {
    var ws = mock(Workspace.class);
    when(ws.getStatus()).thenReturn(WorkspaceStatus.ACTIVE);
    var b = mock(Brand.class);
    when(b.getStatus()).thenReturn(BrandStatus.ACTIVE);
    when(b.getWorkspace()).thenReturn(ws);
    var e = mock(BrandEvidence.class);
    when(e.getBrand()).thenReturn(b);
    when(e.getType()).thenReturn(EvidenceType.FILE);
    when(e.getMediaType()).thenReturn("application/pdf");
    when(e.getStatus()).thenReturn(EvidenceStatus.PENDING);
    when(e.getCapturedAt()).thenReturn(now.minusSeconds(60));
    when(e.getChecksum()).thenReturn("0".repeat(64));
    return e;
  }

  private PdfReviewContent content(UUID latest) {
    return new PdfReviewContent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        latest,
        "0".repeat(64),
        EvidenceAdmission.checksum("Reviewed text"),
        "Reviewed text",
        "isolated-v1",
        now.plusSeconds(86400),
        20);
  }

  @Test
  void consentBindsImmutableScanAndCannotBeExtendedOrReusedAtExpiry() {
    var c = content(UUID.randomUUID());
    var a = new EvidenceAdmission(source(), UUID.randomUUID(), now, c);
    a.requireCurrentPdf(c, 0, 0, 0, now.plusSeconds(1));
    assertThat(a.getPolicyVersion()).isEqualTo("pdf-text-review-v1");
    assertThatThrownBy(() -> a.requireCurrentPdf(c, 0, 0, 0, c.scanExpiresAt()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> a.requireCurrentPdf(content(UUID.randomUUID()), 0, 0, 0, now))
        .isInstanceOf(IllegalStateException.class);
    var extended =
        new PdfReviewContent(
            c.artifactId(),
            c.originalScanId(),
            c.latestScanId(),
            c.sourceChecksum(),
            c.contentChecksum(),
            c.text(),
            c.rendererVersion(),
            c.scanExpiresAt().plusSeconds(1),
            c.sourceBytes());
    assertThatThrownBy(() -> a.requireCurrentPdf(extended, 0, 0, 0, now))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidReviewerAndExpiredConsentCannotCreateReceipt() {
    var c = content(UUID.randomUUID());
    var e = source();
    assertThatThrownBy(() -> new EvidenceAdmission(e, null, now, c))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmission(e, UUID.randomUUID(), c.scanExpiresAt(), c))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmission(e, UUID.randomUUID(), now.minusSeconds(61), c))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
