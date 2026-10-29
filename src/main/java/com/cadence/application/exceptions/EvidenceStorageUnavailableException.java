package com.cadence.application.exceptions;

public class EvidenceStorageUnavailableException extends RuntimeException {
  public EvidenceStorageUnavailableException() {
    super("Evidence file could not be stored; try again later");
  }
}
