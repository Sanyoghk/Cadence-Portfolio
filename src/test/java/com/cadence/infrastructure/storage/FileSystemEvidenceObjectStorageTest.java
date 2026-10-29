package com.cadence.infrastructure.storage;

import static org.junit.jupiter.api.Assertions.*;

import com.cadence.application.ports.EvidenceObjectStorage;
import com.cadence.application.ports.QuarantineReadFailure;
import com.cadence.application.ports.QuarantineReadFailure.Code;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class FileSystemEvidenceObjectStorageTest {
  @TempDir Path temporary;
  private Path root;
  private FileSystemEvidenceObjectStorage storage;
  private static final String KEY = "quarantine/brand/file.pdf";
  private static final String REFERENCE = "quarantine:" + KEY;

  @Test
  void mediaNamespaceCannotBeReadOrDeletedAsQuarantineAndIsBounded() throws Exception {
    byte[] bytes = {1, 2, 3};
    var key =
        new com.cadence.domain.MediaObjectKey(
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            com.cadence.domain.MediaObjectKey.hash(bytes),
            com.cadence.domain.MediaFormat.PNG);
    storage.storeMedia(key, bytes);
    assertArrayEquals(bytes, storage.readMedia(key, bytes.length));
    assertThrows(IllegalArgumentException.class, () -> storage.storeMedia(key, new byte[] {4}));
    assertThrows(QuarantineReadFailure.class, () -> storage.readMedia(key, 2));
    assertThrows(
        QuarantineReadFailure.class, () -> storage.readQuarantined("quarantine:" + key.key(), 3));
    assertThrows(
        QuarantineReadFailure.class, () -> storage.deleteQuarantined("media:" + key.key()));
    assertArrayEquals(bytes, storage.readMedia(key, 3));
  }

  @Test
  @EnabledOnOs({OS.LINUX, OS.MAC})
  void mediaReadAndWriteRejectLinkedParent() throws Exception {
    var key =
        new com.cadence.domain.MediaObjectKey(
            java.util.UUID.randomUUID(),
            java.util.UUID.randomUUID(),
            com.cadence.domain.MediaObjectKey.hash(new byte[] {1}),
            com.cadence.domain.MediaFormat.PNG);
    Files.createDirectories(root);
    var outside = Files.createDirectory(temporary.resolve("media-outside"));
    Files.createSymbolicLink(root.resolve("media"), outside);
    assertThrows(QuarantineReadFailure.class, () -> storage.storeMedia(key, new byte[] {1}));
    assertThrows(QuarantineReadFailure.class, () -> storage.readMedia(key, 1));
    try (var entries = Files.list(outside)) {
      assertEquals(0, entries.count());
    }
  }

  @Test
  void realStorageAndDevelopmentScannerRejectTamperingAndRecordInfection() throws Exception {
    byte[] content =
        "%PDF-1.7 EICAR-STANDARD-ANTIVIRUS-TEST-FILE"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String checksum =
        java.util.HexFormat.of()
            .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
    storage.storeQuarantined(KEY, content);
    var scanner =
        new com.cadence.application.services.QuarantinedEvidenceScanner(
            storage, new DevelopmentEvidenceMalwareScanner());
    assertEquals(
        com.cadence.application.ports.MalwareScanResult.INFECTED,
        scanner.scan(REFERENCE, checksum, content.length).result());
    Files.write(root.resolve(KEY), new byte[] {1});
    assertEquals(
        Code.CHECKSUM_MISMATCH,
        assertThrows(
                QuarantineReadFailure.class,
                () -> scanner.scan(REFERENCE, checksum, content.length))
            .code());
  }

  @BeforeEach
  void setUp() throws Exception {
    root = temporary.toRealPath().resolve("storage");
    storage = new FileSystemEvidenceObjectStorage(root.toString());
  }

  @Test
  void roundTripsExistingFileAndUrlKeyFormatsAndReturnsIndependentBytes() {
    byte[] bytes = {1, 2, 3};
    assertEquals(REFERENCE, storage.storeQuarantined(KEY, bytes));
    assertArrayEquals(bytes, storage.readQuarantined(REFERENCE, 3));
    var read = storage.readQuarantined(REFERENCE, 3);
    read[0] = 9;
    assertArrayEquals(bytes, storage.readQuarantined(REFERENCE, 3));
    String urlKey = "quarantine/url/" + java.util.UUID.randomUUID() + "/" + "a".repeat(64);
    assertArrayEquals(bytes, storage.readQuarantined(storage.storeQuarantined(urlKey, bytes), 3));
  }

  @Test
  void cleanupDeletesOnlyManagedGenerationsAndIsIdempotent() throws Exception {
    String key =
        "quarantine/writes/"
            + java.util.UUID.randomUUID()
            + "/"
            + java.util.UUID.randomUUID()
            + ".pdf";
    String reference = storage.storeQuarantined(key, new byte[] {1, 2, 3});
    storage.deleteQuarantined(reference);
    assertFalse(Files.exists(root.resolve(key)));
    assertDoesNotThrow(() -> storage.deleteQuarantined(reference));
    storage.storeQuarantined(KEY, new byte[] {4});
    assertEquals(
        Code.INVALID_REFERENCE,
        assertThrows(QuarantineReadFailure.class, () -> storage.deleteQuarantined(REFERENCE))
            .code());
    assertArrayEquals(new byte[] {4}, storage.readQuarantined(REFERENCE, 1));
    assertThrows(
        QuarantineReadFailure.class,
        () -> storage.deleteQuarantined("quarantine:quarantine/writes/../../outside"));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(
      strings = {
        "",
        "file:///etc/passwd",
        "https://example.com/file",
        "quarantine:../secret",
        "quarantine:/etc/passwd",
        "quarantine:C:/secret",
        "quarantine:quarantine/../secret",
        "quarantine:quarantine/a/../../secret",
        "quarantine:quarantine/a\\secret",
        "quarantine:quarantine//secret",
        "quarantine:quarantine/a/",
        "quarantine:quarantine/./secret",
        "quarantine:quarantine/%2e%2e/secret",
        "quarantine:quarantine/file:stream",
        "quarantine:quarantine/CON",
        "quarantine:quarantine/nul.pdf",
        "quarantine:quarantine/lpt1",
        "quarantine:quarantine/file.",
        "quarantine:quarantine/file ",
        "quarantine:quarantine/file\n",
        "quarantine:outside/file"
      })
  void rejectsNonCanonicalReferencesBeforeIo(String reference) {
    var failure =
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(reference, 10));
    assertEquals(Code.INVALID_REFERENCE, failure.code());
    assertFalse(Files.exists(root));
    assertNull(failure.getCause());
  }

  @Test
  void rejectsLongReferencesAndUnsafeWriteKeysWithoutCreatingDirectories() {
    assertThrows(
        QuarantineReadFailure.class,
        () -> storage.readQuarantined("quarantine:quarantine/" + "x".repeat(401), 10));
    assertThrows(
        QuarantineReadFailure.class, () -> storage.storeQuarantined("../outside", new byte[] {1}));
    assertThrows(
        QuarantineReadFailure.class,
        () -> storage.storeQuarantined("quarantine/CON", new byte[] {1}));
    assertFalse(Files.exists(root));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 10485761, 2147483647})
  void rejectsInvalidLimitsBeforeIo(int limit) {
    assertEquals(
        Code.SIZE_LIMIT,
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, limit))
            .code());
    assertFalse(Files.exists(root));
  }

  @Test
  void rejectsOversizedMissingEmptyAndDirectoryObjectsWithSafeErrors() throws Exception {
    assertEquals(
        Code.NOT_FOUND,
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 10))
            .code());
    storage.storeQuarantined(KEY, new byte[] {1, 2, 3});
    assertEquals(
        Code.SIZE_LIMIT,
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 2))
            .code());
    Files.write(root.resolve(KEY), new byte[0]);
    assertEquals(
        Code.INVALID_CONTENT,
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 2))
            .code());
    assertEquals(
        Code.UNSAFE_PATH,
        assertThrows(
                QuarantineReadFailure.class,
                () -> storage.readQuarantined("quarantine:quarantine/brand", 2))
            .code());
  }

  @Test
  void storageEnforcesHardWriteLimit() {
    assertThrows(IllegalArgumentException.class, () -> storage.storeQuarantined(KEY, null));
    assertThrows(IllegalArgumentException.class, () -> storage.storeQuarantined(KEY, new byte[0]));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            storage.storeQuarantined(
                KEY, new byte[EvidenceObjectStorage.MAX_QUARANTINE_BYTES + 1]));
    assertFalse(Files.exists(root));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void rejectsLinkedFileForReadAndWrite() throws Exception {
    Path outside = Files.write(temporary.resolve("outside"), new byte[] {7});
    Files.createDirectories(root.resolve("quarantine/brand"));
    Files.createSymbolicLink(root.resolve(KEY), outside);
    assertEquals(
        Code.UNSAFE_PATH,
        assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 10))
            .code());
    assertThrows(QuarantineReadFailure.class, () -> storage.storeQuarantined(KEY, new byte[] {8}));
    assertArrayEquals(new byte[] {7}, Files.readAllBytes(outside));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void rejectsLinkedParentForReadAndWrite() throws Exception {
    Path outside = Files.createDirectory(temporary.resolve("outside"));
    Files.write(outside.resolve("file.pdf"), new byte[] {7});
    Files.createDirectories(root.resolve("quarantine"));
    Files.createSymbolicLink(root.resolve("quarantine/brand"), outside);
    assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 10));
    assertThrows(QuarantineReadFailure.class, () -> storage.storeQuarantined(KEY, new byte[] {8}));
    assertArrayEquals(new byte[] {7}, Files.readAllBytes(outside.resolve("file.pdf")));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void rejectsLinkedStorageRoot() throws Exception {
    Path outside = Files.createDirectory(temporary.resolve("outside"));
    Files.createSymbolicLink(root, outside);
    assertThrows(QuarantineReadFailure.class, () -> storage.readQuarantined(REFERENCE, 10));
    assertThrows(QuarantineReadFailure.class, () -> storage.storeQuarantined(KEY, new byte[] {8}));
    assertFalse(Files.exists(outside.resolve("quarantine")));
  }
}
