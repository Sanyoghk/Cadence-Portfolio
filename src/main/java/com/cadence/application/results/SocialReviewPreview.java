package com.cadence.application.results;

import com.cadence.domain.SocialEvidenceProvider;
import java.time.Instant;
import java.util.UUID;

public record SocialReviewPreview(
    UUID snapshotId,
    UUID evidenceId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    String text,
    SocialEvidenceProvider provider,
    Instant capturedAt,
    Instant bindingExpiresAt,
    String policyVersion,
    String warnings) {}
