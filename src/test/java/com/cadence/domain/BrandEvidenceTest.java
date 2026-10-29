package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BrandEvidenceTest {
  @Test
  void textContractAcceptsTwentyThousandCharactersButReferencesRemainBounded() {
    Brand brand = new Brand("Brand", new Workspace("Workspace", "Creator"));
    assertThat(
            new BrandEvidence(
                    brand, EvidenceType.TEXT, "x".repeat(20000), "a".repeat(64), Instant.now())
                .getSourceReference())
        .hasSize(20000);
    assertThatThrownBy(
            () ->
                new BrandEvidence(
                    brand, EvidenceType.TEXT, "x".repeat(20001), "a".repeat(64), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new BrandEvidence(
                    brand, EvidenceType.URL, "x".repeat(4097), "a".repeat(64), Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void fetchedAndFailedAreTerminalForFetching() {
    Brand brand = new Brand("Brand", new Workspace("Workspace", "Creator"));
    BrandEvidence item =
        new BrandEvidence(
            brand, EvidenceType.URL, "https://example.com", "a".repeat(64), Instant.now());
    assertThatThrownBy(
            () -> item.recordFetched("public:unsafe", "b".repeat(64), "text/plain", Instant.now()))
        .isInstanceOf(IllegalArgumentException.class);
    item.recordFetched("quarantine:source", "b".repeat(64), "text/plain", Instant.now());
    assertThatThrownBy(item::failUrlFetch).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                item.recordFetched("quarantine:again", "b".repeat(64), "text/plain", Instant.now()))
        .isInstanceOf(IllegalStateException.class);
    BrandEvidence failed =
        new BrandEvidence(
            brand, EvidenceType.URL, "https://example.com", "a".repeat(64), Instant.now());
    failed.failUrlFetch();
    assertThatThrownBy(
            () ->
                failed.recordFetched(
                    "quarantine:again", "b".repeat(64), "text/plain", Instant.now()))
        .isInstanceOf(IllegalStateException.class);
  }
}
