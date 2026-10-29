package com.cadence.domain;

import java.util.*;

/** Server application request; HTTP uses separate DTOs and derives tenant/actor itself. */
public record ContentGenerationRequest(
    UUID strategyId,
    long strategyVersion,
    List<String> slotIds,
    UUID postId,
    UUID revisionId,
    Long revisionVersion,
    Field field) {
  public enum Field {
    ALL,
    TITLE,
    CAPTION,
    HASHTAGS,
    FORMAT,
    MEDIA_PROMPT,
    VIDEO_SCRIPT
  }

  public ContentGenerationRequest {
    if (strategyId == null
        || strategyVersion < 0
        || slotIds == null
        || slotIds.isEmpty()
        || slotIds.size() > 84
        || slotIds.stream().anyMatch(s -> s == null || !s.matches("[A-Za-z0-9_-]{1,64}"))
        || new HashSet<>(slotIds).size() != slotIds.size()
        || field == null) throw new IllegalArgumentException("Invalid Content request");
    slotIds = slotIds.stream().sorted().toList();
    if (postId == null
        ? revisionId != null || revisionVersion != null || field != Field.ALL
        : revisionId == null
            || revisionVersion == null
            || revisionVersion < 0
            || slotIds.size() != 1)
      throw new IllegalArgumentException("Invalid regeneration request");
  }

  public PostContent merge(PostContent original, PostContent generated) {
    if (postId == null || field == Field.ALL) return generated;
    if (original == null) throw new IllegalArgumentException("Missing regeneration baseline");
    return new PostContent(
        field == Field.TITLE ? generated.title() : original.title(),
        field == Field.CAPTION ? generated.caption() : original.caption(),
        field == Field.HASHTAGS ? generated.hashtags() : original.hashtags(),
        field == Field.FORMAT ? generated.format() : original.format(),
        field == Field.MEDIA_PROMPT ? generated.mediaPrompt() : original.mediaPrompt(),
        field == Field.VIDEO_SCRIPT ? generated.videoScript() : original.videoScript());
  }
}
