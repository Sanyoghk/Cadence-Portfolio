package com.cadence.application.exceptions;

public final class GenerationBudgetExceededException extends RuntimeException {
  public GenerationBudgetExceededException() {
    super("Generation budget unavailable");
  }
}
