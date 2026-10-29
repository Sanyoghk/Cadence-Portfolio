package com.cadence.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cadence.security.clerk")
public record ClerkSecurityProperties(
    String issuer, String jwksUri, String audience, List<String> authorizedParties) {

  public ClerkSecurityProperties {
    authorizedParties = authorizedParties == null ? List.of() : List.copyOf(authorizedParties);
  }
}
