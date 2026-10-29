package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Server-owned receipt linkage; user-supplied TEXT containing provider-shaped JSON cannot create
 * it.
 */
@Entity
@Table(name = "evidence_social_origins")
public class EvidenceSocialOrigin {
  @Id
  @Column(name = "evidence_id", updatable = false)
  private UUID evidenceId;

  @Column(name = "provider_call_id", nullable = false, updatable = false, unique = true)
  private UUID providerCallId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private SocialEvidenceProvider provider;

  @Column(name = "account_id", nullable = false, updatable = false, length = 128)
  private String accountId;

  @Column(name = "api_version", nullable = false, updatable = false, length = 16)
  private String apiVersion;

  @Column(name = "captured_at", nullable = false, updatable = false)
  private Instant capturedAt;

  @Column(nullable = false, updatable = false, length = 64)
  private String checksum;

  protected EvidenceSocialOrigin() {}

  public EvidenceSocialOrigin(
      UUID evidenceId,
      UUID providerCallId,
      SocialEvidenceProvider provider,
      String accountId,
      String apiVersion,
      Instant capturedAt,
      String checksum) {
    if (evidenceId == null
        || providerCallId == null
        || provider == null
        || accountId == null
        || !accountId.matches("[A-Za-z0-9_-]{1,128}")
        || apiVersion == null
        || !apiVersion.matches("v[1-9][0-9]*\\.0|v2")
        || capturedAt == null
        || checksum == null
        || !checksum.matches("[a-f0-9]{64}"))
      throw new IllegalArgumentException("Invalid social evidence origin");
    this.evidenceId = evidenceId;
    this.providerCallId = providerCallId;
    this.provider = provider;
    this.accountId = accountId;
    this.apiVersion = apiVersion;
    this.capturedAt = capturedAt;
    this.checksum = checksum;
  }

  public UUID getEvidenceId() {
    return evidenceId;
  }

  public UUID getProviderCallId() {
    return providerCallId;
  }

  public SocialEvidenceProvider getProvider() {
    return provider;
  }

  public String getAccountId() {
    return accountId;
  }

  public String getApiVersion() {
    return apiVersion;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public String getChecksum() {
    return checksum;
  }
}
