package com.cadence.domain;

import jakarta.persistence.*;

/** One immutable database mutex identity per supported provider, seeded by migrations. */
@Entity
@Table(name = "provider_capacity_slots")
public class EvidenceProviderCapacity {
  @Id
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 16)
  private SocialEvidenceProvider provider;

  protected EvidenceProviderCapacity() {}

  public EvidenceProviderCapacity(SocialEvidenceProvider provider) {
    if (provider == null) throw new IllegalArgumentException("Social provider is required");
    this.provider = provider;
  }
}
