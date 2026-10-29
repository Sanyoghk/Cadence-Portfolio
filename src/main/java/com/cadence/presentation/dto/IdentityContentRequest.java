package com.cadence.presentation.dto;

import com.cadence.domain.BrandIdentityContent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record IdentityContentRequest(
    @NotNull @Valid Palette palette,
    @NotNull @Size(min = 1, max = 5) List<@NotBlank @Size(max = 100) String> fonts,
    @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 100) String> voiceUp,
    @NotNull @Size(min = 1, max = 10) List<@NotBlank @Size(max = 100) String> voiceDown,
    @NotBlank @Size(max = 1000) String sampleCaption) {
  public record Palette(
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String primary,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String secondary,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String accent,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String neutral,
      @NotNull @Pattern(regexp = "#[0-9a-fA-F]{6}") String background) {}

  public BrandIdentityContent toContent() {
    return new BrandIdentityContent(
        new BrandIdentityContent.Palette(
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
