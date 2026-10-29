# Storage/scanner design — #117

Status (2026-10-01): S3-compatible storage, private clamd scanning, signature freshness, durable
FILE/URL processing, quotas, orphan retention/reconciliation and isolated screenshot/PDF extraction
are implemented. Real local interoperability is verified; external production deployment qualification
remains separate. Hosting, region, budget and data-location requirements have not been selected.

## Verified current behavior

- EvidenceObjectStorage supports immutable generation writes and bounded checksum-verified reads.
  S3EvidenceObjectStorage implements the private S3 contract; MinIO is an explicit nonproduction
  fixture. Internal lifecycle deletion operates only on database-selected, fenced generations.
  See [storage](s3-storage.md) and [lifecycle](quarantine-lifecycle.md).
- EvidenceMalwareScanner.scan returns a structured CLEAN/INFECTED/UNAVAILABLE/ERROR report with bounded engine/signature identifiers. FILE/URL Jobs persist source, attempt and scanner provenance. See [implemented contract and verification](scanner-outcomes.md).
- registerFile performs scanning and storage outside database transactions, with short scoped checks and locked final registration. See [upload boundaries and recovery](file-upload-transactions.md).
- URL Jobs fetch and extract metadata outside transactions, then atomically record FETCHED, Job success and a separate durable malware scan Job using lease/attempt fencing. The scan worker verifies the stored URL bytes with the same bounded/fenced pipeline as FILE evidence ([contract](url-malware-scanning.md)).
- ClamdEvidenceMalwareScanner streams bounded bytes over a pinned private connection, validates
  complete responses and the same fresh signature VERSION before/after scanning, and never turns
  timeout/error/uncertainty into CLEAN. See [clamd contract](clamd-scanning.md).
- EvidenceArtifactWorker processes current CLEAN sources through an isolated Chromium/Poppler
  sidecar and binds immutable artifacts to source checksum/version, scan and Job attempt.
  Screenshots remain private quarantine; extraction does not approve publication or content safety.
  See [artifacts](evidence-artifacts.md) and [sandbox](isolated-evidence-rendering.md).
- FETCHED remains quarantine. Bare `prod` retains disabled evidence providers; explicitly configured
  production adapters require `s3,clamd`, operational acknowledgements, and `renderer` for artifacts.
  No public object download endpoint exists.

## Implemented execution boundaries

1. Preserve OWNER authorization, Workspace scope, archive guards, MIME/magic-byte limits and server-generated object keys.
2. Authenticate/validate and obtain a minimal immutable operation context in a short transaction.
3. Perform bounded quarantine I/O outside DB transactions. Client input never selects storage endpoint, bucket, credentials or absolute key prefix.
4. Persist evidence and enqueue a durable scan operation in a short transaction, rechecking ownership/archive/version where state could change during I/O. Unique constraints and idempotency remain authoritative. Before expensive I/O, commit a quota reservation for a unique generation; rollback leaves a tracked orphan. Cleanup fences publication before deletion, retains quota through a drain window and permanently reconciles tombstones. Historical shared checksum keys are never deleted blindly.
5. Worker reads immutable quarantined content with a size cap, verifies checksum and scans the exact bytes. Define typed outcomes: CLEAN, INFECTED, UNAVAILABLE/ERROR. Only CLEAN is a successful scan; malformed responses, timeouts, signature-database problems and scan-size-limit responses cannot be interpreted as clean.
6. Record scan verdict, source checksum, engine/signature version, scan time and safe reason code under existing attempt fencing. Current CLEAN permits bounded isolated extraction; content approval, public serving and Brand Identity approval remain separate decisions.
7. Render fixed-size static HTML screenshots or extract bounded text from the first 20 PDF pages.
   The sidecar has no parser network access, JavaScript or unsafe sandbox fallback. Output reads pin
   the original temporary directory descriptor and reject links/special files. Java validates bounded
   PNG pixels/CRC/dimensions or UTF-16 text before fenced persistence.

The current corrected renderer revision retains inherited Docker proc readonly/masked mounts,
requires a sole non-dumpable UID10001/server PID1 with mandatory startup parent-access/namespace
negative preflight, and moves readiness polling to a token-free container. Each globally serialized
job gets distinct user/net/PID/mount/IPC namespaces. Trusted child Python PID1 sets dumpable0 before
input/parser. Normal completion permits bounded purge of both `/tmp` and `/dev/shm`; any worker timeout,
signalled unshare supervisor (`returncode < 0`) or unexpected killpg/reap failure poisons before cleanup
and forbids future tenants.
Outer wrapper reap cannot prove namespace termination through mutable PDEATHSIG. Full container
recreation with fresh tmpfs is required; slow/hostile documents can require operator recovery and503 alerts.
Startup requires a read-only image root, bounded private `/tmp` and
`/dev/shm` tmpfs, and a readonly mode 000 `/dev/mqueue` mask; the trusted launcher forbids host binds
on those roots. Native ptrace/process-VM parent-access negatives, filesystem guards and cleanup
contracts and new PID1/timeout/signalled/killpg/reap negatives are included in the corrected
**29/29 Linux Python pass with zero skips**. Real Chromium screenshot 1280×720 passed again after
child protection. Namespace preflight and scoped AppArmor compilation also previously passed.
The complete hardened Maven gate passed:593 tests,0 failures/errors,590passed and3 Windows filesystem
skips; Linux filesystem35/35 passed without skips. Full provider7/7 and renderer integration6/6 passed;
mandatory report gates passed without required skips.
[CI36833733248](https://github.com/Sanyoghk/Cadence/actions/runs/36833733248), source SHA
`ef22ce9d7bf018170eb61c0207f4e4aa0f9106ab`, passed enforcing AppArmor load, namespace preflight,
real Chromium screenshot, full Maven593 with zero failures/errors/skips and Python29/29.
External production-host validation remains separate. Fresh-JAR Compose startup passed:
app/gateway/renderer-ready healthy, health200 and
unauthenticated workspaces401. Earlier slice counts remain
historical. See [renderer contract](isolated-evidence-rendering.md).

Ports and forward migrations through V21 implement these boundaries; V1–V15 remain unchanged.
Existing evidence without matching scan provenance remains unverified; there is no automatic
backfill or promotion to READY. TEXT/pending URL admission, byte/object/write reservations and
approved social-provider attempts have separate durable budgets.

## Provider decision to resolve

| Option | Advantages | Operational obligations / constraints |
| --- | --- | --- |
| Managed object storage + privately operated scanner | Separate storage durability from scanning; scanner protocol can remain behind a port | Operate scanner updates, health/resource limits and private connectivity; select region and budget |
| Object storage + managed scanning service | Scanner operations delegated | Verify data transfer/retention, supported formats, size limits, verdict semantics, region, pricing and authentication |
| Self-hosted object storage + scanner | Control deployment location and network | Own storage durability, replication, backup/restore, monitoring and signature updates; do not infer low total cost |

The implemented contract is S3-compatible storage plus private ClamAV; local tests use MinIO.
The commercial storage vendor remains an operational choice. S3 compatibility does not imply
identical privacy, encryption, conditional writes, IAM, versioning or regional guarantees.
The selected provider must pass its own qualification before production activation.

## Threat model and required controls

- Assets: tenant files, private source content, credentials, DB connections, storage capacity and scan provenance.
- Callers: authenticated OWNER for registration; internal workers for remote storage/scan operations.
- Boundaries: request bytes, configured provider endpoints, object-store responses, scanner responses and completion transaction.
- Attacks: cross-tenant keys/IDs, public bucket exposure, provider SSRF, malicious files, huge/compressed payloads, scanner outage as bypass, stale completion, checksum substitution and expensive repeated scans.
- Controls: server-selected keys/providers, scoped queries, private objects, least-privilege credentials, verified TLS/private scanner transport, size/concurrency/deadline limits, content checksum verification, typed fail-closed outcomes, DB constraints and fenced writes.
- Defense in depth: HTTP/domain validation, independent adapter bounds, storage isolation, scan verdict binding, transactional checks and no raw rendering/download by default.

Do not send filenames as shell commands or ask a remote scanner to open caller-supplied filesystem paths. If using ClamAV, stream bounded bytes through a restricted local/private channel and explicitly assess transport authentication; a raw daemon port must not be exposed publicly.

## Verification and deployment qualification

- Domain: valid transitions; no READY promotion merely from CLEAN; required provenance; no partial/contradictory verdict.
- Adapter contract: clean/infected/error, truncated/oversized response, unsupported command, timeout, unavailable service, checksum mismatch, bounded stream and credential redaction.
- Security: unauthenticated/non-OWNER/cross-Workspace registration denied; arbitrary keys/endpoints rejected; scanner failure leaves data quarantined; no external network I/O inside DB transactions.
- Persistence/worker: idempotent registration, duplicate scans, lease expiration, stale completion, archive/ownership change during I/O, rollback/orphan behavior and PostgreSQL forward migration with existing data.
- Real integration: storage/scanner container or provider sandbox tests with benign fixtures and standard antivirus test signatures; do not use real malware or user data. CI must demonstrate real adapters, not only mocks.
- Production activation: provider setup, private access, credentials, TLS, quotas, signature freshness, retention and recovery verified separately. No paid services or deployment are authorized by this design document.

Confirmed local command:

```powershell
.\mvnw.cmd '-Dtest=RendererEvidenceAdapterTest,RendererContainerIntegrationTest,ClamdContainerIntegrationTest,FullEvidenceProviderIntegrationTest' test
```

The completed provider gate ran **20 tests, 0 failures, 0 errors, 0 skips**. It included real
PostgreSQL/Flyway V1–V21, least-privilege MinIO, ClamAV and Chromium/Poppler: PDF text, private
website screenshot, canonical EICAR attachment rejection and encrypted-PDF rejection. The renderer
container also executed all seven Python boundary tests on Linux without skips. Only public website
download is an offline byte fixture. Scanner freshness uses a test Clock anchored to the real loaded
signature VERSION; durable receipts and leases use the actual PostgreSQL clock.

The final hardened gate also verified runtime-database-role regressions: Maven593 tests passed,
Linux renderer Python29/29 and scripts10/10 passed. The targeted20 above remains historical;
see the [current completion report](issue-117-completion.md) for exact commands and deployment boundaries.

Production activation requires verified TLS/private transport, bucket privacy/IAM, fresh signatures
and updater monitoring, chosen region/retention policy, ingress/resource limits, deployed namespace
support, live authentication/provider credentials and backup/recovery. `production-approved`,
`egress-policy-acknowledged` and `retention-policy-acknowledged` are startup gates; setting a boolean
does not itself prove that the corresponding infrastructure has been qualified. S3 HTTP and MinIO's
synthetic-ACL compatibility are technically prohibited in `prod`. Clamd/renderer ports must remain
private; multi-host traffic needs an authenticated encrypted channel.

## Historical hosting research (2026-09-12)

The following dated research is retained as background, not a current price quotation, provider
selection or deployment approval. It does not describe unfinished adapter implementation.

| Configuration | Cost basis | Trade-off |
| --- | --- | --- |
| Local development/CI: S3 contract fixture + real ClamAV container | No new hosting subscription; existing machine/CI resources still have costs and limits | Implemented local verification; does not qualify a commercial provider or provide hosting |
| Small hosted pilot: Hetzner CX33 + R2 Standard + private ClamAV | Published CX33 EU price EUR 8.49/month excluding VAT and IPv4; 4 vCPU, 8 GB RAM, 80 GB disk. Availability must be checked; the fetched product page marks it unavailable. R2 Standard includes 10 GB-month, 1 million Class A and 10 million Class B operations monthly; above allowances storage is USD 0.015/GB-month, operations charged separately | Operate app, PostgreSQL, backups and scanner yourself. One VM is a single failure point. R2 jurisdiction must satisfy the eventual data-location requirement |
| AWS pilot: S3 + EC2/private ClamAV, optionally RDS PostgreSQL | Quote only after selecting region, instance sizes, availability, storage, requests and transfer; no defensible total yet | Consolidates infrastructure/provider permissions, but requires more cloud configuration. Managed PostgreSQL reduces database administration, not scanner administration |

For illustration, 100 GB-month in R2 Standard costs USD 1.35 for storage after its 10 GB-month allowance, assuming the allowance is otherwise unused. Requests, other services and taxes are separate. These are component prices, not a complete Cadence budget. AI/API usage, backups, monitoring, domain, IPv4 and tax are not included. Recheck prices and capacity before provisioning.

ClamAV's Docker documentation recommends at least 3 GiB RAM, preferably 4 GiB. An 8 GB VM is only a pilot sizing hypothesis for sharing with Java and PostgreSQL; validate concurrency, signature reloads and memory limits before using it. Never expose raw clamd TCP publicly.

Local adapter implementation is complete. A local MinIO fixture cannot prove R2/AWS compatibility:
run the same bounded contract against the chosen provider, inspect privacy/TLS/IAM/versioning and
qualify the operational policy before activation. No server purchase is needed to demonstrate the
implemented local pipeline.

External hosting selection and deployment qualification remain open. They are separate from the
implemented adapters, signature-freshness policy and race-safe orphan reconciliation. The detailed
operator checklist is in [evidence operations](evidence-operations.md).

## Sources

- [S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html)
- [R2 data location](https://developers.cloudflare.com/r2/reference/data-location/)
- [ClamD protocol](https://docs.clamav.net/manual/Usage/ClamdProtocol.html)
- [ClamAV Docker memory requirements](https://docs.clamav.net/manual/Installing/Docker.html)
- [R2 pricing](https://developers.cloudflare.com/r2/pricing/)
- [Hetzner June 2026 pricing](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/)
- [Hetzner cost-optimized server specifications and availability](https://www.hetzner.com/cloud/cost-optimized/)
- [AWS S3 pricing dimensions](https://aws.amazon.com/s3/pricing/)

The original September proposal was documentation only. The current adapter and local verification
contracts are linked above; external production qualification remains a distinct operational gate.
