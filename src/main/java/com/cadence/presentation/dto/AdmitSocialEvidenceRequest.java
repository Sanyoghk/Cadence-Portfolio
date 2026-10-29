package com.cadence.presentation.dto;

import com.cadence.domain.SocialReviewToken;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public record AdmitSocialEvidenceRequest(
    @NotNull UUID snapshotId,
    @NotNull @PositiveOrZero Long sourceVersion,
    @NotNull @PositiveOrZero Long brandVersion,
    @NotNull @PositiveOrZero Long workspaceVersion,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentChecksum,
    @NotNull Instant bindingExpiresAt) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown social review field");
  }

  public SocialReviewToken selectors() {
    return new SocialReviewToken(
        snapshotId,
        sourceVersion,
        brandVersion,
        workspaceVersion,
        contentChecksum,
        bindingExpiresAt);
  }
}
