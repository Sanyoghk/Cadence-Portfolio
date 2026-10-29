package com.cadence.application.ports;

/** Only fixed codes cross the provider boundary; never retain provider messages or URLs. */
public final class UrlFetchFailure extends RuntimeException {
  public enum Code {
    UNSAFE_TARGET(false),
    INVALID_RESPONSE(false),
    UNSUPPORTED_CONTENT(false),
    RESPONSE_TOO_LARGE(false),
    TOO_MANY_REDIRECTS(false),
    REMOTE_REJECTED(false),
    TLS_REJECTED(false),
    TIMEOUT(true),
    UNAVAILABLE(true);

    private final boolean retryable;

    Code(boolean retryable) {
      this.retryable = retryable;
    }
  }

  private final Code code;

  public UrlFetchFailure(Code code) {
    super("URL fetch failed: " + code.name());
    this.code = code;
  }

  public Code code() {
    return code;
  }

  public boolean retryable() {
    return code.retryable;
  }
}
