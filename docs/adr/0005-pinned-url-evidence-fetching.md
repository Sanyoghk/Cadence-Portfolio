# ADR-0005: Pin URL evidence connections to validated public IP addresses

Status: Accepted

Date: 2026-09-11

## Context

Issue #117 has URL registration and durable Jobs, but string validation cannot stop DNS rebinding or redirects to internal services. A separate DNS check followed by an ordinary hostname-based HTTP client introduces a second, potentially different lookup. The JDK HTTP client does not expose a per-request DNS resolver suitable for pinning here.

## Decision

Keep a provider-neutral `UrlEvidenceFetcher` application port. The JDK adapter validates every resolved address, then connects a direct socket to the selected `InetAddress`. HTTPS layers the standard JSSE socket over that connection, with the original hostname, HTTPS endpoint identification, default trust validation and SNI. No proxy, credentials, cookies, authenticator, connection reuse or automatic redirect handling is used.

The small HTTP/1.1 GET transport deliberately supports a restricted subset: status 200, `text/html` or `text/plain`, identity content encoding, and unambiguous content-length/chunked/connection-close framing. It does not execute HTML, parse XML, decompress content or load subresources. Ambiguous framing and unsupported content fail closed. This avoids a new dependency at the cost of owning a limited HTTP parser and reduced website compatibility; a future mature transport must retain connection-time IP pinning and the negative fixtures before replacement.

Limits per fetch: HTTP/HTTPS on their standard ports only; three redirects; 2 MiB body; 32 KiB cumulative header/chunk-metadata budget; 8 KiB lines; 8192 chunks; 3-second DNS/connect timeout; 5-second socket read timeout; 30-second total fetch deadline including redirects/TLS. A deadline closes the socket even when data arrives slowly. DNS uses two daemon threads and a bounded queue of two tasks: a resolver that ignores interruption cannot create unbounded threads or waiting work. Fetch concurrency is capped at two per adapter instance. All DNS answers must be public; mixed public/private answers fail closed. IPv6 transition/special ranges and known cloud infrastructure IPs are denied conservatively. HTTPS-to-HTTP downgrade redirects are rejected.

The worker claims only URL ingest jobs, including previously registered jobs. It preserves the existing three-attempt policy with 10/20-second retry delays. Timeouts, DNS/network failures, HTTP 408/429/5xx and storage unavailability are retryable; unsafe targets, malformed responses, body/content/redirect limits, TLS errors and other HTTP statuses are permanent. Only fixed safe errors reach Job/evidence records.

Network/storage run outside DB transactions. Claim and completion use short transactions, pessimistic Job locking, a unique lease owner per attempt, attempt-number fencing and expiry checks. Evidence and Job completion commit together. Expired final attempts reconcile to terminal evidence failure; generic queue recovery to DEAD is also reconciled. Already fetched evidence completes duplicate jobs without fetching again. Concurrent attempts after lease loss can leave unreferenced quarantine objects, but cannot overwrite committed evidence provenance. Storage keys use evidence UUID plus content checksum, never a remote filename.

V9 adds separate fetched provenance and a DB constraint. The original registration checksum and source URL stay unchanged for idempotency. `PENDING -> FETCHED` means bytes are quarantined, **not** extracted, malware-cleared or safe to render. `PENDING -> FAILED` happens on permanent failure/retry exhaustion. Retries keep evidence PENDING. No transition back from terminal states is exposed. Fetched storage references/body are not added to API DTOs. Website extraction remains a separate outstanding slice.

## Threat model

1. Assets: internal services and cloud credentials; server resources; tenant evidence/provenance and Job integrity.
2. Caller: a Workspace-authorized creator registers an untrusted URL; only the internal worker executes it.
3. Trust boundaries: database source URL, DNS results, each redirect, remote headers/body and storage adapter output.
4. Attacks: loopback/private/metadata access, alternate IP notation, DNS rebinding, redirect pivot, credential forwarding, slow/large responses, compression bombs, malicious HTML, duplicate/stale worker completion.
5. Controls: independent URL/IP checks per hop, pinned TCP destination, standard TLS validation, no forwarded credentials, fixed bounds, quarantine, atomic fenced completion and bounded retries.
6. If registration validation is bypassed, the adapter still rejects the target. If an attempt loses its lease, its completion cannot mutate evidence. If content validation is bypassed, quarantine and the absence of a rendering endpoint still prevent treating raw HTML as trusted content.
7. Defense in depth: API authorization, adapter policy, socket pinning, domain transitions, DB constraints, optimistic/pessimistic concurrency controls; production must additionally deny private/metadata egress at the network layer. A publicly numbered address routed internally by deployment networking cannot be detected from address classification alone.

## Historical slice operations and remaining gates

Storage/scanner/lifecycle/extraction gates below describe the original URL slice. They are superseded by [ADR-0007](0007-private-evidence-pipeline.md) and the [current completion report](../issue-117-completion.md).

`CADENCE_URL_FETCH_WORKER_ENABLED=true` enables one scheduled poll every five seconds; disabled by default. Local/test storage supports development. The production object-storage gate remains fail-closed; production Object Storage and Malware Scanner, quarantine retention/orphan cleanup, egress policy, per-tenant quotas, metrics/alerts and scraping/privacy approval are not delivered by this slice. Enabling the worker with the production placeholder storage will fail and exhaust retries; do not enable it until storage and operational gates are satisfied.

At-least-once execution can repeat a remote GET after a process crash; exactly-once HTTP execution is not promised. Evidence completion is fenced/idempotent. The existing generic queue should not be used to dispatch URL jobs to another worker. Text/file extraction and social adapters remain pending.

## Verification

Deterministic tests cover public and prohibited IPs, mixed DNS answers, DNS rebinding, redirect pivots/limits, HTTP errors, DNS/read deadlines, framing/content/body limits, credential-free requests, TLS hostname/SNI configuration, concurrent claim, duplicate jobs, retries/exhaustion, stale lease completion, terminal states, archived Brands and cross-Workspace API denial. PostgreSQL/Testcontainers fixtures cover V9 mapping, URL-job selection and the fetched-metadata DB constraint; they require Docker and must not be reported as passed when skipped.

References: [JDK 21 layered SSL sockets](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/net/ssl/SSLSocketFactory.html), [JSSE endpoint identification](https://docs.oracle.com/en/java/javase/21/security/java-secure-socket-extension-jsse-reference-guide.html), [IANA IPv4 special-purpose registry](https://www.iana.org/assignments/iana-ipv4-special-registry/).

## DNS admission regression — 2026-10-01

`Future.get()` может вернуть результат до того, как worker снова ожидает новую задачу.
`SynchronousQueue` поэтому могла отклонить следующий быстрый redirect при свободном
native DNS budget. Ограниченная очередь на две задачи устраняет эту гонку; cancelled
tasks удаляются через `purge()`, threads и DNS/overall deadlines сохранены.
Deterministic regression задерживает оба workers после completion и проверяет queued
redirect, исходные четыре transport calls и `TOO_MANY_REDIRECTS`. Отдельный saturation
test подтверждает 2 running + 2 queued и rejection следующего task.
Фактическая команда:
`.\mvnw.cmd --batch-mode --no-transfer-progress '-Dtest=JdkUrlEvidenceFetcherTest,RendererEvidenceAdapterTest,ClamdEvidenceMalwareScannerTest' spotless:apply test` — 81 tests, 0 failures/errors/skips.
