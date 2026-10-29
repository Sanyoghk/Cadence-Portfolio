package com.cadence.presentation.dto;

import com.cadence.domain.BrandEvidence;
import com.cadence.domain.EvidenceStatus;
import com.cadence.domain.EvidenceType;
import java.time.Instant;
import java.util.UUID;

public record BrandEvidenceResponse(
    UUID id,
    UUID brandId,
    EvidenceType type,
    EvidenceStatus status,
    String sourceReference,
    String checksum,
    Instant capturedAt,
    String warning,
    String originalFileName,
    String mediaType,
    Instant createdAt) {

  public static BrandEvidenceResponse from(BrandEvidence evidence) {
    return new BrandEvidenceResponse(
        evidence.getId(),
        evidence.getBrand().getId(),
        evidence.getType(),
        evidence.getStatus(),
        evidence.getType() == EvidenceType.FILE ? null : evidence.getSourceReference(),
        evidence.getChecksum(),
        evidence.getCapturedAt(),
        evidence.getWarning(),
        evidence.getOriginalFileName(),
        evidence.getMediaType(),
        evidence.getCreatedAt());
  }
}
