package com.cadence.domain;

/** Lifecycle status of a social media post. */
public enum PostStatus {

  /** Draft that can still be edited. */
  DRAFT,

  /** Approved by a user. */
  APPROVED,

  /** Scheduled for publication. */
  SCHEDULED,

  /** Successfully published. */
  PUBLISHED,

  /** Publishing failed. */
  FAILED
}
