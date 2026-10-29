package com.cadence.domain;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/** Reported interval counts, not derived rates or proof of platform publication. */
public record MetricRow(
    UUID postId,
    PlaybookContent.Channel platform,
    Instant windowFrom,
    Instant windowUntil,
    Instant observedAt,
    Long reach,
    Long engagement,
    Long saves,
    Long signups,
    Long profileToSiteClicks,
    UUID correctionOf) {
  public static final long MAX_COUNT = 1_000_000_000_000L;

  public MetricRow {
    if (postId == null
        || platform == null
        || windowFrom == null
        || windowUntil == null
        || observedAt == null
        || windowFrom.isBefore(Instant.parse("2000-01-01T00:00:00Z"))
        || !windowFrom.isBefore(windowUntil)
        || windowUntil.isAfter(observedAt)
        || observedAt.isAfter(Instant.parse("2200-01-01T00:00:00Z"))
        || Duration.between(windowFrom, windowUntil).compareTo(Duration.ofDays(366)) > 0
        || windowFrom.getNano() % 1000 != 0
        || windowUntil.getNano() % 1000 != 0
        || observedAt.getNano() % 1000 != 0)
      throw new IllegalArgumentException("Invalid metric identity or UTC interval");
    boolean present = false;
    for (var value : new Long[] {reach, engagement, saves, signups, profileToSiteClicks}) {
      if (value != null) {
        present = true;
        if (value < 0 || value > MAX_COUNT)
          throw new IllegalArgumentException("Invalid metric count");
      }
    }
    if (!present) throw new IllegalArgumentException("At least one reported count required");
  }

  public String naturalKey() {
    return postId + ":" + observedAt;
  }

  public String digest() {
    return sha(
        ("metric-interval-v1|"
                + postId
                + "|"
                + platform
                + "|"
                + micros(windowFrom)
                + "|"
                + micros(windowUntil)
                + "|"
                + micros(observedAt)
                + "|"
                + reach
                + "|"
                + engagement
                + "|"
                + saves
                + "|"
                + signups
                + "|"
                + profileToSiteClicks)
            .getBytes(StandardCharsets.UTF_8));
  }

  private static long micros(Instant t) {
    return t.getEpochSecond() * 1_000_000L + t.getNano() / 1000;
  }

  public static String sha(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
