package com.cadence.application.results;

import java.time.Instant;
import java.util.UUID;

public record PdfReviewPreview(
    UUID evidenceId,
    UUID artifactId,
    UUID originalScanId,
    UUID latestScanId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    String text,
    String rendererVersion,
    String policyVersion,
    Instant scanExpiresAt,
    Instant checkedAt,
    boolean requiresHumanReview,
    boolean admittedToAi) {}
