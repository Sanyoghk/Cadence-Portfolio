package com.cadence.domain;

import jakarta.persistence.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Entity
@Table(name = "brand_analysis_attempts")
public class BrandAnalysisAttempt extends BaseEntity {
  public enum Status {
    RUNNING,
    SUCCEEDED,
    FAILED,
    SUPERSEDED
  }

  @Column(nullable = false, updatable = false)
  private UUID brandId;

  @Column(name = "requested_by", updatable = false)
  private UUID requestedBy;

  @Column(name = "input_brand_name", updatable = false, length = 255)
  private String inputBrandName;

  @Column(name = "input_industry", updatable = false, length = 255)
  private String inputIndustry;

  @Column(nullable = false, updatable = false)
  private long brandVersion;

  @Column(nullable = false, updatable = false)
  private long workspaceVersion;

  @Column(nullable = false, updatable = false, length = 64)
  private String sourceChecksum;

  @Column(nullable = false, updatable = false)
  private Instant startedAt;

  @Column(nullable = false, updatable = false)
  private Instant deadline;

  @Column private Instant completedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(length = 100)
  private String provider;

  @Column(length = 100)
  private String modelVersion;

  @Column(length = 100)
  private String promptVersion;

  @Column(length = 512)
  private String warnings;

  @Column(length = 64)
  private String outputChecksum;

  protected BrandAnalysisAttempt() {}

  public BrandAnalysisAttempt(
      UUID brandId,
      long brandVersion,
      long workspaceVersion,
      String sourceChecksum,
      Instant startedAt) {
    if (brandId == null
        || brandVersion < 0
        || workspaceVersion < 0
        || sourceChecksum == null
        || !sourceChecksum.matches("[0-9a-f]{64}")
        || startedAt == null) throw new IllegalArgumentException("Invalid analysis attempt");
    this.brandId = brandId;
    this.brandVersion = brandVersion;
    this.workspaceVersion = workspaceVersion;
    this.sourceChecksum = sourceChecksum;
    this.startedAt = startedAt;
    this.deadline = startedAt.plusSeconds(60);
    this.status = Status.RUNNING;
  }

  public BrandAnalysisAttempt(
      UUID brandId,
      long brandVersion,
      long workspaceVersion,
      String sourceChecksum,
      Instant startedAt,
      UUID requestedBy,
      String inputBrandName,
      String inputIndustry) {
    this(brandId, brandVersion, workspaceVersion, sourceChecksum, startedAt);
    if (requestedBy == null) throw new IllegalArgumentException("Missing analysis actor");
    this.requestedBy = requestedBy;
    for (String value : new String[] {inputBrandName, inputIndustry}) {
      if (value == null
          || value.isBlank()
          || value.length() > 255
          || !value.equals(value.strip())
          || value.codePoints().anyMatch(Character::isISOControl))
        throw new IllegalArgumentException("Invalid declared analysis input");
    }
    this.inputBrandName = inputBrandName;
    this.inputIndustry = inputIndustry;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public String getInputBrandName() {
    return inputBrandName;
  }

  public String getInputIndustry() {
    return inputIndustry;
  }

  private void transition(Status target, Instant now) {
    if (status != Status.RUNNING
        || target == null
        || target == Status.RUNNING
        || now == null
        || now.isBefore(startedAt)
        || (target == Status.SUCCEEDED && !now.isBefore(deadline))) {
      throw new IllegalStateException("Invalid analysis completion");
    }
    status = target;
    completedAt = now;
  }

  public void finish(Status target, Instant now) {
    if (target == Status.SUCCEEDED)
      throw new IllegalArgumentException("Successful analysis requires provenance");
    transition(target, now);
  }

  public void succeed(
      Instant now,
      String provider,
      String modelVersion,
      String promptVersion,
      String warnings,
      String outputChecksum) {
    for (String identifier : new String[] {provider, modelVersion, promptVersion}) {
      if (identifier == null || !identifier.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}"))
        throw new IllegalArgumentException("Invalid analysis provenance");
    }
    if (warnings == null
        || warnings.length() > 512
        || (!warnings.isEmpty()
            && !warnings.matches(
                "(?:DEMONSTRATION_ONLY|DECLARED_FIELDS_ONLY|HUMAN_REVIEW_REQUIRED)(?:,(?:DEMONSTRATION_ONLY|DECLARED_FIELDS_ONLY|HUMAN_REVIEW_REQUIRED))*"))
        || outputChecksum == null
        || !outputChecksum.matches("[0-9a-f]{64}"))
      throw new IllegalArgumentException("Invalid analysis receipt");
    transition(Status.SUCCEEDED, now);
    this.provider = provider;
    this.modelVersion = modelVersion;
    this.promptVersion = promptVersion;
    this.warnings = warnings;
    this.outputChecksum = outputChecksum;
  }

  public static String checksum(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  public UUID getBrandId() {
    return brandId;
  }

  public long getBrandVersion() {
    return brandVersion;
  }

  public long getWorkspaceVersion() {
    return workspaceVersion;
  }

  public String getSourceChecksum() {
    return sourceChecksum;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Status getStatus() {
    return status;
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

  public String getOutputChecksum() {
    return outputChecksum;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
