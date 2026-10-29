package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookPillarId {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "local_id", length = 64)
  private String localId;

  protected PlaybookPillarId() {}

  PlaybookPillarId(int entryOrder, String localId) {
    this.entryOrder = entryOrder;
    this.localId = localId;
  }

  String localId() {
    return localId;
  }
}
