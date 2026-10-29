package com.cadence.infrastructure.ingest;

import static com.cadence.application.ports.UrlFetchFailure.Code.UNSAFE_TARGET;

import com.cadence.application.ports.UrlFetchFailure;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

/** Conservative public-unicast policy, also applied to every DNS answer and redirect. */
final class PublicUrlPolicy {
  private PublicUrlPolicy() {}

  static URI parse(String source) {
    try {
      URI uri = URI.create(source);
      String scheme = uri.getScheme();
      String host = host(uri).toLowerCase(Locale.ROOT);
      if (source.length() > 4096
          || !("http".equals(scheme) || "https".equals(scheme))
          || uri.getUserInfo() != null
          || uri.getFragment() != null
          || host.contains("%")
          || host.endsWith(".")
          || host.equals("localhost")
          || host.endsWith(".localhost")
          || host.endsWith(".local")
          || host.equals("metadata.google.internal")
          || uri.getPort() != -1 && uri.getPort() != (scheme.equals("https") ? 443 : 80)) {
        throw new UrlFetchFailure(UNSAFE_TARGET);
      }
      return URI.create(uri.toASCIIString());
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new UrlFetchFailure(UNSAFE_TARGET);
    }
  }

  static String host(URI uri) {
    String host = uri.getHost();
    if (host == null || host.isBlank()) throw new UrlFetchFailure(UNSAFE_TARGET);
    return host.startsWith("[") ? host.substring(1, host.length() - 1) : host;
  }

  static void requirePublic(InetAddress address) {
    byte[] b = address.getAddress();
    boolean denied =
        address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress();
    int a = b[0] & 255, c = b[1] & 255;
    if (b.length == 4) {
      int d = b[2] & 255;
      denied |=
          a == 0
              || a == 10
              || a == 127
              || a >= 224
              || a == 100 && c >= 64 && c <= 127
              || a == 169 && c == 254
              || a == 172 && c >= 16 && c <= 31
              || a == 192 && (c == 168 || c == 0 || c == 2 || c == 88 && d == 99)
              || a == 198 && (c == 18 || c == 19 || c == 51 && d == 100)
              || a == 203 && c == 0 && d == 113
              || address.getHostAddress().equals("168.63.129.16");
    } else {
      // Only global unicast; exclude transition, benchmarking, documentation and special ranges.
      denied |=
          (a & 0xe0) != 0x20
              || a == 0x20 && c == 0x02
              || a == 0x20
                  && c == 0x01
                  && ((b[2] & 255) < 2 || (b[2] & 255) == 0x0d && (b[3] & 255) == 0xb8)
              || a == 0x3f && c == 0xff;
    }
    if (denied) throw new UrlFetchFailure(UNSAFE_TARGET);
  }
}
