package com.cadence.application.ports;

import com.cadence.domain.BrandSignature;
import com.cadence.domain.ColorPalette;
import java.util.List;

/**
 * Validated, bounded output. Raw provider JSON, usage diagnostics and secrets stay in the adapter.
 */
public record BrandAnalysisResult(
    Palette palette,
    List<String> fonts,
    List<String> voiceUp,
    List<String> voiceDown,
    String sampleCaption,
    List<Warning> warnings,
    String provider,
    String modelVersion,
    String promptVersion,
    List<AnalysisCitation> citations) {
  public BrandAnalysisResult(
      Palette palette,
      List<String> fonts,
      List<String> voiceUp,
      List<String> voiceDown,
      String sampleCaption,
      List<Warning> warnings,
      String provider,
      String modelVersion,
      String promptVersion) {
    this(
        palette,
        fonts,
        voiceUp,
        voiceDown,
        sampleCaption,
        warnings,
        provider,
        modelVersion,
        promptVersion,
        List.of());
  }

  public enum Warning {
    DEMONSTRATION_ONLY,
    DECLARED_FIELDS_ONLY,
    HUMAN_REVIEW_REQUIRED
  }

  public record Palette(
      String primary, String secondary, String accent, String neutral, String background) {
    public Palette {
      for (var value : new String[] {primary, secondary, accent, neutral, background}) {
        if (value == null || !value.matches("#[0-9A-Fa-f]{6}"))
          throw new IllegalArgumentException("Invalid analysis color");
      }
    }
  }

  public BrandAnalysisResult {
    if (palette == null) throw new IllegalArgumentException("Missing analysis palette");
    fonts = values(fonts, 5, 100);
    voiceUp = values(voiceUp, 10, 100);
    voiceDown = values(voiceDown, 10, 100);
    sampleCaption = text(sampleCaption, 1000);
    if (warnings == null || warnings.size() > 10)
      throw new IllegalArgumentException("Invalid analysis warnings");
    warnings = List.copyOf(warnings);
    provider = identifier(provider);
    modelVersion = identifier(modelVersion);
    promptVersion = identifier(promptVersion);
    if (citations == null || citations.size() > 10)
      throw new IllegalArgumentException("Invalid analysis citations");
    citations = List.copyOf(citations);
  }

  private static String identifier(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
      throw new IllegalArgumentException("Invalid analysis provenance");
    return value;
  }

  private static List<String> values(List<String> values, int maxItems, int maxLength) {
    if (values == null || values.isEmpty() || values.size() > maxItems)
      throw new IllegalArgumentException("Invalid analysis list");
    return values.stream().map(value -> text(value, maxLength)).toList();
  }

  private static String text(String value, int maxLength) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > maxLength
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid analysis text");
    return value.strip();
  }

  /** Canonical length-prefixed fields prevent ambiguous list/string concatenation. */
  public String contentChecksum() {
    try {
      var bytes = new java.io.ByteArrayOutputStream();
      var output = new java.io.DataOutputStream(bytes);
      for (var value :
          new String[] {
            palette.primary(),
            palette.secondary(),
            palette.accent(),
            palette.neutral(),
            palette.background()
          }) write(output, value);
      for (var list : List.of(fonts, voiceUp, voiceDown)) {
        output.writeInt(list.size());
        for (var value : list) write(output, value);
      }
      write(output, sampleCaption);
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
    } catch (java.io.IOException | java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("Analysis checksum unavailable");
    }
  }

  private static void write(java.io.DataOutputStream output, String value)
      throws java.io.IOException {
    var bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    output.writeInt(bytes.length);
    output.write(bytes);
  }

  public BrandSignature toSignature() {
    return new BrandSignature(
        new ColorPalette(
            palette.primary(),
            palette.secondary(),
            palette.accent(),
            palette.neutral(),
            palette.background()),
        fonts,
        voiceUp,
        voiceDown,
        sampleCaption);
  }
}
