package com.cadence.presentation.dto;

import com.cadence.application.results.StrategyDetails;
import com.cadence.domain.ApprovalStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StrategyResponse(
    UUID id,
    UUID brandId,
    UUID workspaceId,
    int version,
    long lockVersion,
    ApprovalStatus status,
    UUID revisedFromId,
    UUID approvedBy,
    Instant approvedAt,
    StrategyDetails.Cadence cadence,
    List<StrategyDetails.Pillar> contentPillars,
    StrategyDetails.Generation generation,
    Instant createdAt,
    Instant updatedAt,
    int contentFormatVersion,
    String completeness,
    com.cadence.domain.PlaybookContent playbook,
    UUID editedBy,
    Instant editedAt,
    boolean readyContentSource) {
  public StrategyResponse {
    contentPillars = List.copyOf(contentPillars);
  }

  public static StrategyResponse from(StrategyDetails details) {
    return new StrategyResponse(
        details.id(),
        details.brandId(),
        details.workspaceId(),
        details.version(),
        details.lockVersion(),
        details.status(),
        details.revisedFromId(),
        details.approvedBy(),
        details.approvedAt(),
        details.cadence(),
        details.contentPillars(),
        details.generation(),
        details.createdAt(),
        details.updatedAt(),
        details.contentFormatVersion(),
        details.completeness(),
        details.playbook(),
        details.editedBy(),
        details.editedAt(),
        details.readyContentSource());
  }
}
