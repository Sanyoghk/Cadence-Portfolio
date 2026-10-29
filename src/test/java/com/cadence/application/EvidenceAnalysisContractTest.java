package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.cadence.domain.EvidenceAdmission;
import java.util.*;
import org.junit.jupiter.api.Test;

class EvidenceAnalysisContractTest {
  private AnalysisTextSource source(String text) {
    return new AnalysisTextSource(
        UUID.randomUUID(), UUID.randomUUID(), 0, EvidenceAdmission.checksum(text), text);
  }

  private BrandAnalysisCommand command(List<AnalysisTextSource> sources) {
    return new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator", sources);
  }

  @Test
  void boundedUtf8AndImmutableSources() {
    var s = source("界".repeat(6666) + "ab");
    var input = new ArrayList<>(List.of(s));
    var c = command(input);
    input.clear();
    assertThat(c.sources()).containsExactly(s);
    assertThat(s.utf8Bytes()).isEqualTo(20000);
    assertThatThrownBy(() -> command(List.of(source("界".repeat(6667)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> command(List.of(s, s))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> c.sources().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void malformedUnicodeControlsAndChecksumRejected() {
    for (String text : List.of("\uD800", "a\u0001", "\uDC00"))
      assertThatThrownBy(() -> source(text)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new AnalysisTextSource(
                    UUID.randomUUID(), UUID.randomUUID(), 0, "0".repeat(64), "Reviewed"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quotesRequireSelectedSourceExactTextAndNoDuplicates() {
    var s = source("Reviewed quote 😀");
    var c = command(List.of(s));
    var quote = new AnalysisCitation(s.admissionId(), "quote 😀");
    c.validateCitations(List.of(quote));
    for (var quotes :
        List.of(
            List.<AnalysisCitation>of(),
            List.of(quote, quote),
            List.of(new AnalysisCitation(s.admissionId(), "Quote")),
            List.of(new AnalysisCitation(UUID.randomUUID(), "quote"))))
      assertThatThrownBy(() -> c.validateCitations(quotes))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> command(List.of()).validateCitations(List.of(quote)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisCitation(s.admissionId(), "界".repeat(334)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void inputDigestBindsSelectedAdmissionAndOrder() {
    var a = source("Reviewed A");
    var b = source("Reviewed B");
    assertThat(command(List.of(a, b)).sourceChecksum())
        .isNotEqualTo(command(List.of(b, a)).sourceChecksum());
    assertThat(command(List.of(a)).sourceChecksum())
        .isNotEqualTo(command(List.of(source(a.text()))).sourceChecksum());
  }

  @Test
  void halfOfValidSurrogatePairCannotBecomeAnExactQuote() {
    String text = "Reviewed 😀";
    String half = text.substring(text.length() - 2, text.length() - 1);
    assertThat(text.contains(half)).isTrue();
    assertThatThrownBy(() -> new AnalysisCitation(UUID.randomUUID(), half))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new com.cadence.domain.BrandAnalysisCitation(
                    UUID.randomUUID(), 0, UUID.randomUUID(), half))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
