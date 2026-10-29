package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookHypothesis {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "statement", length = 1000)
  private String statement;

  @Column(name = "rationale", length = 1000)
  private String rationale;

  protected PlaybookHypothesis() {}

  PlaybookHypothesis(int entryOrder, String statement, String rationale) {
    this.entryOrder = entryOrder;
    this.statement = statement;
    this.rationale = rationale;
  }

  String statement() {
    return statement;
  }

  String rationale() {
    return rationale;
  }
}
