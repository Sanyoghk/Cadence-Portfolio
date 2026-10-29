package com.cadence.application.ports;

/** Provider-neutral bounded I/O port. No persistence, authorization or tool execution authority. */
public interface ContentGenerationService {
  ContentGenerationResult generate(ContentGenerationCommand command);
}
