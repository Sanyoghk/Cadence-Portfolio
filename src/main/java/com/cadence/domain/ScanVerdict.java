package com.cadence.domain;

/** Stored observation, not content approval. */
public enum ScanVerdict {
  CLEAN,
  INFECTED,
  UNAVAILABLE,
  ERROR
}
