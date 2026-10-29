package com.cadence.application.ports;

/**
 * The adapter receives immutable declared brand fields, never JPA entities or quarantined bytes.
 */
public interface BrandAnalysisPort {
  BrandAnalysisResult analyze(BrandAnalysisCommand command);
}
