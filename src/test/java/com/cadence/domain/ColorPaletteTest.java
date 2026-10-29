package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ColorPaletteTest {

  @Test
  void shouldCreateColorPaletteWithValidFields() {
    ColorPalette palette = new ColorPalette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5");

    assertEquals("#000000", palette.getPrimary());
    assertEquals("#FFFFFF", palette.getSecondary());
    assertEquals("#FF0000", palette.getAccent());
    assertEquals("#808080", palette.getNeutral());
    assertEquals("#F5F5F5", palette.getBackground());
  }

  @Test
  void shouldUpdateColorPaletteFields() {
    ColorPalette palette = new ColorPalette();

    palette.setPrimary("#111111");
    palette.setSecondary("#222222");
    palette.setAccent("#333333");
    palette.setNeutral("#444444");
    palette.setBackground("#555555");

    assertEquals("#111111", palette.getPrimary());
    assertEquals("#222222", palette.getSecondary());
    assertEquals("#333333", palette.getAccent());
    assertEquals("#444444", palette.getNeutral());
    assertEquals("#555555", palette.getBackground());
  }
}
