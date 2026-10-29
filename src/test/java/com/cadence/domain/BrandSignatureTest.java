package com.cadence.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrandSignatureTest {

  @Test
  void shouldCreateBrandSignatureWithValidFields() {

    ColorPalette palette = new ColorPalette("#000000", "#FFFFFF", "#FF0000", "#808080", "#F5F5F5");

    BrandSignature signature =
        new BrandSignature(
            palette,
            List.of("Inter", "Helvetica"),
            List.of("professional", "confident"),
            List.of("spammy", "aggressive"),
            "Example caption");

    assertEquals(palette, signature.getPalette());

    assertEquals(2, signature.getFonts().size());
    assertTrue(signature.getFonts().contains("Inter"));

    assertEquals(2, signature.getVoiceUp().size());
    assertTrue(signature.getVoiceUp().contains("professional"));

    assertEquals(2, signature.getVoiceDown().size());
    assertTrue(signature.getVoiceDown().contains("spammy"));

    assertEquals("Example caption", signature.getSampleCaption());
  }

  @Test
  void shouldUpdateBrandSignatureFields() {

    BrandSignature signature = new BrandSignature();

    ColorPalette palette = new ColorPalette("#111111", "#222222", "#333333", "#444444", "#555555");

    signature.setPalette(palette);
    signature.setFonts(List.of("Roboto"));
    signature.setVoiceUp(List.of("friendly"));
    signature.setVoiceDown(List.of("formal"));
    signature.setSampleCaption("New caption");

    assertEquals(palette, signature.getPalette());
    assertEquals(List.of("Roboto"), signature.getFonts());
    assertEquals(List.of("friendly"), signature.getVoiceUp());
    assertEquals(List.of("formal"), signature.getVoiceDown());
    assertEquals("New caption", signature.getSampleCaption());
  }

  @Test
  void shouldInitializeCollectionsWhenNullPassedToConstructor() {

    BrandSignature signature = new BrandSignature(null, null, null, null, null);

    assertNotNull(signature.getFonts());
    assertNotNull(signature.getVoiceUp());
    assertNotNull(signature.getVoiceDown());

    assertTrue(signature.getFonts().isEmpty());
    assertTrue(signature.getVoiceUp().isEmpty());
    assertTrue(signature.getVoiceDown().isEmpty());
  }
}
