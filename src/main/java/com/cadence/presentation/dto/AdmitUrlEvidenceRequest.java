package com.cadence.presentation.dto;

import com.cadence.domain.UrlReviewToken;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record AdmitUrlEvidenceRequest(
    @NotNull UUID snapshotId,
    @NotNull UUID latestScanId,
    @NotNull @PositiveOrZero Long sourceVersion,
    @NotNull @PositiveOrZero Long brandVersion,
    @NotNull @PositiveOrZero Long workspaceVersion,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String sourceChecksum,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentChecksum,
    @NotNull java.time.Instant scanExpiresAt,
    @NotNull @Pattern(regexp = "url-text-review-v1") String policyVersion) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown URL review field");
  }

  public UrlReviewToken selectors() {
    return new UrlReviewToken(
        snapshotId,
        latestScanId,
        sourceVersion,
        brandVersion,
        workspaceVersion,
        sourceChecksum,
        contentChecksum,
        scanExpiresAt,
        policyVersion);
  }
}
