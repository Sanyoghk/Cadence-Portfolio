package com.cadence.presentation.dto;

import com.cadence.domain.Workspace;
import com.cadence.domain.WorkspaceStatus;
import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
    UUID id,
    String name,
    String industry,
    WorkspaceStatus status,
    long lockVersion,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkspaceResponse from(Workspace workspace) {
    return new WorkspaceResponse(
        workspace.getId(),
        workspace.getName(),
        workspace.getIndustry(),
        workspace.getStatus(),
        workspace.getLockVersion(),
        workspace.getCreatedAt(),
        workspace.getUpdatedAt());
  }
}
