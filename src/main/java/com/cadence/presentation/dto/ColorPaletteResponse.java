package com.cadence.presentation.dto;

import com.cadence.domain.ColorPalette;

public record ColorPaletteResponse(
    String primary, String secondary, String accent, String neutral, String background) {

  public static ColorPaletteResponse from(ColorPalette palette) {
    if (palette == null) {
      return null;
    }
    return new ColorPaletteResponse(
        palette.getPrimary(),
        palette.getSecondary(),
        palette.getAccent(),
        palette.getNeutral(),
        palette.getBackground());
  }
}
