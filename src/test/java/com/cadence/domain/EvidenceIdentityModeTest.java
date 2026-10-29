package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class EvidenceIdentityModeTest {
  private final UUID brandId = UUID.randomUUID();

  private BrandIdentityContent content() {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette("#000000", "#111111", "#222222", "#333333", "#FFFFFF"),
        List.of("Inter"),
        List.of("Clear"),
        List.of("Spam"),
        "Caption");
  }

  private Brand brand() {
    var b = mock(Brand.class);
    when(b.getId()).thenReturn(brandId);
    return b;
  }

  private BrandAnalysisAttempt receipt(UUID originalActor) {
    var a = mock(BrandAnalysisAttempt.class);
    when(a.getId()).thenReturn(UUID.randomUUID());
    when(a.getBrandId()).thenReturn(brandId);
    when(a.getStatus()).thenReturn(BrandAnalysisAttempt.Status.SUCCEEDED);
    when(a.getRequestedBy()).thenReturn(originalActor);
    when(a.getOutputChecksum()).thenReturn(content().checksum());
    return a;
  }

  @Test
  void explicitFactoryBindsCurrentCopierSeparatelyFromProducer() {
    UUID copier = UUID.randomUUID();
    var receipt = receipt(UUID.randomUUID());
    var identity = BrandIdentity.fromEvidenceAnalysis(brand(), 1, content(), receipt, copier);
    assertThat(identity.getAnalysisCopiedBy()).isEqualTo(copier);
    assertThat(identity.getAnalysisAttempt()).isSameAs(receipt);
    assertThat(identity.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
  }

  @Test
  void modesMissingActorAndWrongContentCannotBeConfused() {
    var evidence = receipt(UUID.randomUUID());
    var declared = receipt(null);
    assertThatThrownBy(() -> BrandIdentity.fromAnalysis(brand(), 1, content(), evidence))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                BrandIdentity.fromEvidenceAnalysis(
                    brand(), 1, content(), declared, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> BrandIdentity.fromEvidenceAnalysis(brand(), 1, content(), evidence, null))
        .isInstanceOf(IllegalArgumentException.class);
    when(evidence.getOutputChecksum()).thenReturn("0".repeat(64));
    assertThatThrownBy(
            () ->
                BrandIdentity.fromEvidenceAnalysis(
                    brand(), 1, content(), evidence, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class);
  }
}
