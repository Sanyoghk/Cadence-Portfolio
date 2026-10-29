package com.cadence.domain;

import java.util.UUID;

/** Selectors of exactly the server text shown to the reviewer; never client-authored content. */
public record PdfReviewToken(
    UUID artifactId,
    UUID originalScanId,
    UUID latestScanId,
    long sourceVersion,
    long brandVersion,
    long workspaceVersion,
    String sourceChecksum,
    String contentChecksum,
    java.time.Instant scanExpiresAt,
    String policyVersion) {
  public PdfReviewToken {
    if (artifactId == null
        || originalScanId == null
        || latestScanId == null
        || sourceVersion < 0
        || brandVersion < 0
        || workspaceVersion < 0
        || sourceChecksum == null
        || !sourceChecksum.matches("[0-9a-f]{64}")
        || contentChecksum == null
        || !contentChecksum.matches("[0-9a-f]{64}")
        || scanExpiresAt == null
        || !PdfReviewContent.POLICY_VERSION.equals(policyVersion))
      throw new IllegalArgumentException("Invalid PDF review selectors");
  }

  public void requireMatches(PdfReviewContent c, long source, long brand, long workspace) {
    if (!artifactId.equals(c.artifactId())
        || !originalScanId.equals(c.originalScanId())
        || !latestScanId.equals(c.latestScanId())
        || sourceVersion != source
        || brandVersion != brand
        || workspaceVersion != workspace
        || !sourceChecksum.equals(c.sourceChecksum())
        || !contentChecksum.equals(c.contentChecksum())
        || !scanExpiresAt.equals(c.scanExpiresAt()))
      throw new IllegalStateException("PDF review selectors are no longer current");
  }
}
