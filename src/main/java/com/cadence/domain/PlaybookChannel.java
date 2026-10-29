package com.cadence.domain;

import jakarta.persistence.*;

/** Owned normalized Playbook value; mutations go through Strategy. */
@Embeddable
public class PlaybookChannel {
  @Column(name = "entry_order", nullable = false)
  private int entryOrder;

  @Column(name = "channel", length = 20)
  private String channel;

  @Column(name = "share_percent")
  private int sharePercent;

  protected PlaybookChannel() {}

  PlaybookChannel(int entryOrder, String channel, int sharePercent) {
    this.entryOrder = entryOrder;
    this.channel = channel;
    this.sharePercent = sharePercent;
  }

  String channel() {
    return channel;
  }

  int sharePercent() {
    return sharePercent;
  }
}
