package com.cadence.application.ports;

/** Provider-neutral generation port. Implementations must bound I/O; output is always a draft. */
public interface StrategyGenerationService {
  StrategyGenerationResult generate(StrategyGenerationCommand command);
}
