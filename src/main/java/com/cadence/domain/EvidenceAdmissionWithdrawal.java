package com.cadence.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Append-only revocation; the original reviewed snapshot remains immutable. */
@Entity
@Table(name = "evidence_admission_withdrawals")
public class EvidenceAdmissionWithdrawal extends BaseEntity {
  @ManyToOne(optional = false)
  @JoinColumn(name = "admission_id", nullable = false, unique = true, updatable = false)
  private EvidenceAdmission admission;

  @Column(name = "withdrawn_by", nullable = false, updatable = false)
  private UUID withdrawnBy;

  @Column(name = "withdrawn_at", nullable = false, updatable = false)
  private Instant withdrawnAt;

  protected EvidenceAdmissionWithdrawal() {}

  public EvidenceAdmissionWithdrawal(EvidenceAdmission admission, UUID actor, Instant now) {
    if (admission == null
        || admission.getId() == null
        || actor == null
        || now == null
        || now.isBefore(admission.getReviewedAt()))
      throw new IllegalArgumentException("Invalid evidence withdrawal");
    this.admission = admission;
    withdrawnBy = actor;
    withdrawnAt = now;
  }

  public UUID getAdmissionId() {
    return admission.getId();
  }

  public UUID getWithdrawnBy() {
    return withdrawnBy;
  }

  public Instant getWithdrawnAt() {
    return withdrawnAt;
  }
}
