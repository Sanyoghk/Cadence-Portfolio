package com.cadence.infrastructure.storage;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "cadence.evidence.renderer")
public record RendererProperties(
    String host,
    @DefaultValue("8081") int port,
    List<String> allowedCidrs,
    String token,
    @DefaultValue("PT20S") Duration deadline,
    @DefaultValue("false") boolean privateChannelAcknowledged) {
  public RendererProperties {
    // Reuse the same literal/private endpoint validation, with a fixed single operation slot.
    new ClamdScannerProperties(
        host, port, allowedCidrs, deadline, Duration.ofHours(48), 1, privateChannelAcknowledged);
    if (token == null
        || !token.matches("[A-Za-z0-9_-]{32,128}")
        || deadline.compareTo(Duration.ofSeconds(30)) > 0)
      throw new IllegalArgumentException("Invalid private renderer configuration");
    allowedCidrs = List.copyOf(allowedCidrs);
  }

  // A secret-bearing configuration value must never reveal the token in diagnostics.
  @Override
  public String toString() {
    return "RendererProperties[private configuration]";
  }
}
