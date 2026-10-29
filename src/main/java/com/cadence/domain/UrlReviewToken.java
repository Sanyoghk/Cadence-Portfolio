package com.cadence.domain;

import java.util.UUID;

/** Selectors of exactly the server text shown to the reviewer; never client-authored content. */
public record UrlReviewToken(
    UUID snapshotId,
    UUID latestScanId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    java.time.Instant scanExpiresAt,
    String policyVersion) {
  public UrlReviewToken {
    if (snapshotId == null
        || latestScanId == null
        || sourceVersion < 0
        || brandVersion < 0
        || workspaceVersion < 0
        || sourceChecksum == null
        || !sourceChecksum.matches("[0-9a-f]{64}")
        || contentChecksum == null
        || !contentChecksum.matches("[0-9a-f]{64}")
        || scanExpiresAt == null
        || !UrlReviewContent.POLICY_VERSION.equals(policyVersion))
      throw new IllegalArgumentException("Invalid URL review selectors");
  }

  public void requireMatches(UrlReviewContent c, long source, long brand, long workspace) {
    if (!snapshotId.equals(c.snapshotId())
        || !latestScanId.equals(c.latestScanId())
        || sourceVersion != source
        || brandVersion != brand
        || workspaceVersion != workspace
        || !sourceChecksum.equals(c.sourceChecksum())
        || !contentChecksum.equals(c.contentChecksum())
        || !scanExpiresAt.equals(c.scanExpiresAt()))
      throw new IllegalStateException("URL review selectors are no longer current");
  }
}
