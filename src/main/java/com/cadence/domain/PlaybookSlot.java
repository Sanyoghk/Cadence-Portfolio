package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookSlot {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "week_number")
  private int weekNumber;

  @Column(name = "slot_id", length = 64)
  private String slotId;

  @Column(name = "channel", length = 20)
  private String channel;

  @Column(name = "pillar_id", length = 64)
  private String pillarId;

  @Column(name = "audience_id", length = 64)
  private String audienceId;

  @Column(name = "topic", length = 500)
  private String topic;

  @Column(name = "intent", length = 500)
  private String intent;

  @Column(name = "day_offset")
  private int dayOffset;

  protected PlaybookSlot() {}

  PlaybookSlot(
      int entryOrder,
      int weekNumber,
      String slotId,
      String channel,
      String pillarId,
      String audienceId,
      String topic,
      String intent,
      int dayOffset) {
    this.entryOrder = entryOrder;
    this.weekNumber = weekNumber;
    this.slotId = slotId;
    this.channel = channel;
    this.pillarId = pillarId;
    this.audienceId = audienceId;
    this.topic = topic;
    this.intent = intent;
    this.dayOffset = dayOffset;
  }

  int weekNumber() {
    return weekNumber;
  }

  String slotId() {
    return slotId;
  }

  String channel() {
    return channel;
  }

  String pillarId() {
    return pillarId;
  }

  String audienceId() {
    return audienceId;
  }

  String topic() {
    return topic;
  }

  String intent() {
    return intent;
  }

  int dayOffset() {
    return dayOffset;
  }
}
