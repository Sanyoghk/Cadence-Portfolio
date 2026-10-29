package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class BrandIdentityTest {
  @Test
  void analysisOriginRequiresPersistedSuccessfulSameBrandMatchingContent() {
    var b = new Brand("Brand", new Workspace("Studio", "Creator"));
    var brandId = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(b, "id", brandId);
    var c = content(List.of("Inter"));
    var a = new BrandAnalysisAttempt(brandId, 0, 0, "a".repeat(64), java.time.Instant.now());
    org.springframework.test.util.ReflectionTestUtils.setField(a, "id", UUID.randomUUID());
    assertThatThrownBy(() -> BrandIdentity.fromAnalysis(b, 1, c, a))
        .isInstanceOf(IllegalStateException.class);
    a.succeed(
        java.time.Instant.now(),
        "fixture",
        "model-v1",
        "prompt-v1",
        "DECLARED_FIELDS_ONLY",
        c.checksum());
    var identity = BrandIdentity.fromAnalysis(b, 1, c, a);
    assertThat(identity.getAnalysisAttempt()).isSameAs(a);
    assertThatThrownBy(() -> BrandIdentity.fromAnalysis(b, 2, content(List.of("Other")), a))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> BrandIdentity.fromAnalysis(new Brand("Other", b.getWorkspace()), 1, c, a))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void contentChecksumMatchesAnalysisCanonicalUtf8AndUnambiguousLists() {
    var c = content(List.of("Inter", "Māori \uD83D\uDE00"));
    var p = c.palette();
    var output =
        new com.cadence.application.ports.BrandAnalysisResult(
            new com.cadence.application.ports.BrandAnalysisResult.Palette(
                p.primary(), p.secondary(), p.accent(), p.neutral(), p.background()),
            c.fonts(),
            c.voiceUp(),
            c.voiceDown(),
            c.sampleCaption(),
            List.of(),
            "fixture",
            "model",
            "prompt");
    assertThat(c.checksum()).isEqualTo(output.contentChecksum());
    assertThat(content(List.of("Inter,Māori \uD83D\uDE00")).checksum()).isNotEqualTo(c.checksum());
  }

  @Test
  void approvalIsImmutableAndRevisionDoesNotCopyAuditOrAliasContent() {
    var source =
        new BrandIdentity(
            new Brand("Brand", new Workspace("Studio", "Creator")), 1, content(List.of("Inter")));
    assertThatThrownBy(() -> source.approve(UUID.randomUUID(), java.time.Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    source.onCreate();
    var id = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(source, "id", id);
    assertThatThrownBy(() -> source.approve(null, java.time.Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> source.approve(UUID.randomUUID(), source.getCreatedAt().minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
    var actor = UUID.randomUUID();
    var now = java.time.Instant.now();
    source.approve(actor, now);
    assertThat(source.getApprovedBy()).isEqualTo(actor);
    assertThat(source.getApprovedAt()).isEqualTo(now);
    assertThatThrownBy(() -> source.replaceContent(content(List.of("Lato"))))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> source.approve(actor, now)).isInstanceOf(IllegalStateException.class);
    var draft = source.createDraftVersion(3, content(List.of("Lato")));
    assertThat(draft.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
    assertThat(draft.getRevisedFromId()).isEqualTo(id);
    assertThat(draft.getApprovedBy()).isNull();
    assertThat(draft.getApprovedAt()).isNull();
    assertThat(source.getContent().fonts()).containsExactly("Inter");
    assertThat(draft.getContent().fonts()).containsExactly("Lato");
    assertThatThrownBy(() -> source.createDraftVersion(1, content(List.of("Inter"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> draft.createDraftVersion(4, content(List.of("Inter"))))
        .isInstanceOf(IllegalStateException.class);
  }

  private BrandIdentityContent.Palette palette() {
    return new BrandIdentityContent.Palette("#abcdef", "#ffffff", "#000000", "#123456", "#987654");
  }

  private BrandIdentityContent content(List<String> fonts) {
    return new BrandIdentityContent(palette(), fonts, List.of("Clear"), List.of("Spam"), "Caption");
  }

  @Test
  void normalizesAndDefensivelyCopiesDraftContent() {
    var fonts = new ArrayList<>(List.of(" Inter "));
    var content = content(fonts);
    fonts.clear();
    assertThat(content.palette().primary()).isEqualTo("#ABCDEF");
    assertThat(content.fonts()).containsExactly("Inter");
    var identity =
        new BrandIdentity(new Brand("Brand", new Workspace("Studio", "Creator")), 1, content);
    assertThat(identity.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
    assertThatThrownBy(() -> identity.getContent().fonts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    identity.replaceContent(content(List.of("Arial")));
    assertThat(content.fonts()).containsExactly("Inter");
    assertThat(identity.getContent().fonts()).containsExactly("Arial");
  }

  @Test
  void rejectsInvalidColorsCollectionsTextAndRevisionBounds() {
    assertThatThrownBy(
            () ->
                new BrandIdentityContent.Palette("red", "#FFFFFF", "#000000", "#123456", "#987654"))
        .isInstanceOf(IllegalArgumentException.class);
    for (var values :
        List.of(
            List.<String>of(),
            List.of("Inter", "inter"),
            List.of(" "),
            List.of("Bad\nFont"),
            List.of("Bad\u0085Font"),
            Collections.nCopies(6, "Inter")))
      assertThatThrownBy(() -> content(values)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new BrandIdentityContent(
                    palette(),
                    List.of("Inter"),
                    List.of("Clear"),
                    List.of("Spam"),
                    "X".repeat(1001)))
        .isInstanceOf(IllegalArgumentException.class);
    var brand = new Brand("Brand", new Workspace("Studio", "Creator"));
    for (int version : List.of(0, 1001))
      assertThatThrownBy(() -> new BrandIdentity(brand, version, content(List.of("Inter"))))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BrandIdentity(null, 1, content(List.of("Inter"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new BrandIdentity(brand, 1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
