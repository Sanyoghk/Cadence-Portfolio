# ADR0033 — versioned calendar scheduling without publishing

Дата: 2026-10-16. Scope: prompt16, #115/#103; prerequisites14–15 verified at
`c0e547399a797aec8212d544da27404b8581f354` on clean `mvp`. Technical decisions within
approved ADR0031/product scope. Qualification results recorded separately in report16.

## Contract

Scheduling is editorial metadata, never publishing, asset availability, moderation or AI egress.
Only latest nonlegacy APPROVED Post revision with actual approval audit, active parents,
current OWNER/account/membership and exact still-approved full Strategy source is eligible.
The creative source is historical as in ADR0031: scheduling performs no provider I/O and
does not reread withdrawn evidence behind an already human-approved creative work. New AI
generation still uses the stricter ADR0032 lifetime policy. No provider/privacy decision changes.
DRAFT/SUPERSEDED/LEGACY cannot be scheduled; unscheduled drafts use existing bounded Posts API.
Media gate17 and export capture18 remain separate consumers; READY here is not permission to publish.

Each Post has append-only schedule events1–1000. Latest event is the head; zero means no history.
SCHEDULE captures exact approved revision UUID/lockVersion/content SHA256, original IANA timezone,
local datetime, explicit UTC offset, UTC Instant, JVM tzdb version, actual human actor and DB time.
CANCEL copies the prior assignment binding verbatim and records a new actor/time/version.
History never rewrites/deletes an earlier event. Existing legacy scheduledAt is not imported
as verified scheduling: missing platform/timezone/actor cannot be fabricated.

Mutations require both current revision UUID/expectedRevisionVersion and expectedScheduleVersion.
Stale/replayed requests return409; this operation has optimistic replay protection, not an invented
idempotency-key contract. A cancellation requires a SCHEDULE head, including a stale historical
assignment; it may proceed when the Strategy is unavailable, but requires active parents/access.
No-op SCHEDULE with current tokens is an explicit new human decision and appends history.

| Current head | Request | Result |
| --- | --- | --- |
| none | SCHEDULE current APPROVED | first event |
| SCHEDULE | SCHEDULE current APPROVED | reschedule, next event |
| CANCEL | SCHEDULE current APPROVED | new assignment, next event |
| SCHEDULE | CANCEL with current Post/head tokens | copied binding, next CANCEL |
| none/CANCEL | CANCEL |409 |
| any | stale revision/head, foreign scope, invalid source/time | safe refusal, no partial writes |
| SCHEDULE | new content DRAFT/regeneration | old event retained, CONTENT_CHANGED |
| stale assignment | approval of new content | remains stale until explicit new assignment |

## Time, slots and bounds

Input is `timezone`, `localTime`, `offset`; no process/default/system timezone is changed.
Only installed named ZoneIds (including UTC), maximum100 characters. Offset is mandatory even
for ordinary times. `ZoneRules.getValidOffsets(localTime)` must contain it: spring gaps reject400,
autumn folds allow either explicitly chosen offset. Non-hour DST transitions are supported.
No implicit gap shift or fold default. Store the resolved Instant and original tuple; changing
view timezone only changes display fields. PostgreSQL independently checks local/UTC/offset
roundtrip against its timezone database, so differing rules fail closed on new scheduling.
Existing assigned UTC Instants are never moved automatically by future timezone database updates;
original tuple/rulesVersion stays historical, current display uses installed rules.

Technical operational caps, not subscription/pricing or external publishing limits:
- minute-aligned local time (seconds/nanos zero), strictly future, at most366 UTC days from DB now;
- half-open query `[from,until)`, positive interval at most28 local calendar days in view timezone,
  so DST fall-back may produce673 UTC hours; no fixed672-hour shortcut;
- pages0–10000, size1–100, fixed `(scheduledAt,id)` ordering; history `(eventNumber DESC,id)`;
- batch1–84 distinct Posts in one Brand; existing caps10000 Posts/Brand and1000 content revisions
  retained, schedule history separately capped1000 events/Post.

Slot key is `(Brand,platform,UTC Instant at selected local minute)`. Different platforms or Brands
may share the instant. The two instants in a DST fold are distinct slots. Same-platform different
Posts cannot occupy the same effective slot. A stale revision/source/archived-parent assignment
does not represent a usable slot; stale content does not reserve it. Media is not consulted yet.

Batch is all-or-nothing, including tokens/authorization/validation/collisions. Validate final head
set, allowing an atomic slot swap or cancel+reuse. Request order is preserved in response; locks
are acquired in stable source/Post order under the shared parent mutex. No partial success mode.

## Architecture, integrity and alternatives

Separate domain event + repository + secured application use case + strict DTO/controller;
existing Post content APIs/provenance are unchanged. Account→Workspace→Brand→Strategy→Post lock
order follows ADR0031. Reads hold Account/parents while resolving consistent eligibility; no
network I/O/worker/queue involved. Concurrent archive/access/source/edit either precedes scheduling
and causes refusal, or follows it and makes the retained assignment ineffective. Scheduling wins
before archive does not imply automatic execution after archive. Archive is irreversible today.

Compared: mutable scheduledAt on approved Post is simpler but rewrites immutable history; mutable
schedule head plus audit adds a dual-write invariant; append-only ledger derives its head using
indexed versions and preserves audit with fewer mutable invariants. Trade-off: indexed head joins
and read-time eligibility checks. JSON-only history lacks independent FK/version/SQL enforcement.

V49 has composite Brand/Post and Post/revision FKs, event uniqueness/range/check constraints,
insert-only trigger, actual actor/source/currentness/time/checksum validation, and deferred final
slot collision trigger so swaps work atomically. Runtime role has SELECT/INSERT, no UPDATE/DELETE.
Brand is denormalized only for tenant-leading calendar index, independently bound by composite FK.
Time index `(brand_id,scheduled_at,id)` is partial for SCHEDULE; Post/version unique B-tree supports
latest/history checks. No historical migration checksums change; no fabricated legacy schedule rows.
Current lifecycle operations cannot silently revive an invalid assignment: content revision numbers
only increase, approved history is immutable, archive is irreversible, and a LOCKED/changed exact
Strategy no longer matches its captured approval/version. Thus non-scheduling mutations invalidate
slots; only an explicit new SCHEDULE can reserve one, and it always executes the deferred seal.
Checksum v1 encodes UTF8 byte-length-prefixed title, caption, format, mediaPrompt, nullable videoScript,
hashtag count and ordered hashtags, with `post-schedule-content-v1:` prefix; SQL recomputes the same
digest. NULL uses `-1:`; byte lengths prevent delimiter/content ambiguity.

## Threat model and verification

Protect tenant content references, exact human approval and scheduling audit. Callers include
hostile authenticated clients and internal bean callers. HTTP, application and DB are boundaries.
IDOR/forged roles→scoped resource resolution+actual OWNER/membership under Account/parent locks.
Mass assignment→strict nested DTO; no actor/provider/source ownership fields. Stale drag/batch
replay→dual tokens/latest checks. SQL bypass→FK/checks/trigger/immutable privileges. Collision/edit/
archive/revocation races→parent mutex, current revision checks and deferred slot seal. Exhaustion→
batch/history/horizon/window/page caps and indexes. Plain metadata never becomes commands/fetch/
publishing. No new dependency, secret, cookie auth or SecurityFilterChain change; Bearer-only
authentication retains its existing CSRF rationale. No content or credentials logged by this flow.

Qualification: deterministic domain DST/gap/fold/non-hour tests; shared H2/PostgreSQL HTTP lifecycle/
negative tests; real PostgreSQL concurrent replay/slots/edit/revocation, SQL/runtime protections,
migration/UTC inventory and full existing gates. Exact executed results belong to report16.

Operational boundary: calendar query dates1900–2200; event1000 reserved for final CANCEL so
reaching the scheduling history cap cannot prevent cancellation. No new SCHEDULE at event1000.
