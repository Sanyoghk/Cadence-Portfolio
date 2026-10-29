package com.cadence.domain;

import jakarta.persistence.*;

@Embeddable
public class ColorPalette {

  @Column(name = "primary_color")
  private String primary;

  @Column(name = "secondary_color")
  private String secondary;

  @Column(name = "accent_color")
  private String accent;

  @Column(name = "neutral_color")
  private String neutral;

  @Column(name = "background_color")
  private String background;

  protected ColorPalette() {}

  public ColorPalette(
      String primary, String secondary, String accent, String neutral, String background) {
    this.primary = primary;
    this.secondary = secondary;
    this.accent = accent;
    this.neutral = neutral;
    this.background = background;
  }

  public String getPrimary() {
    return primary;
  }

  public void setPrimary(String primary) {
    this.primary = primary;
  }

  public String getSecondary() {
    return secondary;
  }

  public void setSecondary(String secondary) {
    this.secondary = secondary;
  }

  public String getAccent() {
    return accent;
  }

  public void setAccent(String accent) {
    this.accent = accent;
  }

  public String getNeutral() {
    return neutral;
  }

  public void setNeutral(String neutral) {
    this.neutral = neutral;
  }

  public String getBackground() {
    return background;
  }

  public void setBackground(String background) {
    this.background = background;
  }
}
