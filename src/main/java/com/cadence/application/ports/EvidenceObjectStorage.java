package com.cadence.application.ports;

public interface EvidenceObjectStorage {

  int MAX_QUARANTINE_BYTES = 10 * 1024 * 1024;

  /**
   * Store complete bytes privately and return an internal quarantine reference. The caller supplies
   * a server-generated deterministic key; concurrent writes of identical bytes to that key must be
   * safe and readers must never observe a partial write. On success the reference must be exactly
   * {@code "quarantine:" + objectKey}; a caller verifies this before persisting it. A successful
   * return is not a database commit: callers must not delete a shared key merely because their own
   * registration subsequently fails.
   */
  String storeQuarantined(String objectKey, byte[] content);

  /**
   * Read an internal reference selected from authorized evidence, never a client-supplied path.
   * Implementations must bound reads to maxBytes (1..MAX_QUARANTINE_BYTES), reject empty content
   * and return only complete objects. They must not resolve HTTP URLs or return raw provider
   * failures.
   */
  byte[] readQuarantined(String reference, int maxBytes);
}
