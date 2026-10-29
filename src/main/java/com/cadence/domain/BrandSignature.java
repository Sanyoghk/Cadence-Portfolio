package com.cadence.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import java.util.ArrayList;
import java.util.List;

@Embeddable
public class BrandSignature {

  @Embedded private ColorPalette palette;

  @ElementCollection
  @CollectionTable(name = "brand_signature_fonts", joinColumns = @JoinColumn(name = "brand_id"))
  @Column(name = "font")
  @OrderColumn(name = "font_order")
  private List<String> fonts = new ArrayList<>();

  @ElementCollection
  @CollectionTable(name = "brand_signature_voice_up", joinColumns = @JoinColumn(name = "brand_id"))
  @Column(name = "voice_up")
  @OrderColumn(name = "voice_up_order")
  private List<String> voiceUp = new ArrayList<>();

  @ElementCollection
  @CollectionTable(
      name = "brand_signature_voice_down",
      joinColumns = @JoinColumn(name = "brand_id"))
  @Column(name = "voice_down")
  @OrderColumn(name = "voice_down_order")
  private List<String> voiceDown = new ArrayList<>();

  @Column(length = 1000)
  private String sampleCaption;

  public BrandSignature() {}

  public BrandSignature(
      ColorPalette palette,
      List<String> fonts,
      List<String> voiceUp,
      List<String> voiceDown,
      String sampleCaption) {
    this.palette = palette;
    this.fonts = fonts != null ? new ArrayList<>(fonts) : new ArrayList<>();

    this.voiceUp = voiceUp != null ? new ArrayList<>(voiceUp) : new ArrayList<>();

    this.voiceDown = voiceDown != null ? new ArrayList<>(voiceDown) : new ArrayList<>();
    this.sampleCaption = sampleCaption;
  }

  public ColorPalette getPalette() {
    return palette;
  }

  public void setPalette(ColorPalette palette) {
    this.palette = palette;
  }

  public List<String> getFonts() {
    return fonts;
  }

  public void setFonts(List<String> fonts) {
    this.fonts = fonts != null ? fonts : new ArrayList<>();
  }

  public List<String> getVoiceUp() {
    return voiceUp;
  }

  public void setVoiceUp(List<String> voiceUp) {
    this.voiceUp = voiceUp != null ? voiceUp : new ArrayList<>();
  }

  public List<String> getVoiceDown() {
    return voiceDown;
  }

  public void setVoiceDown(List<String> voiceDown) {
    this.voiceDown = voiceDown != null ? voiceDown : new ArrayList<>();
  }

  public String getSampleCaption() {
    return sampleCaption;
  }

  public void setSampleCaption(String sampleCaption) {
    this.sampleCaption = sampleCaption;
  }
}
