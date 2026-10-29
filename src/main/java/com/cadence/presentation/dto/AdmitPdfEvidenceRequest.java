package com.cadence.presentation.dto;

import com.cadence.domain.PdfReviewToken;
import jakarta.validation.constraints.*;
import java.util.UUID;

public record AdmitPdfEvidenceRequest(
    @NotNull UUID artifactId,
    @NotNull UUID originalScanId,
    @NotNull UUID latestScanId,
    @NotNull @PositiveOrZero Long sourceVersion,
    @NotNull @PositiveOrZero Long brandVersion,
    @NotNull @PositiveOrZero Long workspaceVersion,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String sourceChecksum,
    @NotNull @Pattern(regexp = "[0-9a-f]{64}") String contentChecksum,
    @NotNull java.time.Instant scanExpiresAt,
    @NotNull @Pattern(regexp = "pdf-text-review-v1") String policyVersion) {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown PDF review field");
  }

  public PdfReviewToken selectors() {
    return new PdfReviewToken(
        artifactId,
        originalScanId,
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
