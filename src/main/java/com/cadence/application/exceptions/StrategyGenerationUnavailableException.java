package com.cadence.application.exceptions;

public class StrategyGenerationUnavailableException extends RuntimeException {
  public StrategyGenerationUnavailableException() {
    super("Strategy generation is unavailable");
  }
}
