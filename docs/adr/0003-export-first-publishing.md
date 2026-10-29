# ADR-0003: Use approval-gated CSV export for the MVP

Status: Accepted

Date: 2026-09-01

## Context

Direct Meta/TikTok publishing requires application approval, OAuth token custody, idempotent remote actions, reconciliation, and operational support. Those dependencies do not validate the primary hypothesis: whether a creator benefits from a coherent AI-assisted Brand Identity, Strategy Playbook, and content workflow.

## Decision

The MVP ends at creator-approved CSV export compatible with a manual Later/Buffer workflow. Instagram and TikTok remain modeled platforms, but Cadence makes no direct publishing call.

The Workspace `OWNER` reviews, approves, schedules/reschedules, and exports content. Future `EDITOR` permissions require a separate product decision.

## Consequences

- Meta/TikTok OAuth approval is not on the MVP critical path.
- Publishing refresh tokens are not stored in the MVP.
- Post/export state, platform, schedule, revisions, and future remote identifiers remain modeled.
- Direct publishing is a later capability requiring readiness review, sandbox tests, idempotency, and reconciliation.
