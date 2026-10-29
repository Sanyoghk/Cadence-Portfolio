# ADR0034 — private demo media and immutable Post references

Дата2026-10-16. Task17, #98; prerequisite14–15 at clean mvp/f029531 verified,16 preserved.
Technical decisions inside demo scope, not a live media/provider/privacy/cost decision.

## Goal / current architecture / proposed architecture

Existing approved Post revisions and CONTENT jobs carry mediaPrompt, no asset bytes or moderation.
Storage exposes private quarantine, scanners consume bounded bytes, renderer handles screenshots/PDF.
No general video decoder or live media adapter exists. Add a separate private `media/` namespace
using reviewed filesystem/S3 primitives, MediaAsset lifecycle, strict metadata/delivery DTO and
ordered Post revision references. Never admit a quarantine URL/reference as an approved asset.

Only fixed server-owned PNG/MP4 demo fixtures are admitted in this slice: exact immutable catalog
checksum/size/format/dimensions/duration, visibly watermarked DEMO / NOT AI GENERATED. They are
authored static fixtures, not generated images/videos. Arbitrary uploads/provider bytes remain
unsupported until isolated decoding/normalization and provider/privacy/cost qualification. No
OCR/vision/moderation observations are invented. MP4 generated once with Ubuntu FFmpeg dev tool;
backend runtime gains no codec dependency. Actual fixture codec/dimensions verified with ffprobe.
Production creation/delivery/attachment of demo media disabled, including mixed prod+local/test.

## Lifecycle / provenance / human decision

STAGING reserves exact bytes/object quota before private storage I/O. Store→read-back verification→
bytes-only malware scan occurs outside DB transactions. Completion rechecks actual access/parents,
exact staging version and a bounded deadline, then records immutable receipt. Failure never becomes
CLEAN/usable. Crash leaves STAGING inaccessible and quota retained; no silent retry/publication.
Known staging bytes may be reconciled by explicit archive; no automatic quota release or deletion.

Verified CLEAN bytes become AVAILABLE with moderation PENDING. Dedicated owner preview is allowed
only after physical verification/CLEAN and is explicitly unapproved preview. Human review records
actual actor/time and APPROVED_DEMO or REJECTED; no provider moderation claim. Only APPROVED_DEMO
can be attached/delivered as a Post asset. Scan engine/signature/checksum/time are preserved;
development scanner clearly identified as fixture, not real security qualification. Immutable known
demo bytes have no invented scan TTL; real clamd enforces its configured signature freshness at scan.
Future arbitrary/live assets require their own rescan/expiry/decoder/moderation policy.

Archive is irreversible and denies preview/delivery/new attachment/approval; metadata and historical
references remain. All bytes, including failed/staging objects, retained with quota: current product
policy defers hard delete until retention approval. No public delete, storage path or URL capability.
Technical caps:10MiB/object,1000 assets and100MiB reserved bytes/Brand,2 concurrent creates plus2 verifications per application instance (empty-media paths do not consume I/O slots),
bounded I/O deadline; list/history page1–100. Caps are operational, not pricing/product guarantees.

## Post references and platform validation

Refs belong to exact revision, same Brand, unique ordered0–10 assets. SQL uniqueness of selected
asset IDs is deferred to commit so a valid carousel reorder does not fail on Hibernate intermediate
row updates; duplicate final selections remain rejected. Empty references preserve
TEXT-only/editorial compatibility and mean no media readiness claim. Internal format compatibility:
IMAGE one PNG; CAROUSEL2–10 PNGs; VIDEO/REEL one MP4; STORY one PNG or MP4. Platform/format rules
from ADR0031 retained. These are demo editorial rules, not external publishing API limits.

DRAFT asset replacement increments revision version and real human edit audit; APPROVED replacement
creates a new DRAFT with same Post identity/revisedFrom and frozen old references. Manual content
edit preserves compatible refs and physically verifies them; incompatible/archived/missing refs
require explicit replacement/removal. Human approval repeats exact asset/lifecycle/physical checks.
Content regeneration creates a fresh DRAFT without automatically reusing old media; old receipt/
references stay historical. New DRAFT invalidates old schedule by ADR0033; schedule READY remains
editorial, media/export checks are separate. No platform publishing or actual generated asset claim.

Capture→bounded private read/hash/catalog validation→recapture+commit prevents access/archive/
replacement races, with external I/O outside locks. Account→Workspace→Brand→Strategy→Post→Asset
lock order; asset-only operations use Account→Workspace→Brand→Asset. No source model receives bytes.

## Delivery / trust boundaries / alternatives

Owner-scoped metadata and bounded whole-object bytes; no arbitrary URLs, buckets, filenames or
presigned URLs. Preview separate from approved bytes endpoint. Cache-Control private,no-store;
nosniff, attachment with server UUID filename, explicit demo/moderation headers. Range unsupported;
no conditional cache304 bypass. Recheck permissions/parents/asset version after bytes I/O and before
release; revoke after final authorized snapshot is a later operation, not a promise to recall bytes.

Compared: public/static URLs leak tenant data and bypass revocation; quarantine refs conflate CLEAN
with admission; new blob implementations duplicate security boundaries. Reusing private storage
with separate typed key/namespace and independent asset admission preserves tested path/SSRF/S3
guards. Costs: extra metadata/receipt/locks and conservative retained quota, explicit preview/review.

Threat model: protect tenant bytes/history/approval/provenance. Hostile clients may forge ownership,
actor/status/checksum, inject URL/path, substitute malformed/oversized bytes, race revocation or
erase referenced history. Strict DTO+actual OWNER/scope, server catalog/key, physical hash validation,
explicit human review, SQL FKs/checks/immutable guards/runtime grants and fenced completion defend
each boundary. No single CLEAN flag authorizes delivery. No new runtime dependency or debug bypass.

Components: domain asset/catalog/refs, private storage port and existing FS/S3, application capture/
verification/use cases, Post lifecycle, media controller/DTO/OpenAPI, V50/runtime grants.
Testing: domain state/format/caps; real HTTP auth/unknown/foreign/missing/unverified/size/format;
PostgreSQL SQL/runtime/migration and archive/access/replacement races; existing storage/renderer/
full Maven/Python/Linux/startup and dependency qualification. Final results belong to report17.
