package com.cadence.domain;

import java.util.Objects;

/** Trusted adapter evidence, never a client DTO or a claim inferred from generated content. */
public record GenerationUsage(Origin origin, String unit, int scale, long amount, String evidence) {
  public enum Origin {
    SYNTHETIC,
    MEASURED
  }

  public GenerationUsage {
    Objects.requireNonNull(origin);
    if (unit == null
        || !unit.matches("[A-Z_]{1,24}")
        || scale < 0
        || scale > 9
        || amount < 0
        || evidence == null
        || !evidence.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid usage evidence");
  }
}
