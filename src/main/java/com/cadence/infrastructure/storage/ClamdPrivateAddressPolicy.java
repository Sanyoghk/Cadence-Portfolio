package com.cadence.infrastructure.storage;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Explicit CIDR allowlist constrained to private addresses, with no implicit metadata ranges. */
final class ClamdPrivateAddressPolicy {
  private static final List<String> PRIVATE_NETWORKS =
      List.of(
          "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "fc00::/7", "::1/128");

  record Cidr(byte[] network, int prefix) {
    boolean contains(InetAddress address) {
      return contains(address.getAddress());
    }

    boolean contains(byte[] bytes) {
      if (bytes.length != network.length) return false;
      for (int bit = 0; bit < prefix; bit++) {
        int mask = 1 << (7 - bit % 8);
        if ((network[bit / 8] & mask) != (bytes[bit / 8] & mask)) return false;
      }
      return true;
    }
  }

  static Cidr parse(String value) {
    Cidr cidr = literal(value);
    if (PRIVATE_NETWORKS.stream()
        .map(ClamdPrivateAddressPolicy::literal)
        .noneMatch(
            privateRange ->
                cidr.prefix >= privateRange.prefix && privateRange.contains(cidr.network))) {
      throw new IllegalArgumentException("Clamd allowlist must contain only private CIDRs");
    }
    return cidr;
  }

  static void requireAllowed(InetAddress[] addresses, List<Cidr> allowed) {
    if (addresses == null || addresses.length == 0 || addresses.length > 16)
      throw new IllegalArgumentException("Invalid private clamd address");
    for (InetAddress address : addresses) {
      if (address == null
          || address.isAnyLocalAddress()
          || address.isMulticastAddress()
          || address.isLinkLocalAddress()
          || allowed.stream().noneMatch(cidr -> cidr.contains(address))) {
        throw new IllegalArgumentException("Invalid private clamd address");
      }
    }
  }

  private static Cidr literal(String value) {
    if (value == null || value.length() > 64)
      throw new IllegalArgumentException("Invalid clamd CIDR");
    String[] parts = value.split("/", -1);
    if (parts.length != 2 || !parts[1].matches("[0-9]{1,3}"))
      throw new IllegalArgumentException("Invalid clamd CIDR");
    String address = parts[0];
    if (address.indexOf(':') >= 0) {
      if (!address.matches("[0-9A-Fa-f:]+"))
        throw new IllegalArgumentException("Invalid clamd CIDR");
    } else if (!address.matches("(?:0|[1-9][0-9]{0,2})(?:\\.(?:0|[1-9][0-9]{0,2})){3}")) {
      throw new IllegalArgumentException("Invalid clamd CIDR");
    }
    try {
      byte[] bytes = InetAddress.getByName(address).getAddress(); // Numeric literals only: no DNS.
      int prefix = Integer.parseInt(parts[1]);
      if (prefix < 0 || prefix > bytes.length * 8)
        throw new IllegalArgumentException("Invalid clamd CIDR");
      for (int bit = prefix; bit < bytes.length * 8; bit++)
        if ((bytes[bit / 8] & (1 << (7 - bit % 8))) != 0)
          throw new IllegalArgumentException("Clamd CIDR must use a network address");
      return new Cidr(bytes, prefix);
    } catch (UnknownHostException exception) {
      throw new IllegalArgumentException("Invalid clamd CIDR");
    }
  }
}
