package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record BrandIdentityDetails(
    UUID id,
    UUID brandId,
    UUID workspaceId,
    int version,
    long lockVersion,
    ApprovalStatus status,
    BrandIdentityContent content,
    UUID revisedFromId,
    UUID approvedBy,
    Instant approvedAt,
    Instant createdAt,
    Instant updatedAt,
    IdentityAnalysisProvenance analysisProvenance) {
  public static BrandIdentityDetails from(BrandIdentity value) {
    return new BrandIdentityDetails(
        value.getId(),
        value.getBrand().getId(),
        value.getBrand().getWorkspace().getId(),
        value.getVersion(),
        value.getLockVersion(),
        value.getStatus(),
        value.getContent(),
        value.getRevisedFromId(),
        value.getApprovedBy(),
        value.getApprovedAt(),
        value.getCreatedAt(),
        value.getUpdatedAt(),
        IdentityAnalysisProvenance.from(value.getAnalysisAttempt()));
  }
}
