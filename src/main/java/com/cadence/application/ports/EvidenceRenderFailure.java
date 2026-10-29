package com.cadence.application.ports;

public final class EvidenceRenderFailure extends RuntimeException {
  public enum Code {
    INVALID_CONTENT,
    UNAVAILABLE,
    INVALID_RESPONSE
  }

  private final Code code;

  public EvidenceRenderFailure(Code code) {
    super("Evidence rendering failed");
    this.code = java.util.Objects.requireNonNull(code);
  }

  public Code code() {
    return code;
  }
}
