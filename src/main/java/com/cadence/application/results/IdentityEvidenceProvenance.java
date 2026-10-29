package com.cadence.application.results;

import java.util.*;

public record IdentityEvidenceProvenance(
    UUID identityId,
    UUID originIdentityId,
    UUID analysisId,
    UUID copiedBy,
    String sourceChecksum,
    List<Citation> citations) {
  public record Citation(
      UUID admissionId,
      UUID evidenceId,
      long sourceVersion,
      String quote,
      String fieldPath,
      UUID urlSnapshotId,
      UUID socialSnapshotId,
      UUID artifactId,
      UUID originalScanId,
      UUID latestScanId,
      boolean withdrawn) {}

  public IdentityEvidenceProvenance {
    citations = List.copyOf(citations);
  }
}
