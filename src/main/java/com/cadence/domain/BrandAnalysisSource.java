package com.cadence.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "brand_analysis_sources",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"attempt_id", "position"}),
      @UniqueConstraint(columnNames = {"attempt_id", "admission_id"})
    })
public class BrandAnalysisSource extends BaseEntity {
  @Column(name = "attempt_id", nullable = false, updatable = false)
  private UUID attemptId;

  @ManyToOne(optional = false)
  @JoinColumn(name = "admission_id", nullable = false, updatable = false)
  private EvidenceAdmission admission;

  @Column(nullable = false, updatable = false)
  private int position;

  protected BrandAnalysisSource() {}

  public BrandAnalysisSource(UUID attemptId, EvidenceAdmission admission, int position) {
    if (attemptId == null
        || admission == null
        || admission.getId() == null
        || position < 0
        || position >= 5) throw new IllegalArgumentException("Invalid analysis source link");
    this.attemptId = attemptId;
    this.admission = admission;
    this.position = position;
  }

  public EvidenceAdmission getAdmission() {
    return admission;
  }
}
