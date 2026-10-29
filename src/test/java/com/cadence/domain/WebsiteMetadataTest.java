package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebsiteMetadataTest {
  private WebsiteMetadata metadata() {
    return new WebsiteMetadata("Title", "Description", "Text", "", "website-v1");
  }

  @Test
  void rejectsInvalidAdapterOutputAndRequiresFetchedEvidence() {
    assertThatThrownBy(() -> new WebsiteMetadata(null, "", "", "", "v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WebsiteMetadata("x".repeat(301), "", "", "", "v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WebsiteMetadata("", "", "\u0000", "", "v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new WebsiteMetadata("", "", "", "", ""))
        .isInstanceOf(IllegalArgumentException.class);
    Instant now = Instant.now();
    var evidence =
        new BrandEvidence(
            new Brand("Brand", new Workspace("Studio", "Creator")),
            EvidenceType.URL,
            "https://example.com",
            "a".repeat(64),
            now);
    assertThatThrownBy(() -> evidence.recordWebsiteMetadata(metadata(), now))
        .isInstanceOf(IllegalStateException.class);
    evidence.recordFetched("quarantine:test", "b".repeat(64), "text/html", now);
    assertThatThrownBy(() -> evidence.recordWebsiteMetadata(metadata(), now.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    evidence.recordWebsiteMetadata(metadata(), now);
    assertThat(evidence.getWebsiteMetadata()).isEqualTo(metadata());
    assertThat(evidence.getStatus()).isEqualTo(EvidenceStatus.FETCHED);
    assertThatThrownBy(() -> evidence.recordWebsiteMetadata(metadata(), now))
        .isInstanceOf(IllegalStateException.class);
  }
}
