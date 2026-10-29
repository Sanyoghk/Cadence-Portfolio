package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookTone {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "local_id", length = 64)
  private String localId;

  @Column(name = "left_label", length = 100)
  private String leftLabel;

  @Column(name = "right_label", length = 100)
  private String rightLabel;

  @Column(name = "position")
  private int position;

  @Column(name = "rationale", length = 1000)
  private String rationale;

  protected PlaybookTone() {}

  PlaybookTone(
      int entryOrder,
      String localId,
      String leftLabel,
      String rightLabel,
      int position,
      String rationale) {
    this.entryOrder = entryOrder;
    this.localId = localId;
    this.leftLabel = leftLabel;
    this.rightLabel = rightLabel;
    this.position = position;
    this.rationale = rationale;
  }

  String localId() {
    return localId;
  }

  String leftLabel() {
    return leftLabel;
  }

  String rightLabel() {
    return rightLabel;
  }

  int position() {
    return position;
  }

  String rationale() {
    return rationale;
  }
}
