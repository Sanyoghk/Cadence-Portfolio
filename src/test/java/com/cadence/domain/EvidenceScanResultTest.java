package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EvidenceScanResultTest {
  private final Instant now = Instant.parse("2026-09-12T00:00:00Z");

  private BrandEvidence file() {
    var item =
        BrandEvidence.quarantinedFile(
            new Brand("Brand", new Workspace("Studio", "Creator")),
            "quarantine:quarantine/file.pdf",
            "a".repeat(64),
            now.minusSeconds(1),
            "file.pdf",
            "application/pdf");
    ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
    return item;
  }

  private DurableJob running(BrandEvidence item) {
    var job = new DurableJob("BRAND_EVIDENCE_INGEST", "evidence:" + item.getId(), "test", 3, now);
    ReflectionTestUtils.setField(job, "id", UUID.randomUUID());
    job.claim("worker", now, Duration.ofMinutes(2));
    return job;
  }

  private EvidenceScanResult result(
      BrandEvidence evidence,
      DurableJob job,
      String checksum,
      Instant scannedAt,
      ScanVerdict verdict,
      Instant currentTime) {
    return new EvidenceScanResult(
        evidence, job, checksum, scannedAt, verdict, "test-engine", "test-signatures", currentTime);
  }

  @Test
  void validatesJobChecksumTimeAndEvidenceBeforeRecording() {
    var item = file();
    var job = running(item);
    var valid = result(item, job, item.getChecksum(), now, ScanVerdict.CLEAN, now);
    assertEquals(ScanVerdict.CLEAN, valid.getVerdict());
    assertEquals("test-engine", valid.getScannerEngine());
    assertEquals("test-signatures", valid.getSignatureVersion());
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, running(file()), item.getChecksum(), now, ScanVerdict.CLEAN, now));
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, "b".repeat(64), now, ScanVerdict.CLEAN, now));
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, item.getChecksum(), now.minusSeconds(2), ScanVerdict.CLEAN, now));
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, item.getChecksum(), now.plusSeconds(1), ScanVerdict.CLEAN, now));
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, item.getChecksum(), now, null, now));
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, item.getChecksum(), now, ScanVerdict.CLEAN, now.plusSeconds(121)));
    job.succeed("worker", job.getAttempts(), now);
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, job, item.getChecksum(), now, ScanVerdict.CLEAN, now));

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new EvidenceScanResult(
                item, running(item), item.getChecksum(), now, ScanVerdict.CLEAN, "", "v1", now));
  }

  @Test
  void failureIsRestrictedToPendingFiles() {
    var item = file();
    item.failFileScan();
    assertEquals(EvidenceStatus.FAILED, item.getStatus());
    assertThrows(IllegalStateException.class, item::failFileScan);
    var url =
        new BrandEvidence(
            item.getBrand(), EvidenceType.URL, "https://example.com", "b".repeat(64), now);
    assertThrows(IllegalStateException.class, url::failFileScan);
    assertThrows(
        IllegalArgumentException.class,
        () -> result(item, running(item), item.getChecksum(), now, ScanVerdict.CLEAN, now));
  }
}
