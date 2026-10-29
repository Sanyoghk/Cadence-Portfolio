package com.cadence.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable audit value. This receipt is not a model input or perpetual consent. */
public record ApprovedIdentitySource(
    UUID identityId,
    int version,
    long lockVersion,
    String checksum,
    UUID approvedBy,
    Instant approvedAt,
    UUID analysisId,
    UUID requestedBy) {
  public ApprovedIdentitySource {
    if (identityId == null
        || version < 1
        || version > BrandIdentity.MAX_REVISIONS_PER_BRAND
        || lockVersion < 0
        || checksum == null
        || !checksum.matches("[0-9a-f]{64}")
        || approvedBy == null
        || approvedAt == null
        || requestedBy == null)
      throw new IllegalArgumentException("Invalid approved Identity source");
  }
}
