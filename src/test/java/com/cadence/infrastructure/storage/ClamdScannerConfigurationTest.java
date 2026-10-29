package com.cadence.infrastructure.storage;

import static org.assertj.core.api.Assertions.*;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClamdScannerConfigurationTest {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(ClamdScannerConfiguration.class)
          .withPropertyValues(
              "spring.profiles.active=clamd",
              "cadence.evidence.clamd.host=clamd",
              "cadence.evidence.clamd.allowed-cidrs=172.20.0.0/16",
              "cadence.evidence.clamd.private-channel-acknowledged=true");

  @Test
  void enablesValidatedAdapterWithDefaults() {
    context.run(
        value -> {
          assertThat(value).hasNotFailed().hasSingleBean(ClamdEvidenceMalwareScanner.class);
        });
  }

  @Test
  void rejectsMissingIsolationAcknowledgementAndInvalidBounds() {
    for (String property :
        List.of(
            "cadence.evidence.clamd.private-channel-acknowledged=false",
            "cadence.evidence.clamd.host=https://scanner.invalid",
            "cadence.evidence.clamd.host=clamd..private",
            "cadence.evidence.clamd.host=-clamd",
            "cadence.evidence.clamd.host=dead:beef:invalid",
            "cadence.evidence.clamd.port=0",
            "cadence.evidence.clamd.port=65536",
            "cadence.evidence.clamd.deadline=PT61S",
            "cadence.evidence.clamd.deadline=PT0.099S",
            "cadence.evidence.clamd.signature-max-age=PT73H",
            "cadence.evidence.clamd.signature-max-age=PT30S",
            "cadence.evidence.clamd.max-concurrent=0",
            "cadence.evidence.clamd.max-concurrent=9",
            "cadence.evidence.clamd.allowed-cidrs=0.0.0.0/0",
            "cadence.evidence.clamd.allowed-cidrs=169.254.0.0/16")) {
      context.withPropertyValues(property).run(value -> assertThat(value).hasFailed());
    }
  }

  @Test
  void acceptsNumericIpv6WithoutInvokingDnsDuringConfiguration() {
    assertThatCode(
            () ->
                new ClamdScannerProperties(
                    "::1",
                    3310,
                    List.of("::1/128"),
                    Duration.ofSeconds(30),
                    Duration.ofHours(48),
                    2,
                    true))
        .doesNotThrowAnyException();
    assertThatCode(
            () ->
                new ClamdScannerProperties(
                    "fd00::2",
                    3310,
                    List.of("fd00::/8"),
                    Duration.ofSeconds(30),
                    Duration.ofHours(48),
                    2,
                    true))
        .doesNotThrowAnyException();
  }

  @Test
  void acceptsOnlyPrivateNetworkAlignedLiteralCidrs() throws Exception {
    for (String cidr :
        List.of(
            "10.0.0.0/8", "172.16.0.0/12", "192.168.1.0/24", "127.0.0.1/32", "fd00::/8", "::1/128"))
      assertThatCode(() -> ClamdPrivateAddressPolicy.parse(cidr)).doesNotThrowAnyException();
    for (String cidr :
        List.of(
            "10.0.0.1/8",
            "10.0.0.0/7",
            "172.0.0.0/8",
            "192.168.0.0/15",
            "localhost/32",
            "127.1/8",
            "0127.0.0.0/8",
            "8.8.8.8/32",
            "::/0",
            "fe80::/64",
            "ff00::/8",
            "fd00::%eth0/64",
            "::ffff:127.0.0.1/128",
            "999.0.0.0/8",
            "10.0.0.0/999"))
      assertThatIllegalArgumentException().isThrownBy(() -> ClamdPrivateAddressPolicy.parse(cidr));
    ClamdPrivateAddressPolicy.requireAllowed(
        new InetAddress[] {InetAddress.getByName("fd01::2")},
        List.of(ClamdPrivateAddressPolicy.parse("fc00::/7")));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                ClamdPrivateAddressPolicy.requireAllowed(
                    new InetAddress[0], List.of(ClamdPrivateAddressPolicy.parse("10.0.0.0/8"))));
  }

  @Test
  void rejectsMissingOrOversizedConfigurationCollections() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ClamdScannerProperties(
                    "clamd", 3310, null, Duration.ofSeconds(30), Duration.ofHours(48), 2, true));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ClamdScannerProperties(
                    "clamd",
                    3310,
                    List.of(),
                    Duration.ofSeconds(30),
                    Duration.ofHours(48),
                    2,
                    true));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ClamdScannerProperties(
                    "clamd",
                    3310,
                    java.util.Collections.nCopies(9, "10.0.0.0/8"),
                    Duration.ofSeconds(30),
                    Duration.ofHours(48),
                    2,
                    true));
  }
}
