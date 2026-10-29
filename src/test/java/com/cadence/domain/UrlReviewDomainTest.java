package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UrlReviewDomainTest {
  @Test
  void canonicalProjectionPreservesUnicodeQuotesAndBackslashes() throws Exception {
    var m =
        new WebsiteMetadata(
            "Māori 😀", "quote \" slash \\", "<script>untrusted</script>", "NO_TEXT", "website-v2");
    String p = UrlTextSnapshot.project(m);
    assertThat(p)
        .isEqualTo(
            "{\"format\":\"url-text-v1\",\"title\":\"Māori 😀\",\"description\":\"quote \\\" slash \\\\\",\"text\":\"<script>untrusted</script>\"}");
    var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(p);
    assertThat(tree.get("description").asText()).isEqualTo(m.description());
    assertThat(tree.get("text").asText()).isEqualTo(m.text());
  }

  @Test
  void malformedUnicodeAndUnknownExtractionAreRejected() {
    for (String value : java.util.List.of("\uD800", "\uDC00", "\uD800x"))
      assertThatThrownBy(
              () ->
                  UrlTextSnapshot.project(new WebsiteMetadata(value, "", "text", "", "website-v2")))
          .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> UrlTextSnapshot.project(new WebsiteMetadata("", "", "text", "", "website-v1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void selectorsCannotInventPolicyOrNegativeVersions() {
    assertThatThrownBy(
            () ->
                new UrlReviewToken(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    0,
                    0,
                    0,
                    "a".repeat(64),
                    "b".repeat(64),
                    Instant.now(),
                    "text-review-v1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new UrlReviewToken(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    -1,
                    0,
                    0,
                    "a".repeat(64),
                    "b".repeat(64),
                    Instant.now(),
                    UrlReviewContent.POLICY_VERSION))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
