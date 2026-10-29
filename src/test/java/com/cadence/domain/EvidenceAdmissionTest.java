package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceAdmissionTest {
  private BrandEvidence source(String text) {
    var source = mock(BrandEvidence.class);
    var brand = new Brand("Brand", new Workspace("Studio", "Creator"));
    when(source.getId()).thenReturn(UUID.randomUUID());
    when(source.getBrand()).thenReturn(brand);
    when(source.getType()).thenReturn(EvidenceType.TEXT);
    when(source.getStatus()).thenReturn(EvidenceStatus.PENDING);
    when(source.getSourceReference()).thenReturn(text);
    when(source.getChecksum()).thenReturn(EvidenceAdmission.checksum("TEXT\n" + text));
    when(source.getCapturedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
    return source;
  }

  @Test
  void capturesUnicodeTextAndSeparateRegistrationAndContentDigests() {
    var source = source("Māori \uD83D\uDE00\n\ttext");
    var actor = UUID.randomUUID();
    var now = Instant.parse("2026-01-02T00:00:00Z");
    var a = new EvidenceAdmission(source, actor, now);
    assertThat(a.getReviewedText()).isEqualTo(source.getSourceReference());
    assertThat(a.getSourceChecksum())
        .isEqualTo(source.getChecksum())
        .isNotEqualTo(a.getContentChecksum());
    assertThat(a.getReviewedBy()).isEqualTo(actor);
    assertThat(a.getReviewedAt()).isEqualTo(now);
    assertThat(a.getPolicyVersion()).isEqualTo("text-review-v1");
  }

  @Test
  void rejectsMalformedUnicodeControlsBlankOversizedAndIncorrectDigest() {
    for (var text :
        java.util.List.of(
            " ",
            "x".repeat(20001),
            "\uD800",
            "\uDC00",
            "a\u0000b",
            "a\u0001b",
            "a\u007Fb",
            "a\u0085b"))
      assertThatThrownBy(() -> EvidenceAdmission.reviewableText(source(text)))
          .isInstanceOf(IllegalStateException.class);
    var source = source("Valid");
    when(source.getChecksum()).thenReturn("0".repeat(64));
    assertThatThrownBy(() -> EvidenceAdmission.reviewableText(source))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void deniesUnsupportedOrFailedSourcesAndArchivedParents() {
    for (var type : java.util.List.of(EvidenceType.FILE, EvidenceType.URL)) {
      var source = source("Text");
      when(source.getType()).thenReturn(type);
      assertThatThrownBy(() -> EvidenceAdmission.reviewableText(source))
          .isInstanceOf(IllegalStateException.class);
    }
    var source = source("Text");
    when(source.getStatus()).thenReturn(EvidenceStatus.FAILED);
    assertThatThrownBy(() -> EvidenceAdmission.reviewableText(source))
        .isInstanceOf(IllegalStateException.class);
    var archived = source("Text");
    archived.getBrand().getWorkspace().archive();
    assertThatThrownBy(() -> EvidenceAdmission.reviewableText(archived))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> EvidenceAdmission.reviewableText(null))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsMissingReviewerAndTimeBeforeCapture() {
    var source = source("Text");
    var now = Instant.parse("2026-01-02T00:00:00Z");
    assertThatThrownBy(() -> new EvidenceAdmission(source, null, now))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmission(source, UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EvidenceAdmission(source, UUID.randomUUID(), Instant.EPOCH))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
