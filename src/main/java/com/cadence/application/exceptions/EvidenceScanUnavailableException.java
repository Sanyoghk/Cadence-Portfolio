package com.cadence.application.exceptions;

/** Safe application failure: never retain provider messages or causes containing sensitive data. */
public class EvidenceScanUnavailableException extends RuntimeException {
  public EvidenceScanUnavailableException() {
    super("Evidence file could not be scanned; try again later");
  }
}
