package com.cadence.domain;

/** Reject unknown fields on the typed v2 external value contract. */
public interface StrictPlaybookValue {
  @com.fasterxml.jackson.annotation.JsonAnySetter
  default void unknown(String key, Object value) {
    throw new IllegalArgumentException("Unknown Playbook field");
  }
}
