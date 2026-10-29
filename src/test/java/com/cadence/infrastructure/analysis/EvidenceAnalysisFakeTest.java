package com.cadence.infrastructure.analysis;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.cadence.domain.EvidenceAdmission;
import java.util.*;
import org.junit.jupiter.api.Test;

class EvidenceAnalysisFakeTest {
  @Test
  void fakeRetainsDemoWarningsAndNeverCutsSurrogatePair() {
    String text = "a".repeat(39) + "😀tail";
    var s =
        new AnalysisTextSource(
            UUID.randomUUID(), UUID.randomUUID(), 0, EvidenceAdmission.checksum(text), text);
    var command = new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator", List.of(s));
    var result = new DeterministicBrandAnalysisAdapter().analyze(command);
    command.validateCitations(result.citations());
    assertThat(result.citations().getFirst().quote()).isEqualTo("a".repeat(39) + "😀");
    assertThat(result.warnings())
        .containsExactly(
            BrandAnalysisResult.Warning.DEMONSTRATION_ONLY,
            BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED);
  }

  @Test
  void fakeQuotesDecodedUrlFieldWithExplicitPath() {
    var m =
        new com.cadence.domain.WebsiteMetadata("", "Quoted \"brand\"", "Body", "", "website-v2");
    var payload = com.cadence.domain.UrlTextSnapshot.project(m);
    var source =
        new AnalysisTextSource(
            UUID.randomUUID(),
            UUID.randomUUID(),
            0,
            EvidenceAdmission.checksum(payload),
            payload,
            Map.of("title", m.title(), "description", m.description(), "text", m.text()));
    var c = new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Creator", List.of(source));
    var result = new DeterministicBrandAnalysisAdapter().analyze(c);
    c.validateCitations(result.citations());
    assertThat(result.citations().getFirst().fieldPath()).isEqualTo("description");
    assertThat(result.citations().getFirst().quote()).isEqualTo(m.description());
    assertThat(result.promptVersion()).isEqualTo("mixed-evidence-v2");
    assertThat(result.warnings()).contains(BrandAnalysisResult.Warning.DEMONSTRATION_ONLY);
  }
}
