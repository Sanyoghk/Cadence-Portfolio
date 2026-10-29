package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import com.cadence.application.ports.MalwareScanResult;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Real engine/official signatures, without depending on CDN availability or the image build date.
 */
@Testcontainers
class ClamdContainerIntegrationTest {
  @Container
  static final GenericContainer<?> CLAMAV =
      new GenericContainer<>(com.cadence.support.TestContainerImages.clamav())
          .withEnv("TZ", "UTC")
          .withEnv("CLAMAV_NO_FRESHCLAMD", "true")
          .withEnv("CLAMD_STARTUP_TIMEOUT", "180")
          .withCopyFileToContainer(
              MountableFile.forHostPath(java.nio.file.Path.of("docker/clamav/clamd.conf")),
              "/etc/clamav/clamd.conf")
          .withExposedPorts(3310)
          .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(4)));

  private static final byte[] EICAR =
      "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*"
          .getBytes(StandardCharsets.US_ASCII);

  @Test
  void actualDemoPngAndMp4AreCleanByRealEngineWithoutClaimingModeration() throws Exception {
    Instant published = loadedDatabaseTime();
    try (var scanner =
        new ClamdEvidenceMalwareScanner(
            properties(CLAMAV.getMappedPort(3310)),
            java.net.InetAddress::getAllByName,
            Clock.fixed(published.plusSeconds(3600), ZoneOffset.UTC))) {
      for (var fixture : com.cadence.domain.DemoMediaFixture.values())
        try (var in = getClass().getResourceAsStream("/demo-media/" + fixture.resource())) {
          var bytes = in.readAllBytes();
          fixture.verify(bytes);
          assertThat(scanner.scan(bytes).result()).isEqualTo(MalwareScanResult.CLEAN);
        }
    }
  }

  @Test
  void cleanTextAndEicarAreScannedByRealClamdWithRealDatabaseProvenance() throws Exception {
    Instant published = loadedDatabaseTime();
    try (var scanner =
        new ClamdEvidenceMalwareScanner(
            properties(CLAMAV.getMappedPort(3310)),
            java.net.InetAddress::getAllByName,
            Clock.fixed(published.plusSeconds(3600), ZoneOffset.UTC))) {
      var clean = scanner.scan("safe text evidence".getBytes(StandardCharsets.UTF_8));
      assertThat(clean.result()).isEqualTo(MalwareScanResult.CLEAN);
      assertThat(clean.engine()).isEqualTo("clamav-1.4.6");
      assertThat(clean.signatureVersion()).startsWith("daily-").endsWith("@" + published);
      assertThat(scanner.scan(EICAR).result()).isEqualTo(MalwareScanResult.INFECTED);
    }
  }

  @Test
  void realLoadedSignaturesFailClosedWhenClockExceedsFreshnessWindow() throws Exception {
    Instant published = loadedDatabaseTime();
    try (var scanner =
        new ClamdEvidenceMalwareScanner(
            properties(CLAMAV.getMappedPort(3310)),
            java.net.InetAddress::getAllByName,
            Clock.fixed(published.plus(Duration.ofHours(49)), ZoneOffset.UTC))) {
      assertThat(scanner.scan("safe text".getBytes(StandardCharsets.UTF_8)).result())
          .isEqualTo(MalwareScanResult.UNAVAILABLE);
    }
  }

  @Test
  void realConfiguredDaemonRejectsEncryptedPdfThatCannotBeInspected() throws Exception {
    byte[] encrypted;
    try (var fixture = getClass().getResourceAsStream("/evidence/encrypted-aes256.pdf")) {
      assertThat(fixture).isNotNull();
      encrypted = fixture.readAllBytes();
    }
    try (var scanner =
        new ClamdEvidenceMalwareScanner(
            properties(CLAMAV.getMappedPort(3310)),
            java.net.InetAddress::getAllByName,
            Clock.fixed(loadedDatabaseTime().plusSeconds(3600), ZoneOffset.UTC))) {
      assertThat(scanner.scan(encrypted).result()).isEqualTo(MalwareScanResult.INFECTED);
    }
  }

  @Test
  void realLoadedFutureDatabaseIsRejectedAndOutageNeverProducesClean() throws Exception {
    Instant published = loadedDatabaseTime();
    try (var scanner =
        new ClamdEvidenceMalwareScanner(
            properties(CLAMAV.getMappedPort(3310)),
            java.net.InetAddress::getAllByName,
            Clock.fixed(published.minusSeconds(301), ZoneOffset.UTC))) {
      assertThat(scanner.scan(EICAR).result()).isEqualTo(MalwareScanResult.UNAVAILABLE);
    }
    int unavailablePort;
    try (ServerSocket socket = new ServerSocket(0)) {
      unavailablePort = socket.getLocalPort();
    }
    try (var scanner = new ClamdEvidenceMalwareScanner(properties(unavailablePort))) {
      assertThat(scanner.scan(EICAR).result()).isEqualTo(MalwareScanResult.UNAVAILABLE);
    }
  }

  private static ClamdScannerProperties properties(int port) {
    return new ClamdScannerProperties(
        CLAMAV.getHost(),
        port,
        List.of("127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16"),
        Duration.ofSeconds(30),
        Duration.ofHours(48),
        2,
        true);
  }

  private static Instant loadedDatabaseTime() throws Exception {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(CLAMAV.getHost(), CLAMAV.getMappedPort(3310)), 3000);
      socket.setSoTimeout(3000);
      socket.getOutputStream().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));
      var response = new ByteArrayOutputStream();
      int value;
      while ((value = socket.getInputStream().read()) > 0 && response.size() < 512)
        response.write(value);
      assertThat(value).isZero();
      String[] parts = response.toString(StandardCharsets.US_ASCII).split("/", -1);
      assertThat(parts).hasSize(3);
      return LocalDateTime.parse(
              parts[2].replaceAll(" +", " "),
              DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss uuuu", Locale.ENGLISH))
          .toInstant(ZoneOffset.UTC);
    }
  }
}
