package com.cadence.application.ports;

import java.time.Instant;

/**
 * Internal scan observation for checksum-verified bytes. A durable worker persists this provenance;
 * the receipt itself does not approve evidence.
 */
public record QuarantineScanReceipt(
    String checksum,
    Instant scannedAt,
    MalwareScanResult result,
    String engine,
    String signatureVersion) {
  public QuarantineScanReceipt {
    if (checksum == null || !checksum.matches("[0-9a-f]{64}") || scannedAt == null)
      throw new IllegalArgumentException("Invalid quarantine scan receipt");
    new MalwareScanReport(result, engine, signatureVersion);
  }
}
