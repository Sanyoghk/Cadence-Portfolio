package com.cadence.presentation.dto;

import com.cadence.application.usecases.RegisterBrandEvidenceUseCase.Registration;
import com.cadence.domain.JobStatus;
import java.util.UUID;

public record EvidenceRegistrationResponse(
    BrandEvidenceResponse evidence, UUID jobId, JobStatus jobStatus) {

  public static EvidenceRegistrationResponse from(Registration registration) {
    return new EvidenceRegistrationResponse(
        BrandEvidenceResponse.from(registration.evidence()),
        registration.job().getId(),
        registration.job().getStatus());
  }
}
