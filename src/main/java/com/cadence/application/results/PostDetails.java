package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;

/** Application snapshot, including historical source and audit, never lazy entities. */
public record PostDetails(
    UUID id,
    UUID revisionId,
    UUID brandId,
    UUID workspaceId,
    PlaybookContent.Channel platform,
    int revisionNumber,
    long lockVersion,
    PostRevision.State status,
    UUID revisedFromId,
    String title,
    String caption,
    List<String> hashtags,
    PostFormat format,
    String mediaPrompt,
    String videoScript,
    UUID authoredBy,
    Instant authoredAt,
    UUID editedBy,
    Instant editedAt,
    UUID approvedBy,
    Instant approvedAt,
    Source source,
    boolean current,
    boolean contentApproved,
    String legacyStatus,
    Instant legacyScheduledAt,
    Instant createdAt,
    Instant updatedAt,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        UUID generationBatchId,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        String generationSlotId,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
        List<UUID> mediaAssetIds) {
  public record Source(
      UUID strategyId,
      Integer revisionNumber,
      Long lockVersion,
      UUID approvedBy,
      Instant approvedAt,
      String checksum) {}

  public PostDetails {
    hashtags = List.copyOf(hashtags);
    mediaAssetIds = List.copyOf(mediaAssetIds);
  }

  public static PostDetails from(PostRevision r, boolean current) {
    var p = r.getPost();
    return new PostDetails(
        p.getId(),
        r.getId(),
        p.getBrand().getId(),
        p.getBrand().getWorkspace().getId(),
        p.getPlatform(),
        r.getRevisionNumber(),
        r.getLockVersion(),
        r.getStatus(),
        r.getRevisedFromId(),
        r.getTitle(),
        r.getCaption(),
        r.getHashtags(),
        r.getFormat(),
        r.getMediaPrompt(),
        r.getVideoScript(),
        r.getAuthoredBy(),
        r.getAuthoredAt(),
        r.getEditedBy(),
        r.getEditedAt(),
        r.getApprovedBy(),
        r.getApprovedAt(),
        new Source(
            p.getStrategy().getId(),
            p.getSourceRevision(),
            p.getSourceLockVersion(),
            p.getSourceApprovedBy(),
            p.getSourceApprovedAt(),
            p.getSourceChecksum()),
        current,
        current && r.getStatus() == PostRevision.State.APPROVED && r.getApprovedBy() != null,
        r.getLegacyStatus(),
        r.getLegacyScheduledAt(),
        r.getCreatedAt(),
        r.getUpdatedAt(),
        r.getGenerationBatchId(),
        r.getGenerationSlotId(),
        r.getMediaAssetIds());
  }
}
