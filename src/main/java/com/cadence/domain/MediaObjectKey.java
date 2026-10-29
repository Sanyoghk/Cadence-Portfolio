package com.cadence.domain;

import java.security.*;
import java.util.*;

/** Constructed from persisted server ownership and immutable bytes metadata. */
public record MediaObjectKey(UUID brandId, UUID assetId, String checksum, MediaFormat format) {
  public static final int MAX_BYTES = 10 * 1024 * 1024;

  public MediaObjectKey {
    if (brandId == null
        || assetId == null
        || checksum == null
        || !checksum.matches("[a-f0-9]{64}")
        || format == null) throw new IllegalArgumentException("Invalid private media key");
  }

  public String key() {
    return "media/" + brandId + "/" + assetId + "/" + checksum + "." + format.extension();
  }

  public void verify(byte[] content) {
    if (content == null
        || content.length < 1
        || content.length > MAX_BYTES
        || !checksum.equals(hash(content)))
      throw new IllegalArgumentException("Invalid private media bytes");
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA256 unavailable");
    }
  }
}
