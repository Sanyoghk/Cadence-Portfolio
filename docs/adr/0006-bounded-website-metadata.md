# ADR-0006: Extract bounded website metadata without executing HTML

Status: Accepted

Date: 2026-09-11

## Context and decision

Issue #117 calls for normalized website evidence. The existing worker fetches bounded bytes into quarantine but has no metadata extraction. Add a provider-neutral WebsiteMetadataExtractor port returning a validated WebsiteMetadata value. Its jsoup adapter parses downloaded bytes only; it never uses jsoup networking, executes JavaScript, follows links, loads images, renders a browser, or expands XML entities.

Use pinned org.jsoup:jsoup:1.23.2. The JDK does not offer a suitable HTML5 parser; writing another parser or regex-based stripping would increase correctness and security risks. The maintained library supplies malformed HTML recovery and a bounded parser stack. Official [release notes](https://jsoup.org/news/release-1.23.2) and [security advisory](https://github.com/jhy/jsoup/security/advisories/GHSA-pmhh-3w7g-xqp8) were checked: the documented Cleaner issue affects versions through 1.22.2 and is patched in 1.23.1. This implementation does not use Cleaner or produce safe HTML. This review is not a full vulnerability scan; future dependency updates remain necessary.

## Contract and limitations

- At most 2 MiB of text/html or text/plain; independently enforced after the fetch boundary.
- Strict Unicode decoding: UTF-8 without BOM records UTF8_ASSUMED; explicit UTF-8, UTF-16LE and UTF-16BE BOMs record UTF8_BOM, UTF16LE_BOM and UTF16BE_BOM. UTF-32 BOMs and malformed byte/surrogate sequences fail with a fixed permanent error. No heuristic charset guessing or replacement characters. HTTP charset headers and HTML declarations are not interpreted; charset negotiation remains future work. New snapshots use website-v2; old website-v1 snapshots remain unchanged.
- HTML parser depth capped at 128; bounded error tracking (one diagnostic, discarded). HTML_RECOVERED records parser recovery without persisting remote diagnostics.
- Title: 300 UTF-16 units; description: 2000; normalized text: 16000. Truncation preserves surrogate pairs and records TEXT_TRUNCATED; empty text records NO_TEXT. Warnings are fixed codes, never remote messages.
- Standard description meta takes precedence over og:description. Script, style, template, iframe, embedded content, forms and explicit hidden elements are omitted from body text. This is heuristic extraction, not visual rendering: CSS-hidden text can remain and all results stay untrusted.
- Whitespace/control/format characters are normalized. text/plain is kept as literal text even when it contains markup; future consumers must output-encode it and never use it as trusted HTML or AI instructions.
- Metadata is internal; no new public endpoint or DTO fields. Screenshot, PDF/image extraction, social adapters and real AI remain pending.

## Runtime and consistency

OWNER API authorization → durable URL Job → fenced claim → SSRF-safe fetch → bounded local extraction → quarantine storage → fenced transaction recording fetched provenance, metadata and Job success.

Network, parsing and storage run outside database transactions. Existing Job locking, lease-owner/attempt checks and expiry checks gate final writes. Stale/duplicate workers cannot overwrite committed metadata. Invalid encoding/input fails permanently; unexpected adapter exceptions retain bounded retries and safe fixed errors. Extraction failure creates no partial snapshot or successful Job.

V10 adds six nullable columns and an all-or-none constraint tied to URL/FETCHED provenance and capture time. Old PENDING/FETCHED rows remain valid without metadata. No applied migration is changed, and no existing completed source is silently re-fetched or backfilled. Domain rules forbid replacing a recorded snapshot. Metadata is immutable through current application operations; direct database administrators remain trusted.

FETCHED remains quarantined, never READY. Metadata extraction is neither malware clearance, factual validation nor approval. Existing opt-in worker activation and fail-closed production storage/scanner gates remain in force. At-least-once network behavior and potential orphan quarantine objects remain unchanged.

## Threat model

1. Protect server resources, internal network, tenant source data and Job/provenance integrity.
2. OWNER supplies untrusted URLs; an internal worker processes bytes. No new public caller or authorization rule.
3. Trust boundaries: remote bytes, adapter result, storage result and final persisted snapshot.
4. Threats: SSRF through embedded URLs, script/XXE execution, resource exhaustion, malformed encodings, stored XSS/prompt injection, stale completion and partial persistence.
5. Controls: parsing only, existing pinned transport, byte/depth/output limits, fixed diagnostics, validated domain value, unchanged scoped APIs, atomic fenced completion and database constraints.
6. If parsing produces hostile text, it remains internal untrusted quarantine data. Future UI/AI adapters must independently enforce escaping and data/instruction separation. If lease ownership is lost, completion is discarded.
7. Defense in depth: registration and HTTP authorization, connection-time SSRF policy, parser bounds, domain validation, transactional fencing, DB constraints and unchanged production activation gates.

## Verification

Unit tests cover normalized HTML, metadata precedence, non-executed embedded resources, ignored active nodes, malformed/deep markup, literal text, encoding/media/input limits, truncation and Unicode. Worker fixtures cover atomic metadata persistence, duplicate execution, expired leases, permanent extraction failure and existing retries/ownership behavior. PostgreSQL fixtures validate V10 mappings, partial-snapshot rejection, timestamp consistency and a populated V9 → V10 upgrade preserving existing quarantine provenance. Exact run results are tracked with #117 and the implementation report.

## Production considerations

Input/depth bounds reduce parser resource abuse but are not an OS sandbox or a hard CPU deadline. Retain capped worker concurrency and add production quotas, monitoring and resource limits before activation. Object storage/scanner providers, egress policy, retention, screenshots, HTTP charset negotiation/legacy encodings and scraping/provider approval remain open. No deployment or external source scraping is performed by this change.
