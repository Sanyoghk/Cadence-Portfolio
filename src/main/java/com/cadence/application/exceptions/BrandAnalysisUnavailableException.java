package com.cadence.application.exceptions;

public final class BrandAnalysisUnavailableException extends RuntimeException {
  private final boolean invalidOutput;

  public BrandAnalysisUnavailableException() {
    this(false);
  }

  private BrandAnalysisUnavailableException(boolean invalidOutput) {
    super("Brand analysis is unavailable");
    this.invalidOutput = invalidOutput;
  }

  public static BrandAnalysisUnavailableException invalidOutput() {
    return new BrandAnalysisUnavailableException(true);
  }

  public boolean isInvalidOutput() {
    return invalidOutput;
  }
}
