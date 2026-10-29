# Calendar API — prompt16

Contract: [ADR0033](adr/0033-calendar-scheduling.md), [OpenAPI](../src/main/resources/openapi/cadence.json).
All routes require Bearer authentication and actual active OWNER/account/Workspace membership.
Foreign/missing resource404; reserved roles403; malformed input400; stale/conflicting state409.

`POST /api/v1/brands/{brandId}/calendar/schedules` returns200 and an ordered array of schedule
snapshots. A single change serves drag-reschedule;1–84 distinct Posts form an atomic batch.

```json
{
  "changes": [{
    "postId": "00000000-0000-0000-0000-000000000001",
    "revisionId": "00000000-0000-0000-0000-000000000002",
    "expectedRevisionVersion": 1,
    "expectedScheduleVersion": 0,
    "action": "SCHEDULE",
    "timezone": "Europe/London",
    "localTime": "2026-10-25T01:30:00",
    "offset": "+01:00"
  }]
}
```

Example timestamps are illustrative: new scheduling must be strictly future and≤366 UTC days.
That local time occurs twice: `+01:00` selects00:30Z; `Z` selects01:30Z. Both are valid and
different slots. A DST spring gap or incorrect/missing offset rejects400. No server default or
frontend-only timezone conversion. Time precision is whole local minutes. Timezone does not
need to match the Strategy suggestion; explicit selection is per assignment, never a system setting.

First assignment expects scheduleVersion0; subsequent mutations use returned scheduleVersion.
Use actual current revision UUID and lockVersion from existing Post APIs. `CANCEL` uses the same
identity/version fields and omits timezone/localTime/offset; it appends a copied historical binding.
Cancel without a SCHEDULE head409. Retrying an already committed mutation with old tokens409;
read schedule history to resolve a response-loss outcome. No Idempotency-Key contract is claimed.

Only current APPROVED content can be assigned. New content DRAFT/regeneration invalidates a prior
assignment without erasing it, and later approval does not move the old assignment to new content.
The author explicitly schedules the new revision. Collision key: effective Brand+platform+UTC
instant; concurrent or batch duplicates409. Different platform may share time; atomic swaps work.
All batch validation/tokens/collisions must succeed or none of its events are committed.

`GET /api/v1/brands/{brandId}/calendar?from=...Z&until=...Z&timezone=Europe%2FLondon&page=0&size=100`
returns standard PageResponse, sorted UTC scheduledAt then UUID. `[from,until)` is half-open and
bounded by28 local days in display timezone, size1–100/page0–10000. DST can make28 local days
671/673 hours. Query does not assign missing dates or turn generated outline slots into schedules.
Existing bounded Posts API supplies unscheduled/DRAFT content for the frontend planning tray.

Calendar includes SCHEDULE heads in range, including assignments invalidated by later changes.
Response preserves original timezone/localTime/offset/scheduledAt/rulesVersion and exposes current
displayLocalTime/displayOffset. `eligibility` is READY, CONTENT_CHANGED, PARENT_ARCHIVED or
SOURCE_UNAVAILABLE; CANCEL heads are absent from calendar. READY is editorial schedule validity,
not execution/export/media permission. No network request or publishing side effect occurs.

`GET /api/v1/posts/{postId}/schedule-history?page=0&size=20` returns all events, descending
scheduleVersion. Previous events have `current=false,eligibility=HISTORICAL`; current cancellation
has CANCELLED. History remains readable under actual permissions after archive/source changes.
Every event includes actorId/occurredAt, exact revision/version, SHA256 contentChecksum and original
time binding. It has a separate scheduleVersion; content lockVersion is never incremented by scheduling.
Read history also distinguishes no prior assignment (empty list) from a cancelled assignment.

Legacy scheduledAt remains unverified read-only Post history. Media17/CSV18 and platform publishing
are not implemented by these endpoints; existing human approval semantics are unchanged.

Operational boundary: calendar query dates1900–2200; event1000 reserved for final CANCEL so
reaching the scheduling history cap cannot prevent cancellation. No new SCHEDULE at event1000.
