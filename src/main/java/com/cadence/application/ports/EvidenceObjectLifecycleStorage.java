package com.cadence.application.ports;

/** Private cleanup capability; never expose it through a client-selected object reference. */
public interface EvidenceObjectLifecycleStorage {

  /**
   * Idempotently delete a complete quarantine object. The application must first durably claim the
   * unique generation and prevent registration/writes to that generation; deleting a deterministic
   * shared key based only on its age is unsafe. A failure must leave the claim retryable.
   */
  void deleteQuarantined(String reference);
}
