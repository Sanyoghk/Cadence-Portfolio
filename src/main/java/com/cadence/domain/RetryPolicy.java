package com.cadence.domain;

import java.time.Duration;

public record RetryPolicy(Duration initialDelay, Duration maximumDelay) {

  public RetryPolicy {
    if (initialDelay == null || initialDelay.isZero() || initialDelay.isNegative()) {
      throw new IllegalArgumentException("initialDelay must be positive");
    }
    if (maximumDelay == null || maximumDelay.compareTo(initialDelay) < 0) {
      throw new IllegalArgumentException("maximumDelay must not be shorter than initialDelay");
    }
  }

  public Duration delayForAttempt(int attempt) {
    if (attempt < 1) {
      throw new IllegalArgumentException("attempt must be at least 1");
    }
    long multiplier = 1L << Math.min(attempt - 1, 30);
    try {
      Duration delay = initialDelay.multipliedBy(multiplier);
      return delay.compareTo(maximumDelay) > 0 ? maximumDelay : delay;
    } catch (ArithmeticException ignored) {
      return maximumDelay;
    }
  }
}
