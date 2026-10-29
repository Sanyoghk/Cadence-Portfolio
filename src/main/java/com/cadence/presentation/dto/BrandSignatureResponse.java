package com.cadence.presentation.dto;

import com.cadence.domain.BrandSignature;
import java.util.List;

public record BrandSignatureResponse(
    ColorPaletteResponse palette,
    List<String> fonts,
    List<String> voiceUp,
    List<String> voiceDown,
    String sampleCaption) {

  public static BrandSignatureResponse from(BrandSignature signature) {
    if (signature == null) {
      return null;
    }
    return new BrandSignatureResponse(
        ColorPaletteResponse.from(signature.getPalette()),
        List.copyOf(signature.getFonts()),
        List.copyOf(signature.getVoiceUp()),
        List.copyOf(signature.getVoiceDown()),
        signature.getSampleCaption());
  }
}
