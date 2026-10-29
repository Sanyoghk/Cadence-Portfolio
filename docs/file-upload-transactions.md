# File upload transaction boundaries — #117

## Delivered behavior and architecture

FILE registration retains its HTTP 202 response, followable Job Location, OWNER authorization, MIME/size checks, synchronous CLEAN requirement and quarantine. Scanner and object-storage I/O now run outside database transactions. No dependency, migration or production activation is added.

Runtime flow:

Bearer authentication → OWNER/scope method authorization → short scoped active-Brand check → bounded owned byte snapshot and MIME/filename validation → scan a separate copy with checksum mutation detection → short locked duplicate check → quarantine write outside transaction if needed → short locked active-Brand/Workspace recheck → evidence and Job committed together.

The use case uses explicit TransactionTemplate boundaries. Propagation.NEVER rejects an inherited transaction instead of suspending it and leaving its connection held during I/O. The existing registration use case remains the application orchestrator; repositories own persistence queries and ports own scanner/storage operations. Text and URL registration retain their existing path.

Finalization locks Brand and its Workspace before checking for an existing checksum. Concurrent FILE uploads can write identical bytes to the same deterministic quarantine key, but their database finalizations serialize and return one evidence and one Job. Existing database uniqueness remains defense in depth. Different uploads within one Workspace briefly contend on the Workspace row; network I/O holds none of these locks. No JVM-only lock or database-vendor-specific upsert is needed.

The submitted byte array is copied after its 10 MiB bound is checked. The scanner receives a separate copy and its checksum is verified after return; mutated CLEAN fails closed. Storage receives another copy. This isolates caller/scanner mutations from the retained upload snapshot. The later durable scan independently verifies stored bytes; registration is not an attestation of a real storage provider's integrity.

The storage adapter must return `quarantine:` followed by the exact server-generated object key. Registration rejects a different reference before committing evidence or a Job. This prevents an adapter error from persisting a reference to another tenant's object. The URL fetch worker applies the same check and retries with a safe error when it fails. The check binds the reference to the requested key; it does not prove that a remote provider stored the supplied bytes, so provider integrity still requires its own contract and tests.

## Threat model / Security Review

Protected assets: tenant files, evidence/Job integrity, database connections and provider details. Callers remain authenticated OWNERs through existing scoped APIs. Trust boundaries include request bytes, adapter replies and database finalization after external I/O.

| Threat | Protection | Verification |
| --- | --- | --- |
| Foreign tenant or reserved role triggers I/O | Existing method/HTTP authorization plus scoped Brand query before scan | Existing HTTP and direct-method negative suites |
| Archive during scan/write bypasses initial validation | Recheck active Brand/Workspace under pessimistic locks before commit | Archive-during-storage tests for both records on H2/PostgreSQL |
| Duplicate concurrent registrations | Deterministic key, locked final recheck, evidence/Job transaction and existing unique constraints | Two uploads rendezvous inside storage and return identical IDs |
| DB connection held through slow adapter | NEVER entry and explicit short transactions | Scanner/storage assertions on real Spring transaction state; inherited transaction rejected |
| Scanner changes content and reports CLEAN | Owned snapshot, separate adapter copy, before/after SHA-256 | Mutation rejected without storage or rows |
| Storage exception leaks secrets or false success | Fixed HTTP 503, no attached provider cause; missing/non-quarantine/oversized reference rejected | HTTP secret fixture and invalid-reference cases |
| Evidence committed without its Job | Same final transaction | Injected enqueue failure rolls evidence back |

Authentication, CORS and CSRF configuration are unchanged: this API uses explicit Bearer headers, not cookie sessions. Queries are parameterized; filenames do not select storage paths. No new public endpoint, credentials, logging payload or dependency is introduced. Retry requests still scan even when an identical evidence record exists; this preserves the existing fail-closed contract and costs scanner work.

## Failure recovery and production considerations

Object storage and PostgreSQL do not share a transaction. If storage succeeds but final registration fails or the process exits, an unreferenced quarantined object can remain. Its deterministic key allows a retry to reuse/replace identical bytes. Do not blindly delete on rollback: a concurrent successful upload may reference the same object. No object deletion capability is added here.

Before production, implement race-safe retention/orphan reconciliation with a grace period and ownership/reference checks, tenant quotas and upload/scanner rate limits. This slice does not cap cumulative orphan storage. Runtime access to quarantine must remain private. Real adapters need bounded deadlines and integrity contracts; scanner engine/signature provenance, monitoring and READY policy remain pending. URL scan chaining is implemented separately. The development scanner is only an EICAR fixture and production storage/scanning remains disabled.

Follow-up: [V12 FILE write intents](quarantine-write-intents.md) now record a durable pre-write attempt and provide read-only unresolved inventory. They do not authorize automatic object deletion; write fencing and deletion recovery remain outstanding.

Membership authorization uses the existing authenticated request scope; this change adds archive revalidation, not a new mid-request membership revocation protocol. Backup/restore, real provider failure behavior and load/throughput remain unverified.

## Tests and verification

Shared FileUploadTransactionContract runs through real Spring proxies and repositories, with scanner/storage mocks, on H2 locally and PostgreSQL/Testcontainers in CI. The PostgreSQL report gate now requires the upload suite as well as persistence and file-worker suites.

EvidenceScanIntegrationTest no longer wraps HTTP requests in a test transaction. The Job Location upload test explicitly opts out of its class test transaction; its fixture is consequently committed before the request. All authorization, response and idempotency assertions remain intact. UseCaseAuthorizationTest supplies the new transaction-manager dependency.

Commands: `.\mvnw.cmd spotless:apply clean verify`; `python -m unittest discover -s scripts -p 'test_*.py'`; `git diff --check`. Actual results are recorded in the task/PR after execution. Local Docker-dependent skips must not be counted as PostgreSQL passes.

Local clean verify passed: 333 reported tests, zero failures/errors, 33 skipped (30 disabled PostgreSQL methods without Docker and three Linux-only fixtures); 300 executed tests passed, including all 12 upload contract cases. Spotless, JaCoCo and Spring startup passed. Five Python gate tests passed. An initial run found a Mockito restubbing error in new fixtures; switching to doAnswer/doThrow preserved every assertion and the complete suite then passed. PostgreSQL results are recorded separately in CI.

## Changed files

- RegisterBrandEvidenceUseCase.java: explicit transaction boundaries, byte ownership, final revalidation and safe adapter failures.
- BrandRepository.java: scoped pessimistic lock of Brand/Workspace.
- EvidenceObjectStorage.java: explicit concurrent deterministic-write and partial-write contract.
- EvidenceStorageUnavailableException.java and ApiExceptionHandler.java: fixed storage-unavailable 503 response.
- FileUploadTransactionContract.java, FileUploadTransactionTest.java, PostgreSqlFileUploadTransactionTest.java: shared concurrency, rollback, archive and transaction checks.
- EvidenceScanIntegrationTest.java: realistic HTTP transaction boundary and storage failure contract.
- JobApiIntegrationTest.java: transaction-free upload/Location scenario.
- UseCaseAuthorizationTest.java: constructor wiring while preserving authorization assertions.
- scripts/verify_postgres_report.py and scripts/test_verify_postgres_report.py: mandatory upload report and negative gate tests.
- README.md, DEVELOPMENT_PLAN.md, DEVELOPMENT_PLAN_RU.md, docs/storage-scanner-design.md, docs/durable-file-scanning.md, docs/quarantine-reading.md and this report: current scope and remaining release gates.

## Learning points

1. A DB transaction cannot roll back an object-store write; recovery must account for both systems.
2. Validation before I/O can become stale; lock and revalidate before committing database effects.
3. Idempotency means retries/concurrent requests share one logical result, even if external work runs twice.
4. Spring transaction annotations work through proxies; real integration tests are needed to prove the boundary.
