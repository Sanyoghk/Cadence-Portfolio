package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookGoal {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "channel", length = 20)
  private String channel;

  @Column(name = "goal_order")
  private int goalOrder;

  @Column(name = "objective", length = 500)
  private String objective;

  @Column(name = "metric_hint", length = 100)
  private String metricHint;

  @Column(name = "rationale", length = 1000)
  private String rationale;

  protected PlaybookGoal() {}

  PlaybookGoal(
      int entryOrder,
      String channel,
      int goalOrder,
      String objective,
      String metricHint,
      String rationale) {
    this.entryOrder = entryOrder;
    this.channel = channel;
    this.goalOrder = goalOrder;
    this.objective = objective;
    this.metricHint = metricHint;
    this.rationale = rationale;
  }

  String channel() {
    return channel;
  }

  int goalOrder() {
    return goalOrder;
  }

  String objective() {
    return objective;
  }

  String metricHint() {
    return metricHint;
  }

  String rationale() {
    return rationale;
  }
}
