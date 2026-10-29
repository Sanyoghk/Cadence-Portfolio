package com.cadence.application.results;

import com.cadence.domain.*;
import java.time.Instant;
import java.util.*;

public record MediaDetails(
    UUID id,
    UUID brandId,
    UUID workspaceId,
    long lockVersion,
    String origin,
    DemoMediaFixture fixture,
    MediaFormat format,
    String contentType,
    String checksum,
    int byteSize,
    int width,
    int height,
    int durationMillis,
    MediaAsset.State status,
    MediaAsset.Moderation moderation,
    ScanVerdict scanResult,
    String scanEngine,
    String scanSignature,
    Instant scannedAt,
    Instant completedAt,
    String verificationFailure,
    UUID requestedBy,
    Instant requestedAt,
    Instant deadline,
    UUID reviewedBy,
    Instant reviewedAt,
    UUID archivedBy,
    Instant archivedAt,
    List<String> warnings) {
  public MediaDetails {
    warnings = List.copyOf(warnings);
  }

  public static MediaDetails from(MediaAsset a) {
    return new MediaDetails(
        a.getId(),
        a.getBrand().getId(),
        a.getBrand().getWorkspace().getId(),
        a.getLockVersion(),
        a.getOrigin(),
        a.getFixture(),
        a.getFormat(),
        a.getFormat().mime(),
        a.getChecksum(),
        a.getByteSize(),
        a.getWidth(),
        a.getHeight(),
        a.getDurationMillis(),
        a.getStatus(),
        a.getModeration(),
        a.getScanResult(),
        a.getScanEngine(),
        a.getScanSignature(),
        a.getScannedAt(),
        a.getCompletedAt(),
        a.getVerificationFailure(),
        a.getRequestedBy(),
        a.getRequestedAt(),
        a.getDeadline(),
        a.getReviewedBy(),
        a.getReviewedAt(),
        a.getArchivedBy(),
        a.getArchivedAt(),
        a.getScanEngine() != null && a.getScanEngine().equals("cadence-eicar-fixture")
            ? List.of(
                "DEMONSTRATION_ONLY", "NOT_AI_GENERATED", "DEMO_SCAN_NOT_SECURITY_QUALIFICATION")
            : List.of("DEMONSTRATION_ONLY", "NOT_AI_GENERATED"));
  }

  public MediaObjectKey key() {
    return new MediaObjectKey(brandId, id, checksum, format);
  }
}
