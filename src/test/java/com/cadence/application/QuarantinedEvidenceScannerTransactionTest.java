package com.cadence.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.cadence.application.ports.*;
import com.cadence.application.services.QuarantinedEvidenceScanner;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class QuarantinedEvidenceScannerTransactionTest {
  @Autowired private QuarantinedEvidenceScanner service;
  @Autowired private PlatformTransactionManager transactions;
  @MockitoBean private EvidenceObjectStorage storage;
  @MockitoBean private EvidenceMalwareScanner scanner;

  @Test
  void springProxyRejectsInheritedTransactionsBeforeIo() {
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              assertThrows(
                  IllegalTransactionStateException.class,
                  () -> service.scan("quarantine:quarantine/file", "a".repeat(64), 10));
            });
    verifyNoInteractions(storage, scanner);
  }

  @Test
  void springProxyScansWithoutOpeningADatabaseTransaction() throws Exception {
    byte[] bytes = {1, 2, 3};
    String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    when(storage.readQuarantined(anyString(), anyInt()))
        .thenAnswer(
            invocation -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return bytes;
            });
    when(scanner.scan(any()))
        .thenAnswer(
            invocation -> {
              assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
              return new MalwareScanReport(
                  MalwareScanResult.CLEAN, "test-engine", "test-signatures");
            });
    assertEquals(
        MalwareScanResult.CLEAN, service.scan("quarantine:quarantine/file", checksum, 3).result());
  }
}
