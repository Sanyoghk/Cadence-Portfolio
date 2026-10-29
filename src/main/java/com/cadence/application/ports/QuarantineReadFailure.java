package com.cadence.application.ports;

/** Safe internal failure codes; no storage paths, content or provider exception causes. */
public final class QuarantineReadFailure extends RuntimeException {
  public enum Code {
    INVALID_REFERENCE,
    UNSAFE_PATH,
    NOT_FOUND,
    SIZE_LIMIT,
    INVALID_CONTENT,
    CHECKSUM_MISMATCH,
    UNAVAILABLE
  }

  private final Code code;

  public QuarantineReadFailure(Code code) {
    super("Quarantine read failed: " + java.util.Objects.requireNonNull(code).name());
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
