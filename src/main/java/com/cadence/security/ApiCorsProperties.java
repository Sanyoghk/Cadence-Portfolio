package com.cadence.security;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Explicit browser origins; CORS never supplies authentication or resource authorization. */
@ConfigurationProperties(prefix = "cadence.security.cors")
public record ApiCorsProperties(List<String> allowedOrigins) {
  public ApiCorsProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    if (allowedOrigins.size() > 10
        || allowedOrigins.stream().distinct().count() != allowedOrigins.size()) {
      throw new IllegalArgumentException("CORS requires at most ten distinct explicit origins");
    }
    for (String origin : allowedOrigins) {
      try {
        URI uri = URI.create(origin);
        boolean loopback = List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
        if (origin.length() > 255
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getQuery() != null
            || uri.getFragment() != null
            || !uri.getPath().isEmpty()
            || uri.getPort() > 65535
            || uri.getPort() == 0
            || origin.contains("*")
            || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && loopback))) {
          throw new IllegalArgumentException("Invalid CORS origin");
        }
      } catch (RuntimeException exception) {
        throw new IllegalArgumentException("CORS requires explicit HTTPS origins or HTTP loopback");
      }
    }
  }
}
