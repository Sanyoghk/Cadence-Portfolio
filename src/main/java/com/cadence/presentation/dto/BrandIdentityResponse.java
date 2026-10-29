package com.cadence.presentation.dto;

import com.cadence.application.results.BrandIdentityDetails;
import com.cadence.domain.*;
import java.time.Instant;
import java.util.UUID;

public record BrandIdentityResponse(
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
    com.cadence.application.results.IdentityAnalysisProvenance analysisProvenance) {
  public static BrandIdentityResponse from(BrandIdentityDetails v) {
    return new BrandIdentityResponse(
        v.id(),
        v.brandId(),
        v.workspaceId(),
        v.version(),
        v.lockVersion(),
        v.status(),
        v.content(),
        v.revisedFromId(),
        v.approvedBy(),
        v.approvedAt(),
        v.createdAt(),
        v.updatedAt(),
        v.analysisProvenance());
  }
}
