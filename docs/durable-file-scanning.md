# Durable FILE scanning and persisted provenance — #117

## Delivered behavior

EvidenceMalwareScanWorker consumes existing BRAND_EVIDENCE_INGEST Jobs associated with FILE evidence and explicit scan Jobs for URL evidence. No FILE registration response or Job Location changes are required. Successful scanning means that quarantined bytes were checksum-verified and a verdict was stored; evidence remains quarantined, never READY.

| Outcome | Job | Evidence | Persisted scan result |
| --- | --- | --- | --- |
| CLEAN | SUCCEEDED | PENDING/quarantined | Yes |
| INFECTED | DEAD | FAILED/quarantined | Yes |
| UNAVAILABLE / ERROR | FAILED, then DEAD on attempt exhaustion | PENDING, then FAILED | Yes, one per observed attempt |
| Temporary read failure | Retry, bounded by maxAttempts | PENDING, then FAILED | No: no scan occurred |
| Invalid/missing/oversized/tampered source | DEAD | FAILED | No |
| Expired/superseded completion | Unchanged by stale worker | Unchanged by stale worker | No |

Archive/source changes prevent accepting an in-flight result. Final expired leases mark evidence FAILED, including when the generic queue already recovered the Job to DEAD. Unsupported/orphan Jobs and TEXT sources are not processed. V13 extends the worker to explicit URL scan Jobs; see [URL malware scanning](url-malware-scanning.md).

## Architecture / runtime flow

Existing OWNER-scoped upload → quarantine + ingest Job → short locked claim transaction → bounded read and checksum scan with no DB transaction → short locked completion transaction → immutable result + Job transition.

Worker entry rejects inherited transactions. Claim selects one due FILE Job, locks it and its evidence/Brand/Workspace ownership graph, checks active state, then records a random lease owner and attempt. Completion locks the same records, rechecks owner, attempt, expiry, source version/checksum/reference and active state. A late CLEAN cannot overwrite a newer INFECTED attempt. Result insertion and Job/evidence changes commit atomically.

Registration retains its synchronous development scan; the subsequent [upload transaction slice](file-upload-transactions.md) moved scanner/storage I/O outside DB transactions. This worker adds durable verification of stored bytes. V13 reuses it for URL fetch-to-scan chaining without changing the FILE flow.

## Persistence and migration

V11 adds evidence_scan_results with evidence/Job foreign keys, unique (job_id, attempt_number), source version, checksum, scan time, lease owner and explicit verdict. V14 adds validated scanner engine and signature-set identifiers. Existing V11–V13 observations are labeled `legacy-unknown`; the migration does not invent a provider or mark old evidence verified. Source references remain on the evidence record; the result binds their evidence version and actual checksum.

V15 permits terminal URL evidence to retain fetched-object provenance after an infected result or exhausted scan retries. It still accepts `FAILED` URL evidence with no fetched fields when fetching itself failed, and still rejects partial fetched metadata.

A PostgreSQL BEFORE INSERT trigger independently validates FILE/PENDING evidence, active Brand/Workspace, matching checksum/source version, corresponding ingest payload, current Job attempt/owner and unexpired lease. V13 extends it to URL/FETCHED evidence and explicit scan Jobs. It takes shared locks while validating; the worker already holds write locks. UPDATE of a result is rejected. Runtime should not have UPDATE/DELETE privileges on this audit table; administrative retention/deletion must remain explicit. Foreign keys prohibit accidental parent deletion; there is no ORM cascade removal. This is not protection against a privileged database administrator.

The worker inserts/flushes the result while the persisted Job is RUNNING, then transitions the Job within the same transaction. H2 validates mappings and application behavior; only PostgreSQL executes this trigger. The preserved V9 → V10 test now explicitly targets V10; a new populated V10 → V11 test verifies unchanged old evidence and direct SQL rejection of wrong checksum/attempt/owner/evidence/verdict, null verdict, duplicates, result edits, parent deletion and expired leases.

`EvidenceMalwareScanner` returns a structured report containing verdict, engine and signature-set identifiers. The durable worker stores all fields in the same append-only observation. Identifiers are bounded and allowlisted in Java and PostgreSQL so raw provider responses, control characters and unbounded data cannot enter audit columns. This is still not a production antivirus attestation: real adapters must supply truthful values and enforce deadlines and signature-freshness policy.

## Security Review

- Assets: tenant source content, integrity of Job/result history, DB connections and scanner capacity.
- Callers: existing OWNER-scoped uploads; internal worker consumes only persisted FILE ingest relations. No new public endpoint or manual Job mutation API.
- Threat: stale/duplicate worker changes the result. Protection: locked claim/completion, random lease owner, attempt/expiry checks, unique result key and atomic transaction. Verification: expiry, concurrent worker and superseded-result tests on H2/PostgreSQL.
- Threat: forged provenance or cross-source association. Protection: domain binding and PostgreSQL trigger/foreign keys; source checksum verified before scan. Verification: tampering test and direct SQL negative fixtures.
- Threat: source archived/changed during I/O. Protection: lock ownership graph and recheck active state/version/reference/checksum. Verification: pre-scan archive, in-flight archive and version change tests.
- Threat: failure interpreted as success or infinite retry. Protection: only CLEAN succeeds; bounded attempts/backoff; read errors never fabricate a scan observation. Verification: all scanner outcomes, retry exhaustion, final lease recovery and checksum mismatch.
- Threat: secrets leak through failures or progress API. Protection: fixed errors and unchanged scoped JobResponse DTO. Verification: injected secret fixture, existing HTTP/method authorization and Job DTO suites.
- Threat: production activation of fixture scanning. Protection: scheduler defaults off and requires local AND NOT prod; production storage remains fail-closed. Verification: prod and local+prod activation tests.
- No new dependency, network destination, shell command or authentication bypass. Database runtime privileges and local filesystem permissions remain operational controls.

## Activation / production considerations

For local development only, use explicit local profile and CADENCE_FILE_SCAN_WORKER_ENABLED=true. Polling is serial with five-second fixed delay; each run processes at most one Job. Lease is two minutes; retries use the existing capped policy, with registration providing at most three attempts. The development scanner is an EICAR-marker fixture, not an antivirus. Do not activate a real network scanner without a bounded deadline comfortably inside the lease.

Production polling cannot activate even if the flag is true. Real storage/scanner adapters, quotas, retention/orphan cleanup, observability, signature updates/freshness policy and approval/READY policy remain release work. Hard deletion and scan-history API remain absent.

Recovery: V11 and V14 are additive; retain rows and apply a forward corrective migration if needed. Do not edit applied migrations or drop audit records to roll back a release. Backup/restore remains a separate operational exercise.

## Actual local verification

`.\mvnw.cmd spotless:apply clean verify`: BUILD SUCCESS; 312 tests reported, zero failures/errors, 25 skips without local Docker/Linux; 287 executed tests passed. Testcontainers skips count disabled parameterized methods differently from an executing suite. All 17 H2 worker cases passed; PostgreSQL suites and Linux symlink cases require remote CI confirmation. Spotless, JaCoCo and test-profile startup passed.

`python -m unittest discover -s scripts -p 'test_*.py'`: five tests passed. The report gate now requires both PostgreSqlPersistenceTest and PostgreSqlFileScanWorkerTest; a missing/skipped worker report fails it. `git diff --check` passed.

An intermediate race fixture caused Mockito recursion while restubbing inside an Answer; it was corrected to doReturn with the same assertions. An intermediate run was stopped; only the completed final clean verify is treated as successful validation.

Initial CI run 34665847293 passed all seven PostgreSQL persistence/migration tests but exposed direct JDBC Instant binding in the shared worker fixtures. Retry/expiry setup now uses java.sql.Timestamp; assertions and production behavior are unchanged. PostgreSQL worker verification requires the subsequent CI run.

## Changed files

- Domain: BrandEvidence.java adds FILE failure transition; ScanVerdict.java and EvidenceScanResult.java define immutable scan observations.
- Persistence: EvidenceScanResultRepository.java stores observations; BrandEvidenceRepository.java locks ownership graph; DurableJobRepository.java selects due FILE Jobs; V11__evidence_scan_results.sql adds table and provenance guards.
- Execution: EvidenceMalwareScanWorker.java orchestrates claim/scan/completion; FileScanScheduling.java retains the compatible local-only opt-in property; application.properties and .env.example document the disabled flag.
- Tests: FileScanWorkerContract.java, FileScanWorkerTest.java, PostgreSqlFileScanWorkerTest.java share H2/PostgreSQL scenarios; EvidenceScanResultTest.java validates domain failures; ConfigurationProfilesTest.java verifies production isolation; PostgreSqlPersistenceTest.java verifies V11 and upgrade constraints.
- CI: scripts/verify_postgres_report.py and scripts/test_verify_postgres_report.py require both PostgreSQL suites.
- Documentation: README.md, DEVELOPMENT_PLAN.md, DEVELOPMENT_PLAN_RU.md, docs/storage-scanner-design.md, docs/quarantine-reading.md, docs/scanner-outcomes.md, docs/job-status-api.md and this report track delivered scope and remaining work.

Learning points: lease fencing prevents a slow old worker from becoming authoritative again; a transaction makes result and Job state one atomic change; a database trigger can enforce cross-row invariants beyond CHECK constraints; quarantine and antivirus verdicts remain separate from content approval.
