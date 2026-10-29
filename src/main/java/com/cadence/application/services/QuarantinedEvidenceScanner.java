package com.cadence.application.services;

import com.cadence.application.ports.*;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal worker building block. Callers must select references/checksums from authorized
 * evidence.
 */
@Service
public class QuarantinedEvidenceScanner {
  private final EvidenceObjectStorage storage;
  private final EvidenceMalwareScanner scanner;
  private final Clock clock;

  public QuarantinedEvidenceScanner(EvidenceObjectStorage storage, EvidenceMalwareScanner scanner) {
    this(storage, scanner, Clock.systemUTC());
  }

  @Autowired
  public QuarantinedEvidenceScanner(
      EvidenceObjectStorage storage, EvidenceMalwareScanner scanner, Clock clock) {
    this.storage = Objects.requireNonNull(storage);
    this.scanner = Objects.requireNonNull(scanner);
    this.clock = Objects.requireNonNull(clock);
  }

  /**
   * No remote I/O may inherit a worker's claim/completion transaction. No database writes occur
   * here.
   */
  @Transactional(propagation = Propagation.NEVER)
  public QuarantineScanReceipt scan(String reference, String expectedChecksum, int maxBytes) {
    if (expectedChecksum == null || !expectedChecksum.matches("[0-9a-f]{64}"))
      throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
    if (maxBytes < 1 || maxBytes > EvidenceObjectStorage.MAX_QUARANTINE_BYTES)
      throw new QuarantineReadFailure(Code.SIZE_LIMIT);
    byte[] content;
    try {
      content = storage.readQuarantined(reference, maxBytes);
    } catch (QuarantineReadFailure failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw new QuarantineReadFailure(Code.UNAVAILABLE);
    }
    if (content == null || content.length == 0)
      throw new QuarantineReadFailure(Code.INVALID_CONTENT);
    if (content.length > maxBytes) throw new QuarantineReadFailure(Code.SIZE_LIMIT);
    // Own the exact bytes being checked/scanned even if an adapter reuses its returned array.
    content = content.clone();
    if (!expectedChecksum.equals(checksum(content)))
      throw new QuarantineReadFailure(Code.CHECKSUM_MISMATCH);
    MalwareScanReport report;
    try {
      report = scanner.scan(content);
    } catch (RuntimeException failure) {
      report = failureReport();
    }
    if (report == null || !expectedChecksum.equals(checksum(content))) report = failureReport();
    return new QuarantineScanReceipt(
        expectedChecksum,
        Instant.now(clock),
        report.result(),
        report.engine(),
        report.signatureVersion());
  }

  private MalwareScanReport failureReport() {
    return new MalwareScanReport(MalwareScanResult.ERROR, "scanner-adapter", "unavailable");
  }

  private String checksum(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }
}
