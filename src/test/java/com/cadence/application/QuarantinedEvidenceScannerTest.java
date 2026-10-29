package com.cadence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import com.cadence.application.services.QuarantinedEvidenceScanner;
import java.security.MessageDigest;
import java.time.*;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuarantinedEvidenceScannerTest {
  private final EvidenceObjectStorage storage = mock(EvidenceObjectStorage.class);
  private final EvidenceMalwareScanner scanner = mock(EvidenceMalwareScanner.class);
  private final Instant now = Instant.parse("2026-09-12T00:00:00Z");
  private final QuarantinedEvidenceScanner service =
      new QuarantinedEvidenceScanner(storage, scanner, Clock.fixed(now, ZoneOffset.UTC));
  private final byte[] bytes = {1, 2, 3};
  private final String reference = "quarantine:quarantine/brand/file.pdf";

  private String checksum() throws Exception {
    return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  @ParameterizedTest
  @EnumSource(MalwareScanResult.class)
  void bindsEveryExplicitOutcomeToVerifiedBytesAndTime(MalwareScanResult result) throws Exception {
    when(storage.readQuarantined(reference, 3)).thenReturn(bytes);
    when(scanner.scan(any())).thenReturn(report(result));
    var receipt = service.scan(reference, checksum(), 3);
    assertEquals(
        new QuarantineScanReceipt(checksum(), now, result, "test-engine", "test-signatures"),
        receipt);
    verify(scanner).scan(org.mockito.AdditionalMatchers.aryEq(bytes));
  }

  @Test
  void tamperedContentNeverReachesScanner() throws Exception {
    when(storage.readQuarantined(reference, 3)).thenReturn(new byte[] {9, 2, 3});
    assertEquals(
        Code.CHECKSUM_MISMATCH,
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3))
            .code());
    verifyNoInteractions(scanner);
  }

  @Test
  void incompleteOrOversizedAdapterContentNeverReachesScanner() throws Exception {
    when(storage.readQuarantined(reference, 3)).thenReturn(null, new byte[0], new byte[4]);
    assertEquals(
        Code.INVALID_CONTENT,
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3))
            .code());
    assertEquals(
        Code.INVALID_CONTENT,
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3))
            .code());
    assertEquals(
        Code.SIZE_LIMIT,
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3))
            .code());
    verifyNoInteractions(scanner);
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {"", "a", "G0000000000000000000000000000000000000000000000000000000000000000"})
  void rejectsInvalidExpectedChecksumsBeforeIo(String checksum) {
    assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum, 3));
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void rejectsInvalidLimitsBeforeIo() throws Exception {
    assertThrows(
        QuarantineReadFailure.class, () -> service.scan(reference, checksum(), Integer.MAX_VALUE));
    assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 0));
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void providerFailuresNeverBecomeCleanOrRetainSensitiveDetails() throws Exception {
    when(storage.readQuarantined(reference, 3))
        .thenThrow(new IllegalStateException("private-path secret"));
    var failure =
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3));
    assertEquals(Code.UNAVAILABLE, failure.code());
    assertNull(failure.getCause());
    assertFalse(failure.getMessage().contains("secret"));
    verifyNoInteractions(scanner);
    doReturn(bytes).when(storage).readQuarantined(reference, 3);
    when(scanner.scan(any())).thenThrow(new IllegalArgumentException("provider secret"));
    assertEquals(MalwareScanResult.ERROR, service.scan(reference, checksum(), 3).result());
    doReturn(null).when(scanner).scan(any());
    assertEquals(MalwareScanResult.ERROR, service.scan(reference, checksum(), 3).result());
  }

  @Test
  void scannerCannotMutateSharedStorageBytesOrReturnCleanForChangedBytes() throws Exception {
    when(storage.readQuarantined(reference, 3)).thenReturn(bytes);
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              byte[] input = invocation.getArgument(0);
              input[0] = 9;
              return report(MalwareScanResult.CLEAN);
            });
    assertEquals(MalwareScanResult.ERROR, service.scan(reference, checksum(), 3).result());
    assertArrayEquals(new byte[] {1, 2, 3}, bytes);
  }

  @Test
  void storageFailureCodesRemainSafeAndPreventScanning() throws Exception {
    when(storage.readQuarantined(reference, 3))
        .thenThrow(new QuarantineReadFailure(Code.NOT_FOUND));
    assertEquals(
        Code.NOT_FOUND,
        assertThrows(QuarantineReadFailure.class, () -> service.scan(reference, checksum(), 3))
            .code());
    verifyNoInteractions(scanner);
  }

  @Test
  void receiptsRejectMissingOrMalformedProvenance() throws Exception {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QuarantineScanReceipt(
                null, now, MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QuarantineScanReceipt(
                "wrong", now, MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QuarantineScanReceipt(
                checksum(), null, MalwareScanResult.CLEAN, "test-engine", "test-signatures"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new QuarantineScanReceipt(checksum(), now, null, "test-engine", "test-signatures"));
    assertThrows(
        IllegalArgumentException.class,
        () -> new QuarantineScanReceipt(checksum(), now, MalwareScanResult.CLEAN, "", "v1"));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new QuarantineScanReceipt(
                checksum(), now, MalwareScanResult.CLEAN, "engine", "bad\nversion"));
  }

  private MalwareScanReport report(MalwareScanResult result) {
    return new MalwareScanReport(result, "test-engine", "test-signatures");
  }
}
