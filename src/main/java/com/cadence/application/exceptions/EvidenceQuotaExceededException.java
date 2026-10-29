package com.cadence.application.exceptions;

public class EvidenceQuotaExceededException extends IllegalStateException {
  public EvidenceQuotaExceededException() {
    super("Workspace evidence quota exceeded");
  }
}
