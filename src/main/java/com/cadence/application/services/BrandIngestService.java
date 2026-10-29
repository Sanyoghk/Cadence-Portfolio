package com.cadence.application.services;

import com.cadence.application.exceptions.BrandAnalysisUnavailableException;
import com.cadence.application.ports.*;
import org.springframework.stereotype.Service;

@Service
public class BrandIngestService {
  private final BrandAnalysisPort analyzer;

  public BrandIngestService(BrandAnalysisPort analyzer) {
    this.analyzer = analyzer;
  }

  public BrandAnalysisResult analyze(BrandAnalysisCommand command) {
    final BrandAnalysisResult result;
    try {
      result = analyzer.analyze(command);
    } catch (RuntimeException failure) {
      throw new BrandAnalysisUnavailableException();
    }
    try {
      if (result == null) throw new IllegalArgumentException();
      command.validateCitations(result.citations());
      if (!command.sources().isEmpty()
          && (result.warnings().contains(BrandAnalysisResult.Warning.DECLARED_FIELDS_ONLY)
              || !result.warnings().contains(BrandAnalysisResult.Warning.HUMAN_REVIEW_REQUIRED)))
        throw new IllegalArgumentException();
      return result;
    } catch (RuntimeException failure) {
      throw BrandAnalysisUnavailableException.invalidOutput();
    }
  }
}
