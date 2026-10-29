package com.cadence.domain;

/** Approval state for strategies and other reviewable entities. */
public enum ApprovalStatus {

  /** Initial editable version. */
  DRAFT,

  /** Approved and ready for use. */
  APPROVED,

  /** Locked from further modifications. */
  LOCKED
}
