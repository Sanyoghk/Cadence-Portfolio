package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A committed attempt consumes quota even if external I/O or evidence publication fails. */
@Entity
@Table(name = "evidence_provider_calls")
public class EvidenceProviderCall {
  @Id private UUID id;

  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  @Column(name = "brand_id", nullable = false, updatable = false)
  private UUID brandId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private SocialEvidenceProvider provider;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  protected EvidenceProviderCall() {}

  public EvidenceProviderCall(
      UUID workspaceId, UUID brandId, SocialEvidenceProvider provider, Instant requestedAt) {
    if (workspaceId == null || brandId == null || provider == null || requestedAt == null)
      throw new IllegalArgumentException("Invalid provider call reservation");
    id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.brandId = brandId;
    this.provider = provider;
    this.requestedAt = requestedAt;
  }

  public UUID getId() {
    return id;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }
}
