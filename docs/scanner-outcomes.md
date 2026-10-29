# Typed evidence scanner outcomes — #117

## Delivered behavior

File registration requires an explicit `CLEAN` result from `EvidenceMalwareScanner.scan(byte[])` before storage, evidence persistence or Job enqueueing. The scanner receives bytes only; the original filename is no longer a scanner argument. The port returns a closed enum instead of arbitrary provider messages.

| Result | HTTP behavior | Side effects for this request |
| --- | --- | --- |
| CLEAN | 202 with existing evidence/Job contract | Quarantined PENDING evidence and idempotent ingest Job |
| INFECTED | 400, fixed rejection message | No storage, evidence or Job |
| UNAVAILABLE / ERROR | 503 Problem Details, fixed retry-later message | No storage, evidence or Job |
| null / adapter RuntimeException | Same safe 503 | No storage, evidence or Job |

An explicit CLEAN verdict is only the registration gate. It does not approve evidence, mark it READY or prove it is safe for AI/rendering. The local/test adapter remains an EICAR-marker fixture, not a real antivirus. The production adapter returns UNAVAILABLE and production storage still rejects all writes. Previously the disabled production scanner raised a generic 409; it now yields a controlled 503 indicating that scanning is unavailable.

## Architecture and runtime flow

`Bearer JWT → local OWNER + Workspace authorization → active Brand/Workspace → file size/type validation → typed scanner result → quarantine storage → evidence + durable ingest Job → 202`

The application use case enforces the allow condition; adapters classify scan outcomes; the presentation layer maps a dedicated application exception to 503. Raw scanner exceptions are discarded at the port boundary, without retaining their message or cause. Existing generic handlers otherwise remain unchanged.

No new dependency, database column or migration is needed for this contract slice. It intentionally does not persist a scan assertion that lacks checksum/engine/time provenance. Scan persistence belongs to the subsequent durable-scanning design.

## Threat model and Security Review

- Assets: uploaded bytes, tenant isolation, database/storage capacity and private provider details.
- Caller: authenticated OWNER of the target Workspace. Scanner responses and exceptions cross the provider trust boundary.
- Threat: scanner outage, malformed result or exception treated as success. Protection: only CLEAN allows registration; all other outcomes stop before side effects. Verification: HTTP tests for UNAVAILABLE, ERROR, null and three exception types, asserting unchanged evidence/Job counts and no storage calls.
- Threat: exception text discloses provider credentials, internal host or filename. Protection: fixed application exception with no provider cause; fixed Problem Details. Verification: injected sensitive fixture strings are absent from the response and the resolved exception has no cause.
- Threat: cross-Workspace, unauthenticated or reserved-role upload invokes an expensive scanner. Protection: existing controller/use-case authorization precedes scanning. Verification: 401/403 tests and no scanner/storage interactions; existing direct-bean/scoped repository tests remain active.
- Threat: CLEAN promotes unreviewed content to READY. Protection: existing quarantined PENDING registration semantics. Verification: clean upload response and idempotency test.
- Defense in depth: HTTP/method authorization, scoped lookup, active-state checks, file validation, explicit verdict gate and quarantine. Compromising a real scanner still requires independent content-policy controls; CLEAN is not a universal safety assertion.
- No SQL/command execution, new URL target, public download, credential or dependency is introduced. Raw provider failures are not logged. Structured safe diagnostic codes/metrics remain future work.

## Actual verification

`.\mvnw.cmd spotless:apply verify` — BUILD SUCCESS: 225 tests, zero failures/errors, six PostgreSQL/Testcontainers skips without local Docker; 219 executed tests passed. Spotless, JaCoCo and test-profile Spring startup passed.

New coverage: 12 HTTP integration cases and two adapter/profile tests. Cases cover clean/idempotent PENDING registration, infected file, unavailable/error/null, three exception types, OWNER/EDITOR/VIEWER/foreign/unauthenticated access, MIME mismatch, development fixture behavior and production-only fail-closed bean selection.

Remote CI is verified separately after push. Local skips are not PostgreSQL passes.

## Remaining work / production blockers

- `registerFile` still performs local scanner/storage work inside its DB transaction. Do not plug in network adapters before transaction restructuring.
- Bounded quarantine reads ([contract](quarantine-reading.md)) and durable FILE/URL scan Jobs with V14 Job/source/engine/signature provenance and retry/fencing ([FILE flow](durable-file-scanning.md), [URL flow](url-malware-scanning.md)) are implemented. Race-safe orphan cleanup remains pending.
- Real object storage/scanner integration, deadlines, signature freshness, quotas and retention remain unimplemented. Current tests use deterministic adapters/mocks and prove the contract, not antivirus effectiveness.
- URL evidence scanning, READY eligibility and provider activation remain separate work. #117 remains open.

## Learning points

- Fail-closed means lack of proof stops the operation, even when the dependency fails unexpectedly.
- A port result reports provider observations; the use case decides whether they permit a business operation.
- A scanner verdict and a reviewed content state answer different questions and must not be conflated.
