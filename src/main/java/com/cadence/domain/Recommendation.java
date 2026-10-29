package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "recommendations")
public class Recommendation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "strategy_id", nullable = false)
  private Strategy strategy;

  @Column(nullable = false, length = 2000)
  private String text;

  @Column(nullable = false)
  private double confidence;

  @Column(name = "expected_lift", nullable = false, length = 500)
  private String expectedLift;

  protected Recommendation() {}

  public Recommendation(Strategy strategy, String text, double confidence, String expectedLift) {
    setStrategy(strategy);
    setText(text);
    setConfidence(confidence);
    setExpectedLift(expectedLift);
  }

  public Strategy createDraftStrategy() {
    return strategy.createDraftVersion();
  }

  public Strategy getStrategy() {
    return strategy;
  }

  public void setStrategy(Strategy strategy) {
    if (strategy == null) {
      throw new IllegalArgumentException("Recommendation strategy must not be null");
    }

    this.strategy = strategy;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = DomainText.required(text, "Recommendation text", 2000);
  }

  public double getConfidence() {
    return confidence;
  }

  public void setConfidence(double confidence) {
    if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {

      throw new IllegalArgumentException("Confidence must be between 0.0 and 1.0");
    }

    this.confidence = confidence;
  }

  public String getExpectedLift() {
    return expectedLift;
  }

  public void setExpectedLift(String expectedLift) {
    this.expectedLift = DomainText.required(expectedLift, "Expected lift", 500);
  }
}
