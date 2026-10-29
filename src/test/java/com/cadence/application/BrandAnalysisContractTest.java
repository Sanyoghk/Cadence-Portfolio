package com.cadence.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.*;
import com.cadence.application.services.BrandIngestService;
import com.cadence.domain.Brand;
import com.cadence.domain.Workspace;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrandAnalysisContractTest {
  private BrandAnalysisResult result(List<String> fonts, String caption, String provider) {
    return new BrandAnalysisResult(
        new BrandAnalysisResult.Palette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"),
        fonts,
        List.of("clear"),
        List.of("spammy"),
        caption,
        List.of(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED),
        provider,
        "model-v1",
        "prompt-v1");
  }

  @Test
  void commandIsNormalizedBoundedAndContainsNoEntity() {
    var command = new BrandAnalysisCommand(UUID.randomUUID(), 1, 2, " Brand ", " Creator ");
    assertThat(command.brandName()).isEqualTo("Brand");
    for (String input : List.of(" ", "a".repeat(256), "bad\u0000input")) {
      assertThatThrownBy(() -> new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, input, "Creator"))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThatThrownBy(() -> new BrandAnalysisCommand(null, 0, 0, "Brand", "Creator"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void providerResultRejectsMalformedOrExcessiveOutput() {
    assertThatThrownBy(
            () ->
                new BrandAnalysisResult.Palette(
                    "url(javascript:alert(1))", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5"))
        .isInstanceOf(IllegalArgumentException.class);
    for (var fonts :
        List.of(
            List.<String>of(), List.of("x".repeat(101)), java.util.Collections.nCopies(6, "Inter")))
      assertThatThrownBy(() -> result(fonts, "Caption", "provider"))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> result(List.of("Inter"), "x".repeat(1001), "provider"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> result(List.of("Inter"), "Caption", "token=private-secret"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void immutableResultAndFreshSignatureCannotBeModifiedThroughAdapterAliases() {
    var fonts = new ArrayList<>(List.of("Inter"));
    var result = result(fonts, "Caption", "provider");
    fonts.add("Unvalidated");
    assertThat(result.fonts()).containsExactly("Inter");
    assertThatThrownBy(() -> result.fonts().add("Changed"))
        .isInstanceOf(UnsupportedOperationException.class);
    var first = result.toSignature();
    first.getPalette().setPrimary("untrusted");
    assertThat(result.toSignature().getPalette().getPrimary()).isEqualTo("#000000");
  }

  @Test
  void contentChecksumDistinguishesAmbiguousListRepresentations() {
    var first = result(List.of("a, b", "c"), "Caption", "provider");
    var second = result(List.of("a", "b, c"), "Caption", "provider");
    assertThat(first.fonts().toString()).isEqualTo(second.fonts().toString());
    assertThat(first.contentChecksum()).isNotEqualTo(second.contentChecksum());
    assertThat(first.contentChecksum())
        .isEqualTo(result(List.of("a, b", "c"), "Caption", "provider").contentChecksum());
  }

  @Test
  void adapterFailureAndNullOutputBecomeSafeUnavailableWithoutReplacingSignature() {
    var port = mock(BrandAnalysisPort.class);
    var brand = new Brand("Brand", new Workspace("Studio", "Creator"));
    var original = result(List.of("Inter"), "Previous", "provider").toSignature();
    brand.updateSignature(original);
    var service = new BrandIngestService(port);
    when(port.analyze(any()))
        .thenThrow(new IllegalArgumentException("api_key=private-secret issuer.internal"));
    assertThatThrownBy(
            () ->
                service.analyze(
                    new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator")))
        .isInstanceOf(BrandAnalysisUnavailableException.class)
        .hasMessage("Brand analysis is unavailable")
        .hasNoCause();
    assertThat(brand.getSignature()).isSameAs(original);
    reset(port);
    assertThatThrownBy(
            () ->
                service.analyze(
                    new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator")))
        .isInstanceOf(BrandAnalysisUnavailableException.class)
        .hasNoCause();
  }
}
