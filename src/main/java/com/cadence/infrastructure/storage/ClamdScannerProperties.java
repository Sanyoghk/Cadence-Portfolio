package com.cadence.infrastructure.storage;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Deployment-selected private clamd endpoint; none of these values originate in a request. */
@ConfigurationProperties(prefix = "cadence.evidence.clamd")
public record ClamdScannerProperties(
    String host,
    @DefaultValue("3310") int port,
    List<String> allowedCidrs,
    @DefaultValue("PT30S") Duration deadline,
    @DefaultValue("PT48H") Duration signatureMaxAge,
    @DefaultValue("2") int maxConcurrent,
    @DefaultValue("false") boolean privateChannelAcknowledged) {

  public ClamdScannerProperties {
    if (!validHost(host)
        || port < 1
        || port > 65535
        || allowedCidrs == null
        || allowedCidrs.isEmpty()
        || allowedCidrs.size() > 8
        || deadline == null
        || deadline.compareTo(Duration.ofMillis(100)) < 0
        || deadline.compareTo(Duration.ofSeconds(60)) > 0
        || signatureMaxAge == null
        || signatureMaxAge.compareTo(Duration.ofMinutes(1)) < 0
        || signatureMaxAge.compareTo(Duration.ofHours(72)) > 0
        || maxConcurrent < 1
        || maxConcurrent > 8
        || !privateChannelAcknowledged) {
      throw new IllegalArgumentException("Invalid private clamd configuration");
    }
    allowedCidrs = List.copyOf(allowedCidrs);
    allowedCidrs.forEach(ClamdPrivateAddressPolicy::parse);
  }

  private static boolean validHost(String host) {
    if (host == null || host.length() > 253) return false;
    if (host.contains(":")) {
      if (!host.matches("[0-9A-Fa-f:]+")) return false;
      try {
        return java.net.InetAddress.getByName(host).getAddress().length == 16;
      } catch (java.net.UnknownHostException exception) {
        return false;
      }
    }
    return host.matches(
        "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
  }
}
