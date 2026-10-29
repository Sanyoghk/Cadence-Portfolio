package com.cadence.domain;

/** Validated, provider-neutral identity of the engine and signature set used for one scan. */
public record ScanProvenance(String engine, String signatureVersion) {
  private static final int MAX_IDENTIFIER_LENGTH = 100;
  private static final String IDENTIFIER_PATTERN = "\\A[A-Za-z0-9][A-Za-z0-9._:+/@ -]{0,99}\\z";

  public ScanProvenance {
    if (!valid(engine) || !valid(signatureVersion))
      throw new IllegalArgumentException("Invalid scan provenance");
  }

  private static boolean valid(String value) {
    return value != null
        && value.length() <= MAX_IDENTIFIER_LENGTH
        && value.matches(IDENTIFIER_PATTERN);
  }
}
