package com.cadence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class ContentPillar {

  @Column(name = "pillar_name")
  private String name;

  @Column(name = "pillar_weight")
  private int weight;

  @Column(name = "pillar_description", length = 1000)
  private String description;

  protected ContentPillar() {}

  public ContentPillar(String name, int weight, String description) {
    setName(name);
    setWeight(weight);
    setDescription(description);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = DomainText.required(name, "Content pillar name", 255);
  }

  public int getWeight() {
    return weight;
  }

  public void setWeight(int weight) {
    if (weight < 0 || weight > 100) {
      throw new IllegalArgumentException("Content pillar weight must be between 0 and 100");
    }

    this.weight = weight;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = DomainText.required(description, "Content pillar description", 1000);
  }
}
