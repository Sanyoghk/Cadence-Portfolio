package com.cadence.domain;

/** Untrusted plain text, never safe HTML or approved AI instructions. */
public record WebsiteMetadata(
    String title, String description, String text, String warnings, String version) {
  public WebsiteMetadata {
    validate(title, 300);
    validate(description, 2000);
    validate(text, 16000);
    validate(warnings, 500);
    validate(version, 32);
    if (version.isBlank()) throw new IllegalArgumentException("Extraction version is required");
  }

  private static void validate(String value, int limit) {
    if (value == null
        || value.length() > limit
        || value.codePoints().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("Invalid website metadata");
  }
}
