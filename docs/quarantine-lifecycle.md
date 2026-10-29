# Quarantine lifecycle, quotas and orphan reconciliation

## Contract and ownership

`QuarantineLifecycle.reserve(workspaceId, brandId, maxBytes, extension)` commits a reservation before
any scanner, network or object-storage I/O. Its server-generated identity is a random UUID and the
server-generated key is `quarantine/writes/<workspace UUID>/<reservation UUID>.<extension>`.
Clients cannot choose it. Every retry gets its own generation; no generation is reused or overwritten
by another attempt. Content checksums still deduplicate evidence in the application and are not storage
ownership identifiers.

The caller first checks authentication, role, permitted workspace and active Brand. The service
checks active workspace and the Brand-to-workspace relation again. `reserve` takes a workspace row
lock before summing retained quota and counting the last hour's writes, so concurrent requests against
different Brands cannot oversubscribe the same workspace. A rejected quota request performs no external
I/O. Interrupted uploads, provider failures and failed database commits keep their byte/object reservation.
Hourly provider-cost quota counts all generations, including failed and tombstoned ones.

Lifecycle boundaries are truncated to database microsecond precision before persistence and comparison.
Otherwise a fixed nanosecond Clock can persist a rounded-up `reserved_at` and falsely reject publication
at the same instant, or round up `write_deadline` and accidentally permit its exact expiry. The contract
tests deliberately use a nanosecond fraction that would round up to protect both deadline boundaries.

`reference(reservationId, evidenceId, exactReference, actualBytes)` requires the evidence transaction
(`Propagation.MANDATORY`). It locks the generation, verifies deadline, Brand/workspace, exact reference
and `1 <= actualBytes <= reservedBytes`, then changes `RESERVED` to `REFERENCED` in the same commit as
Evidence and its durable job. Rollback leaves an orphan reservation. When a concurrent duplicate upload
finds evidence referencing another generation, that attempt must remain an orphan; registering its
different key against the existing Evidence would forge provenance.

```
Ownership check → quota reservation commit → bounded I/O → Evidence + Job + reference commit
                                                ↓ failure
                                   expired orphan → DELETING → TOMBSTONED
```

## Retention and deletion

This policy automatically expires **unreferenced managed generations only**. It does not delete active,
archived, rejected or failed Evidence that still references its bytes. A referenced object and its quota
remain retained. Future user-data deletion requires a separate authorized deletion use case, job/scan
dependency analysis, legal retention policy and immutable audit history.

An orphan becomes eligible at `writeDeadline + orphanRetention`. Cleanup locks its registry row and
rechecks Evidence source/fetched references, registered historical intents and active jobs with a direct
object payload. It durably changes the row to `DELETING` before requesting an idempotent private delete.
External I/O happens outside the database transaction. Failed deletes retain quota and retry no sooner
than the configured write deadline; cleanup attempts and next due time survive process restart.

A successful delete leaves a permanent `TOMBSTONED` registry row. Quota stays reserved through the
deletion drain window. A **second successful delete after that window** releases its byte/object quota.
The row remains on a periodic reconciliation schedule permanently: this removes late provider PUTs
whose original client response was ambiguous. Tombstone keys can never be referenced again, including
through raw PostgreSQL statements. An old worker cannot publish a revived generation.

Cleanup prioritizes quota-retaining generations before already-released tombstones. Candidate selection
excludes referenced/active-job generations and still repeats those checks under its row lock, so an old
blocked reference cannot occupy every batch forever. `tombstone-sweep` is a minimum interval, not a
guaranteed completion SLA: total tombstone backlog, instance capacity and provider deadlines can extend
it. Monitor oldest-due age and size cleanup capacity for that backlog. Permanent reconciliation is an
intentional operational trade-off for handling ambiguous provider completion safely.

The private `EvidenceObjectLifecycleStorage.deleteQuarantined(reference)` capability accepts only the
registry-selected internal reference. There is no HTTP delete endpoint and no client-selected URL,
bucket, prefix or filesystem path. Storage adapters validate quarantine-key syntax and keep deletion
inside their configured private bucket/root. Repeating deletion of an absent object is successful.

## Race safety and database safeguards

The PostgreSQL V16 migration validates sizes, state combinations, ownership, times and exact key identity.
Publication into Evidence or a direct object Job takes the **same generation row lock** as cleanup.
Either publication wins and cleanup observes a reference, or cleanup wins and publication is denied.
Referenced generations cannot be reclaimed. `DELETING` and `TOMBSTONED` cannot transition back to
`RESERVED`; permanent generation rows cannot be removed. A provider delete result from an old cleanup
attempt cannot complete a newer attempt because application confirmation checks the attempt number.

Historical deterministic shared keys and historical write intents remain supported. They are never
automatically adopted or deleted: an age-only inventory cannot establish ownership when another upload
may reference the same bytes. An operator must inventory and migrate them with verified ownership.
They still consume quota: since historical lengths are unknown, FILE evidence is conservatively charged
10 MiB and fetched URL evidence 2 MiB per Evidence row, plus one object each, until audited adoption
records an exact safe size. Active and failed references both count; old data cannot bypass new quotas.

The runtime application assumes trusted server components respect bounded I/O. S3 timeout/cancellation
does not prove that a remote server canceled a PUT. The drain window conservatively retains quota for
bounded in-flight operations; permanent reconciliation handles a later ambiguous PUT. Thus deletion
protects all referenced data synchronously, while physical orphan cleanup is eventual. Deployment
validation must measure the selected provider's completion/cancellation behavior and set drain/retention
accordingly. A provider that accepts indefinitely delayed writes can transiently recreate orphan bytes
after quota release; it still cannot make those bytes eligible for processing. Bucket quotas and lifecycle
rules are additional operational controls, with rules restricted to the orphan policy rather than all
quarantine objects.

## Operational configuration

| Property under `cadence.evidence.lifecycle` | Default | Purpose |
|---|---:|---|
| `max-workspace-bytes` | 536870912 | Retained bytes plus pending upper-bound reservations |
| `max-workspace-objects` | 1000 | Retained objects plus unresolved reservations |
| `max-workspace-writes-per-hour` | 100 | Sliding-hour provider-cost cap; failed attempts count |
| `write-deadline` | PT2M | Last time a reservation can publish its reference |
| `orphan-retention` | PT24H | Additional retention after expiry before initial cleanup |
| `deletion-drain` | PT5M | Minimum delay before repeat deletion releases quota |
| `tombstone-sweep` | PT1H | Periodic repeat deletion of permanently fenced generations |
| `cleanup-batch` | 20 | Maximum claimed generations per worker invocation |

Configuration is rejected for nonpositive/unbounded limits, write deadlines outside 1 second–10 minutes,
drain shorter than the write deadline, retention above 365 days, sweeps above 7 days, or batches above 100.
Production adapters must have operation deadlines below the reservation's write deadline. Multiple
instances share database locking; no process-local quota counter is used. Repository quota limits protect
the application contract, and database constraints independently protect ownership and lifecycle integrity.
The migration/runtime roles must be separated in deployment: ordinary API users have no direct SQL access.

`EvidenceRegistrationQuota` separately protects durable evidence-row capacity, including TEXT and URL
records whose fetch has not started. Under the same workspace-first lock order, properties
`cadence.evidence.registration.max-workspace-evidence` (default 1000) and
`max-workspace-registrations-per-hour` (default 100) constrain new rows. Existing idempotent registrations
return the same Evidence/Job even at that limit. The database transaction rolls back rejected registrations
and emits controlled HTTP 429 without a new Evidence/Job or a Location header. A larger row cap alone
does not grant additional byte/object/provider-write quota, and an object cap alone cannot constrain
unlimited TEXT or pending URL rows. `EvidenceRegistrationQuotaContract` checks the two layers separately
on H2 and PostgreSQL, including concurrent Brands, shared TEXT/URL budget, historical row capacity,
duplicates at the limit, tenant isolation, archived workspaces, anonymous denial and the HTTP 429 boundary.

### Transaction isolation

Workspace row locking and count-based quota checks require `READ_COMMITTED`. Under PostgreSQL
`REPEATABLE_READ`, a waiting transaction can keep its original snapshot even after acquiring the
workspace lock: locking an unchanged workspace row does not refresh counts of newly committed
reservations or Evidence rows. That can let two requests consume the same final quota slot.

`QuarantineLifecycle.reserve` explicitly starts a new `READ_COMMITTED` transaction. TEXT/URL
registration, FILE publication, approved-social publication, and cleanup transaction templates also
explicitly choose `READ_COMMITTED`, independently of the datasource's default isolation setting.
The application port `EvidenceTransactionIsolation`, implemented by infrastructure adapter
`JdbcEvidenceTransactionIsolation`, checks the actual transaction-bound JDBC connection before workspace
locking/counts. A registration joining an existing `REPEATABLE_READ` transaction is rejected with a
fixed error instead of silently inheriting its stale snapshot. The caller must open a compatible
transaction; the application does not weaken unrelated transactions globally.

The PostgreSQL quota suites deliberately configure the datasource default to `REPEATABLE_READ`.
Their concurrency regressions hold the workspace row through an independent JDBC connection, wait
until both contenders are really blocked according to `pg_stat_activity`, then release it and require
exactly one quota acceptance. An additional negative test rejects inherited `REPEATABLE_READ`
registration without creating Evidence or Jobs. Cleanup uses a fresh statement snapshot after its
generation lock, including when a raw-SQL publication retained a reference without changing the
generation state; immutable tombstones and publication locks remain independent protections.

## Threat model

1. **Protected data:** quarantine bytes, tenant ownership, Evidence provenance, quota and deletion state.
2. **Caller:** authenticated OWNER within an authorized workspace; scheduled workers are trusted services.
3. **Trust boundary:** HTTP input before authorization/validation, private storage/scanner responses, and
   durable state read by concurrent workers. External responses never authorize a deletion or publication.
4. **Attack attempts:** cross-tenant key substitution, replay after lease expiry, quota races, retries to
   avoid cost caps, deletion of a shared key, oversized reservations, secret-bearing provider errors.
5. **Protections:** UUID generation keys, workspace locking, persisted sliding-hour accounting, exact
   reference and byte checks, terminal tombstones, bounded batches, safe provider error logging.
6. **Single-layer bypass:** PostgreSQL ownership/state/check constraints and publication row locks reject
   forged raw-SQL references; cleanup also checks existing Evidence/intents/jobs before provider I/O.
7. **Defense in depth:** request authorization → application ownership/quotas → transactional domain
   state → database provenance guard → adapter path/bucket validation → permanent reconciliation.

Authentication and authorization belong to the public use case. These lifecycle services are internal
infrastructure/application capabilities, not public endpoints. No dependencies were added for this slice.

## Verification

`QuarantineLifecycleContract` runs the same meaningful scenarios on H2 and migrated PostgreSQL:
simultaneous quota reservations across two Brands, tenant isolation, inherited-transaction rejection,
expired publication, rollback of Evidence/Job/reference, referenced-object retention, cleanup claim fencing,
late-PUT reconciliation, retained quota during delete failures, concurrent cleanup, active-job retention,
hourly cost caps including tombstones, oversized and archived-workspace rejection.

`PostgreSqlQuarantineLifecycleTest` adds raw-SQL negative tests for publication after cleanup, cross-Brand
and untracked generation references, removal/reopening of tombstones, forged sizes, and reclamation of
referenced objects. `QuarantineObjectTest` checks exact deadline boundaries and configuration limits.
Actual execution results are recorded in the final issue completion report; this document describes the
contract and test coverage without claiming tests were run before they execute.

## Deployment validation

Verify private bucket access, least-privilege read/write/delete credentials, endpoint TLS, provider IAM,
S3 consistency/timeout semantics, bucket capacity, retention approval, cleanup scheduling and alerts.
Monitor unresolved bytes/objects, expired generations, oldest pending delete and failed cleanup count.
Test recovery after process termination during PUT, delete and database commit against the real provider.
Referenced-Evidence deletion and adoption of pre-V16 shared keys require their own audited operations.
