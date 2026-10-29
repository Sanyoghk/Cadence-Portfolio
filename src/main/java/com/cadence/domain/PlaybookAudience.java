package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookAudience {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "local_id", length = 64)
  private String localId;

  @Column(name = "segment", length = 200)
  private String segment;

  @Column(name = "needs", length = 1000)
  private String needs;

  @Column(name = "rationale", length = 1000)
  private String rationale;

  protected PlaybookAudience() {}

  PlaybookAudience(int entryOrder, String localId, String segment, String needs, String rationale) {
    this.entryOrder = entryOrder;
    this.localId = localId;
    this.segment = segment;
    this.needs = needs;
    this.rationale = rationale;
  }

  String localId() {
    return localId;
  }

  String segment() {
    return segment;
  }

  String needs() {
    return needs;
  }

  String rationale() {
    return rationale;
  }
}
