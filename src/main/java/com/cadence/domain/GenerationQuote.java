package com.cadence.domain;

/** Provider-neutral upper estimate; construction does not authorize live admission. */
public record GenerationQuote(
    Namespace namespace, String unit, int scale, long upper, String version) {
  public enum Namespace {
    SYNTHETIC,
    LIVE
  }

  public GenerationQuote {
    if (namespace == null
        || unit == null
        || !unit.matches("[A-Z_]{1,24}")
        || scale < 0
        || scale > 9
        || upper < 1
        || upper > 1_000_000_000L
        || version == null
        || !version.matches("[A-Za-z0-9._:-]{1,64}"))
      throw new IllegalArgumentException("Invalid generation quote");
    if (namespace == Namespace.SYNTHETIC
            && (!unit.equals("SYNTHETIC_CREDIT")
                || scale != 0
                || !version.equals("synthetic-fixed-v1"))
        || namespace == Namespace.LIVE && unit.equals("SYNTHETIC_CREDIT"))
      throw new IllegalArgumentException("Quote namespace mismatch");
  }

  public static GenerationQuote synthetic(long upper) {
    return new GenerationQuote(
        Namespace.SYNTHETIC, "SYNTHETIC_CREDIT", 0, upper, "synthetic-fixed-v1");
  }
}
