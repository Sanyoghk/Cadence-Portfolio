# ADR0035 — bounded private CSV snapshot export

Дата: 2026-10-16. Task18; actual #103 requires approved CSV through a durable Job.
Baseline mvp/abaeeb1;14–17 qualified locally, no repeated implementation. #107 maps
Content/calendar/export; historical checkbox state is not implementation evidence.

## Decision and lifetime

Separate CSV_EXPORT DurableJob, not AI_GENERATION and not an ingest job. Request explicitly
selects1–84 distinct Posts with current revision UUID/version and schedule event version,
one Brand, interval[from,until) <=28 local days, named view timezone. No implicit partial
selection or silently omitted drafts. Server captures immutable revision/schedule FKs,
content checksum and ordered snapshot checksum; original source/model/human provenance
remains in immutable linked history. Sort by scheduled UTC instant then Post UUID.

Only latest nonlegacy audited APPROVED revision, current SCHEDULE head bound to that revision,
exact still-approved full Strategy, active parents and actual active OWNER/membership qualify.
Every selected media reference must remain reviewed/available, tenant/format bound and physically
verified through ADR0034 outside transactions, including submission, worker and download.
Empty media is editorial TEXT_ONLY, never a successful generated/publishable asset claim.

Historical creative policy ADR0031/0033 is preserved: export does not transmit to AI or
re-admit underlying Identity evidence. Withdrawal of raw AI evidence does not erase an
already human-approved creative work. CSV is explicitly APPROVED_CREATIVE_V1 snapshot,
not current evidence consent, provider moderation or publishing permission. Changed/withdrawn
selected assets, source Strategy, revision or schedule deny the entire export/download.
New Identity/Strategy approval does not replace the exact captured source. No export of DRAFT,
legacy or stale revisions, even when an older approved revision still exists.

Capture, pre-render validation, publication and download each authorize persisted actor/current
requester and exact captured graph. Account→Workspace→Brand→sorted Strategy/Post→Asset→export/job
locks; bounded private I/O and CSV rendering outside DB transactions; recapture before commit.
Permission revocation after successful publication preserves historical SUCCEEDED but denies
download. No claim of revoking already delivered bytes; each response linearizes at final check.

## Idempotency, durability and operational bounds

Required ASCII key16–128 `[A-Za-z0-9_-]`, scoped to actor/Brand/CSV_EXPORT. Canonical normalized
request digest includes interval, timezone and sorted selection tokens. Same key/digest returns
same Job without reselecting content; different digest409. Other actors/Brands have independent
key namespaces. Replay still requires current resource access, not a bearer capability.

At most100 retained exports/Brand, each reserves2MiB before work; failures/expiry retain quota.
Deadline5 minutes from DB capture; Job max3 attempts,60s lease, bounded retries2–10s. Expired lease
recovers; rendering is pure/retry safe, one artifact publication fenced by exact worker+attempt,
deadline and Job lease. Lost lease never publishes. No AI budget or unknown spend is invented.
One worker per process, DB fences coordinate multiple instances. Worker default disabled until
explicit `cadence.csv-export.worker-enabled=true`; manual internal runOnce for deterministic tests.

Artifact bytes stored in private PostgreSQL bytea, <=2MiB. Atomic artifact/checksum/Job completion
avoids object-store orphan/dual-write complexity. Download lifetime exactly24h from capture,
exclusive expiry. No automatic deletion: current retention policy retains history/bytes with
quota; future approved deletion policy must distinguish download expiry from physical deletion.
No storage key, arbitrary URL/path, client filename, public/signed bearer URL or range delivery.

## CSV v1

UTF-8 with BOM, comma delimiter, CRLF record separators, every cell double-quoted with internal
quotes doubled; embedded CR/LF and Unicode retained. Header/column order fixed in CsvDocument.
Times ISO8601; original schedule timezone/local/offset plus requested view timezone/local/offset.
Rows identify captured revision/version/checksum, Strategy version/checksum, schedule event/version,
creator/edit/approval/export actor/time and ordered media IDs/checksums/origin/moderation.
Missing audit/provider fields remain empty; no fabricated observations or assets. Export timestamp
is captured once, not worker clock, so retries/replay are byte deterministic.

Before CSV quoting, if first significant codepoint after Unicode whitespace/space/control/format
is `= + - @`, prefix apostrophe. Leading TAB/CR/LF also prefix apostrophe. This intentionally
changes dangerous cell representation; original immutable text and checksum remain history.
Spreadsheet auto-conversion/import behavior varies: generic manual-use CSV, no undocumented
Later/Buffer import compatibility promise. Filename `cadence-export-{server UUID}.csv` only.

## Alternatives / threats / verification

Synchronous HTTP has simpler code but no durable crash recovery. Public object URLs weaken
current authorization; object storage adds a publication reconciliation invariant. Private DB
bytes cost bounded DB space but enable atomic success and no new storage capability/dependency.
Mutable current queries at worker time break selection stability; immutable FKs + fresh eligibility
give deterministic history and fail closed on changes. No silent partial success.

Protect private approved content against BOLA/mass assignment/formula execution/resource exhaustion,
stale publication and duplicate workers. Strict DTO + actual membership + scoped lookup + bounded
capture + private physical verification + domain validation + SQL FKs/checks/triggers + runtime
least privilege provide independent layers. Tests must cover allowed CSV, Unicode/quoting/formulas,
draft/foreign/stale/archive/access/media races, idempotency collisions, expired lease/restart,
late-worker fencing, PostgreSQL bypass and runtime role; full gates/startup before qualification.
