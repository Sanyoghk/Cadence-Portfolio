package com.cadence.domain;

import java.util.*;

/** Immutable complete creator-edited identity; no JPA/provider/HTTP state. */
public record BrandIdentityContent(
    Palette palette,
    List<String> fonts,
    List<String> voiceUp,
    List<String> voiceDown,
    String sampleCaption) {
  public record Palette(
      String primary, String secondary, String accent, String neutral, String background) {
    public Palette {
      primary = color(primary);
      secondary = color(secondary);
      accent = color(accent);
      neutral = color(neutral);
      background = color(background);
    }

    private static String color(String value) {
      if (value == null || !value.matches("#[0-9A-Fa-f]{6}"))
        throw new IllegalArgumentException("Invalid identity color");
      return value.toUpperCase(Locale.ROOT);
    }
  }

  public BrandIdentityContent {
    if (palette == null) throw new IllegalArgumentException("Identity palette is required");
    fonts = items(fonts, 5);
    voiceUp = items(voiceUp, 10);
    voiceDown = items(voiceDown, 10);
    sampleCaption = text(sampleCaption, 1000);
  }

  private static List<String> items(List<String> values, int max) {
    if (values == null || values.isEmpty() || values.size() > max)
      throw new IllegalArgumentException("Invalid identity collection");
    var result = values.stream().map(v -> text(v, 100)).toList();
    if (result.stream().map(v -> v.toLowerCase(Locale.ROOT)).distinct().count() != result.size())
      throw new IllegalArgumentException("Duplicate identity values");
    return result;
  }

  private static String text(String value, int max) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > max
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid identity text");
    return value.strip();
  }

  /** Same length-prefixed content format as analysis receipts; metadata is excluded. */
  public String checksum() {
    try {
      var bytes = new java.io.ByteArrayOutputStream();
      var output = new java.io.DataOutputStream(bytes);
      for (var value :
          List.of(
              palette.primary(),
              palette.secondary(),
              palette.accent(),
              palette.neutral(),
              palette.background())) write(output, value);
      for (var list : List.of(fonts, voiceUp, voiceDown)) {
        output.writeInt(list.size());
        for (var value : list) write(output, value);
      }
      write(output, sampleCaption);
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Identity checksum unavailable");
    }
  }

  private static void write(java.io.DataOutputStream output, String value)
      throws java.io.IOException {
    var bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    output.writeInt(bytes.length);
    output.write(bytes);
  }
}
