package com.cadence.presentation.dto;

import com.cadence.domain.SocialEvidenceProvider;
import jakarta.validation.constraints.NotNull;

public record RegisterSocialEvidenceRequest(@NotNull SocialEvidenceProvider provider) {}
