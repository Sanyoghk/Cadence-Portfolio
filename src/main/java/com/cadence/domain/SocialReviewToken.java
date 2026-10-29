package com.cadence.domain;

import java.time.Instant;
import java.util.UUID;

public record SocialReviewToken(
    UUID snapshotId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String contentChecksum,
    Instant bindingExpiresAt) {
  public SocialReviewToken {
    if (snapshotId == null
        || sourceVersion < 0
        || brandVersion < 0
        || workspaceVersion < 0
        || contentChecksum == null
        || !contentChecksum.matches("[0-9a-f]{64}")
        || bindingExpiresAt == null)
      throw new IllegalArgumentException("Invalid social review selectors");
  }
}
