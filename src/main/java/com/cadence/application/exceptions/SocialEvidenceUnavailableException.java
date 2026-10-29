package com.cadence.application.exceptions;

/** Never attach raw provider responses, URLs or credential values as a cause. */
public final class SocialEvidenceUnavailableException extends RuntimeException {
  public SocialEvidenceUnavailableException() {
    super("Approved social evidence source is unavailable");
  }
}
