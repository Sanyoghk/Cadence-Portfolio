package com.cadence.application;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.cadence.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class UrlAnalysisContractTest {
  @Test
  void fieldQuotesUseDecodedValuesAndDigestVersionPreservesLegacy() throws Exception {
    var metadata = new WebsiteMetadata("A \"quote\"", "Description", "Body", "", "website-v2");
    String payload = UrlTextSnapshot.project(metadata);
    var a = UUID.randomUUID();
    var e = UUID.randomUUID();
    var url =
        new AnalysisTextSource(
            a,
            e,
            0,
            EvidenceAdmission.checksum(payload),
            payload,
            Map.of(
                "title",
                metadata.title(),
                "description",
                metadata.description(),
                "text",
                metadata.text()));
    var command =
        new BrandAnalysisCommand(UUID.randomUUID(), 0, 0, "Brand", "Industry", List.of(url));
    command.validateCitations(List.of(new AnalysisCitation(a, "\"quote\"", "title")));
    assertThatThrownBy(
            () -> command.validateCitations(List.of(new AnalysisCitation(a, "\"quote\""))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AnalysisCitation(a, "quote", "format"))
        .isInstanceOf(IllegalArgumentException.class);
    var text = new AnalysisTextSource(a, e, 0, EvidenceAdmission.checksum(payload), payload);
    var legacy =
        new BrandAnalysisCommand(command.analysisId(), 0, 0, "Brand", "Industry", List.of(text));
    assertThat(legacy.sourceChecksum())
        .isEqualTo(
            EvidenceAdmission.checksum(
                "text-evidence-v1\nBrand\nIndustry\n"
                    + a
                    + "\n"
                    + e
                    + "\n0\n"
                    + text.contentChecksum()));
    assertThat(command.sourceChecksum()).isNotEqualTo(legacy.sourceChecksum());
    var serialized =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(url));
    assertThat(serialized.has("fields")).isFalse();
    assertThat(serialized.path("text").asText()).isEqualTo(payload);
    assertThat(serialized.path("representation").asText()).isEqualTo("url-text-v1");
  }
}
