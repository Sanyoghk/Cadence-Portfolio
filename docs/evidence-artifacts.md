# Isolated evidence artifacts

## Result and source boundary

The durable `EVIDENCE_DOCUMENT_EXTRACT` job produces an immutable `EvidenceArtifact` after source
bytes have a persisted current `CLEAN` malware observation. URL `text/html` produces a private PNG
screenshot (1280 × 720); FILE `application/pdf` produces text bounded to 16000 UTF-16 code units.
Existing PNG/JPEG registrations retain typed metadata without executing a document renderer. Screenshot
bytes remain quarantined; neither a CLEAN source nor a rendered image is an automatic release or an AI
analysis authorization. No new public object/download API is introduced by this slice.

`EvidenceRenderer` is a bytes-only port, implemented by a private isolated renderer adapter. It accepts
already-fetched UTF-8 HTML or PDF bytes, never an external URL/path. Source reads and checksums come
from authorized persisted Evidence, not request parameters. The renderer must disable script execution
and external resource loading, run without credentials, use a nonroot process, and bound request/output,
process memory/CPU/concurrency and total deadline. A missing adapter fails closed through the disabled
renderer rather than executing documents inside the API process.

The current renderer hardening keeps a sole non-dumpable token-bearing server UID10001/container
PID1, with mandatory startup negative preflight before token/HTTP and a separate token-free readiness
container. Jobs get distinct user/net/PID/mount/IPC namespaces while preserving inherited Docker
readonly/masked proc protections. Parent proc environment/memory/FD access must actually be denied;
Linux negative tests also deny native ptrace/process-VM access to the non-dumpable parent.
Trusted child Python PID1 sets dumpable0 before reading input or launching a parser. Normal completion
permits bounded purge of both `/tmp` and `/dev/shm`, also performed before each globally serialized job.
Any worker timeout, signalled unshare supervisor (`returncode < 0`) or unexpected killpg/reap failure
poisons the service before cleanup;
purge and future tenants are forbidden until full container recreation with fresh tmpfs. Reaping the outer
unshare wrapper does not prove namespace termination because its kill-child PDEATHSIG relay is mutable.
Cleanup failure also poisons the service. A slow/hostile document can require operator recovery; monitor 503.
Startup rejects a writable image root or invalid tmpfs flags/sizes: `/tmp`≤128MiB and
`/dev/shm`≤256MiB are private rw,noexec,nosuid,nodev tmpfs; `/dev/mqueue` is masked with readonly
mode 000 tmpfs≤4KiB. The trusted launcher must never bind host directories onto cleanup roots.
Each pre/post purge has a combined-root 2s budget; worker 10s and HTTP watchdog 20s bound execution.
The corrected revision passed all 29 Linux Python tests, including PID1/timeout/signalled/killpg/reap
negatives, and a real 1280×720 Chromium screenshot after child protection. Preflight/AppArmor compilation
also previously passed. Full hardened Maven593 tests passed with zero failures/errors; the3 Windows
filesystem skips ran separately in Linux35/35 with zero skips. Fresh-JAR Compose startup also passed:
app/gateway/renderer-ready healthy, health200 and unauthenticated workspaces401.
[CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248), source SHA
`ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab`, passed enforcing AppArmor load, namespace preflight,
real Chromium screenshot, full Maven593 with zero failures/errors/skips and Python29/29.
External production-host validation remains a separate deployment gate. See the
[renderer contract and deployment restrictions](isolated-evidence-rendering.md).

## Runtime flow

```
Persisted CLEAN scan + explicit artifact feature policy
    → durable extraction Job commit
    → claim Job/Evidence row locks and snapshot scan/source identity
    → screenshot-only quota reservation commit
    → bounded private read + SHA-256 verification
    → strict HTML Unicode normalization or original PDF bytes
    → private isolated renderer
    → renderer response bounds / mutation / PNG-header validation
    → screenshot private storage I/O
    → recheck Job lease + source version/checksum + latest CLEAN observation
    → Artifact + screenshot generation reference + successful Job commit
```

All network, scanner, renderer and storage I/O runs outside database transactions.
`EvidenceArtifactWorker.runOnce()` rejects an inherited transaction before I/O. Claims use a two-minute
lease and bounded retry policy (10 seconds to 1 minute, maximum attempts from the durable Job).

Completion compares the captured owner, attempt, unexpired lease, exact source reference/SHA-256,
Evidence optimistic-lock version and latest scan result ID. An archived source, new observation, changed
source or reclaimed lease prevents publication of the old result. A screenshot already stored by that
losing attempt remains an orphan reservation for the race-safe lifecycle worker.

## Artifact provenance and contracts

Every row records parent Evidence, source checksum/version, exact CLEAN scan result, extraction Job,
attempt/lease owner, renderer version, production time and output checksum. Screenshots additionally
record the exact private generation reference and fixed pixel dimensions. PDF text records its bounded
string and SHA-256 of UTF-8 text; image-only PDFs may validly produce an empty string.

The source's latest scan must be CLEAN for its current bytes/version and no older than
`cadence.evidence.artifacts.max-scan-age` (default `PT24H`, allowed 1 second–7 days). A stale observation
requires a fresh scan before a new extraction; an old positive observation cannot override a later
negative/unavailable observation. PostgreSQL also enforces a seven-day hard upper limit independently
of shorter application policy.

Renderer records bound screenshot output to 3 MiB and document text to 16000 code units. The worker
independently verifies PNG magic, `IHDR` dimensions against 1280 × 720, and source-input immutability.
This is a bounded output contract check, not a substitute for a full image/content safety decision;
screenshots remain quarantined. Text rejects forbidden control characters and unpaired Unicode
surrogates, while retaining tabs/newlines and valid supplementary characters such as emoji.

Screenshots use `QuarantineLifecycle.reserve(..., 3 MiB, "png")` before renderer I/O. Reservation upper
bounds and hourly writes count toward the parent workspace budget. `referenceArtifact` requires the
artifact transaction, locks the generation, checks exact reference and parent Brand/workspace, then
retains actual screenshot bytes. Failed writes and rolled-back derivatives retain quota until orphan
cleanup. Existing source and derivative generations have independent ownership and checksums.

The unique `(evidence_id, source_checksum, source_version, kind)` key prevents duplicate artifacts.
An idempotent job sees an existing artifact and finishes without rendering again. There is no
overwrite or mutable renderer result: a future source/version must produce another provenance row.

## PostgreSQL defense in depth

V17 adds `evidence_artifacts` and an append-only trigger. Inserts independently validate:

- parent source/type/version/current checksum and supported PDF/HTML media type;
- exact latest CLEAN scan identity/source version/checksum and chronological freshness;
- running extraction Job, payload binding, matching attempt/owner and unexpired lease;
- fixed screenshot dimensions or bounded PDF text shape;
- private screenshot generation is RESERVED, owned by the source Brand and not expired.

Screenshot publication and cleanup acquire the same generation row lock. Lifecycle validation accepts
the artifact's reference as an owned derivative of its parent Evidence, and both application cleanup and
database lifecycle guards reject reclaiming any referenced artifact. UPDATE/DELETE cannot change or
remove artifact history through raw SQL. Historical records are not rewritten.

Artifact claim and completion templates explicitly use `READ_COMMITTED`, even if the datasource
default is `REPEATABLE_READ`. After acquiring the source Evidence lock, the latest-scan query must
see observations committed while that lock was awaited. A later non-CLEAN observation can append
without changing the source row; a transaction-wide stale snapshot could otherwise miss that
revocation. The source lock serializes scan publication and artifact completion, and the fresh
statement snapshot then supplies the current latest observation for both application and database
provenance validation.

## Threat model and security review

1. **Protected data:** source bytes and malware decisions, tenant ownership, derivative bytes/text and
   their audit identity, quotas and private infrastructure credentials.
2. **Caller:** trusted scheduled worker after an authenticated ingestion request; no external user can
   select a renderer input URL or a storage generation.
3. **Trust boundaries:** private object-storage bytes, private renderer responses, concurrent database
   state and artifact publication. A renderer result alone is not an authority to release content.
4. **Attacks:** render before CLEAN, substitute source bytes, exploit parser/HTML/PDF execution, escape
   renderer via embedded URLs, consume CPU/memory, forge large PNG dimensions/text, mutate an input,
   publish after a revoked lease or later negative scan, substitute another tenant's storage reference.
5. **Protection:** current-scan/source fencing, independent checksum/bounds, private isolated bytes-only
   renderer, network/script restrictions, quotas/deadlines, exact generation ownership, append-only SQL
   provenance and private quarantine retention.
6. **Single-layer bypass:** source/output checks and PostgreSQL publication/lease/ownership guards
   remain independently enforced. A missing or malformed renderer response produces a bounded retry
   and no artifact. A successfully written losing screenshot is reclaimed as an orphan.
7. **Defense in depth:** authenticated ingestion → durable CLEAN observation → source and lease checks
   → isolated process → independent response validation → transactional immutable provenance → storage
   generation and database locks → periodic orphan reconciliation.

Provider exceptions become fixed `Evidence rendering unavailable` job errors. Raw endpoint URLs,
credentials, rendered content and document text are not logged. This slice adds no parsing-library
dependency to the API process; renderer dependencies belong to its separate image and security scan.
Future APIs must use DTOs, authorization and escaped text rendering rather than expose these entities.

## Verification

The shared `EvidenceArtifactWorkerContract` runs on H2 and migrated PostgreSQL. It covers PDF Unicode
and empty text, private screenshots/quota ownership, non-CLEAN source refusal, checksum substitution,
renderer mutation/control-character/oversized-text rejection, sanitized provider errors, new scan and
source archive during rendering, forged PNG dimensions, mismatched storage generation, inherited
transactions, concurrent worker claims and reclaimed-lease fencing. PostgreSQL-specific tests cover
append-only artifact history and raw-SQL refusal to reclaim referenced screenshot generations.

Targeted command:

```powershell
.\mvnw.cmd '-Dtest=EvidenceArtifactWorkerTest,PostgreSqlEvidenceArtifactWorkerTest' test
```

Actual test execution results are recorded by the issue completion report. The suite's storage/renderer
mocks test publication races deterministically; private adapter/container tests separately verify real
MinIO and isolated renderer behavior. A mocked CLEAN record never proves production ClamAV connectivity.

## Production validation

Enable artifact scheduling only with the real isolated renderer profile and explicit approved provider
configuration. Validate private connectivity/TLS or enforced private network policy, DNS pinning and
egress denial, image provenance/security scanning, nonroot filesystem/capabilities, seccomp/container
limits, request concurrency/total deadlines, renderer API compatibility and ClamAV signature freshness.
Confirm the sum of source read, renderer and screenshot storage bounds fits the reservation/Job deadline.
Test the real provider and recovery after crashes between screenshot PUT and database commit.

The renderer sandbox's operating-system protections, production IAM, network rules and actual deployed
capacity require deployment validation; local deterministic tests prove the application contract and
database races, not those external infrastructure properties.
