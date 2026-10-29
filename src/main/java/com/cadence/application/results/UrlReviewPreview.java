package com.cadence.application.results;

import java.time.Instant;
import java.util.UUID;

public record UrlReviewPreview(
    UUID evidenceId,
    UUID snapshotId,
    UUID latestScanId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    String text,
    com.cadence.domain.WebsiteMetadata metadata,
    UUID fetchJobId,
    int fetchAttempt,
    String fetchedChecksum,
    Instant fetchedAt,
    Instant extractedAt,
    String policyVersion,
    Instant scanExpiresAt,
    Instant checkedAt,
    boolean requiresHumanReview,
    boolean admittedToAi) {}
