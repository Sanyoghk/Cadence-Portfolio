package com.cadence.domain;

import jakarta.persistence.*;

/** Seeded singleton. All ledger writers serialize here after parent/operation locks. */
@Entity
@Table(name = "generation_budget_mutex")
public class GenerationBudgetMutex {
  @Id private int id;

  protected GenerationBudgetMutex() {}

  public static GenerationBudgetMutex singleton() {
    var m = new GenerationBudgetMutex();
    m.id = 1;
    return m;
  }
}
