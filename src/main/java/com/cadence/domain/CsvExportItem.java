package com.cadence.domain;

import jakarta.persistence.*;

/** Immutable source links; approved text/audit/media remain in original immutable revision. */
@Embeddable
public class CsvExportItem {
  @ManyToOne(optional = false)
  @JoinColumn(name = "revision_id", nullable = false, updatable = false)
  private PostRevision revision;

  @ManyToOne(optional = false)
  @JoinColumn(name = "schedule_id", nullable = false, updatable = false)
  private PostScheduleEvent schedule;

  @Column(name = "revision_version", nullable = false, updatable = false)
  private long revisionVersion;

  @Column(name = "content_checksum", nullable = false, length = 64, updatable = false)
  private String contentChecksum;

  protected CsvExportItem() {}

  public CsvExportItem(PostRevision r, PostScheduleEvent s) {
    if (r == null
        || s == null
        || r.getStatus() != PostRevision.State.APPROVED
        || !r.getId().equals(s.getRevision().getId()))
      throw new IllegalStateException("Approved scheduled revision required");
    revision = r;
    schedule = s;
    revisionVersion = r.getLockVersion();
    contentChecksum = PostScheduleEvent.checksum(r.content());
  }

  public PostRevision getRevision() {
    return revision;
  }

  public PostScheduleEvent getSchedule() {
    return schedule;
  }

  public long getRevisionVersion() {
    return revisionVersion;
  }

  public String getContentChecksum() {
    return contentChecksum;
  }
}
