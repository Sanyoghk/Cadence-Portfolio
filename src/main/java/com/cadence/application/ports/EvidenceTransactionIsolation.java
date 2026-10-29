package com.cadence.application.ports;

/** Rejects transaction contexts whose snapshot semantics cannot safely enforce evidence quotas. */
public interface EvidenceTransactionIsolation {
  void requireReadCommitted();
}
