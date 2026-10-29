package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Embeddable
public class StrategyGenerationProvenance {
  @Column(name = "generation_id", updatable = false)
  private UUID id;

  @Column(name = "generation_brand_version", updatable = false)
  private Long brandVersion;

  @Column(name = "generation_workspace_version", updatable = false)
  private Long workspaceVersion;

  @Column(name = "generation_started_at", updatable = false)
  private Instant startedAt;

  @Column(name = "generation_deadline", updatable = false)
  private Instant deadline;

  @Column(name = "generation_provider", length = 100, updatable = false)
  private String provider;

  @Column(name = "generation_model_version", length = 100, updatable = false)
  private String modelVersion;

  @Column(name = "generation_prompt_version", length = 100, updatable = false)
  private String promptVersion;

  @Column(name = "generation_warnings", length = 512, updatable = false)
  private String warnings;

  @Column(name = "generation_identity_id", updatable = false)
  private UUID identityId;

  @Column(name = "generation_identity_version", updatable = false)
  private Integer identityVersion;

  @Column(name = "generation_identity_lock_version", updatable = false)
  private Long identityLockVersion;

  @Column(name = "generation_identity_checksum", updatable = false)
  private String identityChecksum;

  @Column(name = "generation_identity_approved_by", updatable = false)
  private UUID identityApprovedBy;

  @Column(name = "generation_identity_approved_at", updatable = false)
  private Instant identityApprovedAt;

  @Column(name = "generation_identity_analysis_id", updatable = false)
  private UUID identityAnalysisId;

  @Column(name = "generation_requested_by", updatable = false)
  private UUID requestedBy;

  public void attachIdentity(ApprovedIdentitySource source) {
    if (identityId != null || source == null)
      throw new IllegalStateException("Identity source already set");
    identityId = source.identityId();
    identityVersion = source.version();
    identityLockVersion = source.lockVersion();
    identityChecksum = source.checksum();
    identityApprovedBy = source.approvedBy();
    identityApprovedAt = source.approvedAt();
    identityAnalysisId = source.analysisId();
    requestedBy = source.requestedBy();
  }

  public ApprovedIdentitySource getIdentitySource() {
    return identityId == null
        ? null
        : new ApprovedIdentitySource(
            identityId,
            identityVersion,
            identityLockVersion,
            identityChecksum,
            identityApprovedBy,
            identityApprovedAt,
            identityAnalysisId,
            requestedBy);
  }

  @Column(name = "generation_content_format", updatable = false)
  private Integer contentFormat;

  @Column(name = "generation_output_checksum", length = 64, updatable = false)
  private String outputChecksum;

  public Integer getContentFormat() {
    return contentFormat;
  }

  public String getOutputChecksum() {
    return outputChecksum;
  }

  public void attachOutput(StrategyContent base, PlaybookContent playbook) {
    if (contentFormat != null) throw new IllegalStateException("Immutable output receipt");
    playbook.validateWith(base);
    contentFormat = 2;
    outputChecksum = BrandAnalysisAttempt.checksum(playbook.canonical(base));
  }

  protected StrategyGenerationProvenance() {}

  public StrategyGenerationProvenance(
      UUID id,
      long brandVersion,
      long workspaceVersion,
      Instant startedAt,
      String provider,
      String modelVersion,
      String promptVersion,
      String warnings) {
    if (id == null || brandVersion < 0 || workspaceVersion < 0 || startedAt == null)
      throw new IllegalArgumentException("Invalid strategy generation source");
    for (String identifier : new String[] {provider, modelVersion, promptVersion})
      if (identifier == null || !identifier.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
        throw new IllegalArgumentException("Invalid strategy provenance");
    if (warnings == null
        || warnings.length() > 512
        || (!warnings.isEmpty()
            && !warnings.matches(
                "(?:DEMONSTRATION_ONLY|HUMAN_REVIEW_REQUIRED)(?:,(?:DEMONSTRATION_ONLY|HUMAN_REVIEW_REQUIRED))*")))
      throw new IllegalArgumentException("Invalid strategy warnings");
    this.id = id;
    this.brandVersion = brandVersion;
    this.workspaceVersion = workspaceVersion;
    this.startedAt = startedAt;
    this.deadline = startedAt.plusSeconds(60);
    this.provider = provider;
    this.modelVersion = modelVersion;
    this.promptVersion = promptVersion;
    this.warnings = warnings;
  }

  public UUID getId() {
    return id;
  }

  public Long getBrandVersion() {
    return brandVersion;
  }

  public Long getWorkspaceVersion() {
    return workspaceVersion;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public String getProvider() {
    return provider;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getWarnings() {
    return warnings;
  }
}
