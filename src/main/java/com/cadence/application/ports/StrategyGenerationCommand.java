package com.cadence.application.ports;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StrategyGenerationCommand(
    UUID generationId,
    long brandVersion,
    long workspaceVersion,
    String brandName,
    String industry,
    Signature signature,
    Instant startedAt) {
  public StrategyGenerationCommand {
    if (generationId == null
        || brandVersion < 0
        || workspaceVersion < 0
        || signature == null
        || startedAt == null) throw new IllegalArgumentException("Invalid strategy source");
    brandName = text(brandName, 255);
    industry = text(industry, 255);
  }

  public Instant deadline() {
    return startedAt.plusSeconds(60);
  }

  public record Signature(
      BrandAnalysisResult.Palette palette,
      List<String> fonts,
      List<String> voiceUp,
      List<String> voiceDown,
      String sampleCaption) {
    public Signature {
      if (palette == null) throw new IllegalArgumentException("Missing signature palette");
      fonts = values(fonts, 5);
      voiceUp = values(voiceUp, 10);
      voiceDown = values(voiceDown, 10);
      sampleCaption = text(sampleCaption, 1000);
    }
  }

  private static List<String> values(List<String> values, int maximum) {
    if (values == null || values.isEmpty() || values.size() > maximum)
      throw new IllegalArgumentException("Invalid signature list");
    return values.stream().map(v -> text(v, 100)).toList();
  }

  private static String text(String value, int maximum) {
    if (value == null
        || value.isBlank()
        || value.strip().length() > maximum
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid strategy source text");
    return value.strip();
  }
}
