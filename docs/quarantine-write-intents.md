# FILE quarantine write intents — #117

## Purpose and runtime flow

Object storage and PostgreSQL cannot commit atomically. A successful storage write followed by a failed registration can leave an unreferenced object. `RegisterBrandEvidenceUseCase.registerFile` now commits a `QuarantineWriteIntent` before calling `EvidenceObjectStorage.storeQuarantined`. Its key is bound to the Brand UUID, SHA-256 digest and detected file extension. The write still runs outside a database transaction. Finalization stores evidence and its ingest Job and marks that specific intent registered in one database transaction. If storage or finalization fails, the intent remains unresolved.

The first locked duplicate check still runs before the intent is created. Concurrent requests that pass that check create separate intents for the same deterministic key; each successful finalization points to the same evidence and Job. A failed request cannot delete an object that another request committed. The V12 migration adds the intent table, Brand/evidence foreign keys, key and completion checks, a trigger binding completed intents to their exact FILE evidence, and indexes for inventory. Existing migrations are unchanged.

`QuarantineWriteIntentRepository.findUnresolvedWithoutReference(cutoff, pageable)` provides a bounded, read-only inventory. It excludes keys still referenced by any evidence source or fetched URL. The caller must choose a grace cutoff and bounded page size. An unresolved row may mean a failed write, a crashed process, or an upload still in progress. It is **not** proof that an object exists or permission to delete it. There is no scheduler, external API or automatic deletion in this slice.

## Security and recovery boundary

- Protected assets: tenant files, referenced quarantine objects, disk capacity and auditability of failed writes. The existing OWNER/Workspace checks run before an intent is created. The key comes from server-side Brand ID, checksum and file type, never from a filename or client path.
- A stale attempt can finish writing after any fixed grace period. Automatic deletion therefore needs a durable per-key deletion state and write fencing: new writers must be excluded while deletion is in progress, and old writers must be unable to commit a reference to a deleted object. A reference query plus a grace period alone cannot provide this guarantee.
- No object deletion is performed on transaction rollback. The inventory query excludes a pending intent if another upload registered the same shared key.
- Retained unresolved intents consume database space. Operational retention and cleanup must be designed with the deletion protocol, including crash recovery and legacy objects written before V12. This change does not cap disk consumption or make production storage/scanning available.
- Do not expose this inventory to tenants or log object keys by default. It contains Brand identifiers and content checksums.

## Verification

The shared H2/PostgreSQL upload contract checks that the intent is visible before storage I/O, successful/duplicate registration, rollback after enqueue failure, and exclusion of a shared referenced key from inventory. Domain tests reject foreign/noncanonical keys and double or invalid completion. PostgreSQL migration tests verify the V12 mapping, foreign-key and `CHECK` constraints. Local Docker skips are not PostgreSQL passes; exact verification results belong to the implementation report.
