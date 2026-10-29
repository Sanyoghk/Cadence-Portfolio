package com.cadence.presentation.dto;

import com.cadence.application.ports.*;
import com.cadence.application.usecases.AnalyzeEvidenceUseCase;
import java.util.*;

public record EvidenceAnalysisResponse(
    UUID analysisId,
    BrandSignatureResponse signature,
    List<AnalysisCitation> citations,
    List<BrandAnalysisResult.Warning> warnings,
    String provider,
    String modelVersion,
    String promptVersion) {
  public static EvidenceAnalysisResponse from(AnalyzeEvidenceUseCase.Outcome outcome) {
    var r = outcome.result();
    return new EvidenceAnalysisResponse(
        outcome.analysisId(),
        BrandSignatureResponse.from(outcome.signature()),
        r.citations(),
        r.warnings(),
        r.provider(),
        r.modelVersion(),
        r.promptVersion());
  }
}
