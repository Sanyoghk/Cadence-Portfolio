package com.cadence.domain;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ContentGenerationContractTest {
  PostContent original =
      new PostContent(
          "Human title",
          "Human caption",
          List.of("#human"),
          PostFormat.REEL,
          "Human prompt",
          "Human script");
  PostContent generated =
      new PostContent(
          "AI title", "AI caption", List.of("#ai"), PostFormat.VIDEO, "AI prompt", "AI script");

  @ParameterizedTest
  @EnumSource(ContentGenerationRequest.Field.class)
  void fieldMergeUsesOnlySelectedField(ContentGenerationRequest.Field field) {
    var r =
        new ContentGenerationRequest(
            UUID.randomUUID(), 0, List.of("w1s0"), UUID.randomUUID(), UUID.randomUUID(), 0L, field);
    var merged = r.merge(original, generated);
    assertThat(merged.title())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.TITLE
                ? generated.title()
                : original.title());
    assertThat(merged.caption())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.CAPTION
                ? generated.caption()
                : original.caption());
    assertThat(merged.hashtags())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.HASHTAGS
                ? generated.hashtags()
                : original.hashtags());
    assertThat(merged.format())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.FORMAT
                ? generated.format()
                : original.format());
    assertThat(merged.mediaPrompt())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.MEDIA_PROMPT
                ? generated.mediaPrompt()
                : original.mediaPrompt());
    assertThat(merged.videoScript())
        .isEqualTo(
            field == ContentGenerationRequest.Field.ALL
                    || field == ContentGenerationRequest.Field.VIDEO_SCRIPT
                ? generated.videoScript()
                : original.videoScript());
  }

  @Test
  void requestsRejectDuplicateSlotsUnsupportedBoundsAndInvalidRegeneration() {
    for (var slots :
        List.of(
            List.<String>of(),
            List.of("x", "x"),
            List.of("../foreign"),
            java.util.stream.IntStream.range(0, 85).mapToObj(i -> "s" + i).toList()))
      assertThatThrownBy(
              () ->
                  new ContentGenerationRequest(
                      UUID.randomUUID(),
                      0,
                      slots,
                      null,
                      null,
                      null,
                      ContentGenerationRequest.Field.ALL))
          .isInstanceOf(IllegalArgumentException.class);
    assertThat(
            new ContentGenerationRequest(
                    UUID.randomUUID(),
                    0,
                    List.of("z", "a"),
                    null,
                    null,
                    null,
                    ContentGenerationRequest.Field.ALL)
                .slotIds())
        .containsExactly("a", "z");
    assertThatThrownBy(
            () ->
                new ContentGenerationRequest(
                    UUID.randomUUID(),
                    0,
                    List.of("a"),
                    null,
                    null,
                    null,
                    ContentGenerationRequest.Field.CAPTION))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void providerUnknownOwnershipAndMissingVersionRejected() throws Exception {
    var mapper = new ObjectMapper();
    String valid =
        """
      {"schemaVersion":1,"items":[{"slotId":"w1s0","channel":"INSTAGRAM","content":{"title":"Title","caption":"Caption","hashtags":[],"format":"IMAGE","mediaPrompt":"Concept","videoScript":null}}],"provider":"fake","modelVersion":"demo","promptVersion":"v1","warnings":["HUMAN_REVIEW_REQUIRED"]}
      """;
    assertThat(mapper.readValue(valid, ContentGenerationResult.class).items()).hasSize(1);
    for (String invalid :
        List.of(
            valid.replace("\"schemaVersion\":1,", ""),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            valid.replace("\"title\":\"Title\"", "\"title\":\"Title\",\"approvedBy\":\"forged\""),
            valid.replace("\"provider\":\"fake\"", "\"provider\":\"fake\",\"cost\":0"),
            valid.replace("\"HUMAN_REVIEW_REQUIRED\"", "\"DEMONSTRATION_ONLY\"")))
      assertThatThrownBy(() -> mapper.readValue(invalid, ContentGenerationResult.class))
          .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
  }
}
