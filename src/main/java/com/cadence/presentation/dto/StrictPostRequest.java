package com.cadence.presentation.dto;

/** Explicit external allowlist: unknown Post fields are rejected at any nesting level. */
public interface StrictPostRequest {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  default void unknown(String key, Object value) {
    throw new IllegalArgumentException("Unknown Post field");
  }
}
