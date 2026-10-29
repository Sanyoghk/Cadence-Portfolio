package com.cadence.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(
    name = "brand_analysis_citations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"attempt_id", "position"}))
public class BrandAnalysisCitation extends BaseEntity {
  @Column(name = "attempt_id", nullable = false, updatable = false)
  private UUID attemptId;

  @Column(name = "admission_id", nullable = false, updatable = false)
  private UUID admissionId;

  @Column(nullable = false, updatable = false)
  private int position;

  @Column(nullable = false, updatable = false, length = 500)
  private String quote;

  @Column(name = "field_path", updatable = false, length = 32)
  private String fieldPath;

  public String getFieldPath() {
    return fieldPath;
  }

  protected BrandAnalysisCitation() {}

  public UUID getAdmissionId() {
    return admissionId;
  }

  public String getQuote() {
    return quote;
  }

  public BrandAnalysisCitation(UUID attemptId, int position, UUID admissionId, String quote) {
    this(attemptId, position, admissionId, quote, null);
  }

  public BrandAnalysisCitation(
      UUID attemptId, int position, UUID admissionId, String quote, String fieldPath) {
    if (fieldPath != null && !com.cadence.domain.SocialTextProjection.citationPath(fieldPath))
      throw new IllegalArgumentException("Invalid citation field path");
    this.fieldPath = fieldPath;
    if (attemptId == null
        || position < 0
        || position >= 10
        || admissionId == null
        || quote == null
        || quote.isBlank()
        || quote.length() > 500
        || quote.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1000
        || !java.nio.charset.StandardCharsets.UTF_8.newEncoder().canEncode(quote)
        || quote
            .codePoints()
            .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t'))
      throw new IllegalArgumentException("Invalid analysis citation receipt");
    this.attemptId = attemptId;
    this.position = position;
    this.admissionId = admissionId;
    this.quote = quote;
  }
}
