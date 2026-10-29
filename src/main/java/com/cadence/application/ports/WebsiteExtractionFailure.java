package com.cadence.application.ports;

/** Permanent input failure; never contains remote text or parser diagnostics. */
public final class WebsiteExtractionFailure extends RuntimeException {
  public WebsiteExtractionFailure() {
    super("Website metadata could not be extracted");
  }
}
