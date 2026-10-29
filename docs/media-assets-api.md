# Private demo media API — task17

[ADR0034](adr/0034-private-demo-media.md), [OpenAPI](../src/main/resources/openapi/cadence.json).
Actual active OWNER/account/membership required; foreign/missing resources404, reserved roles403,
unknown fields/malformed/format/range/page input400, stale/unverified/archived state409, unavailable
physical prerequisites/capacity503. No provider exception details, object keys, buckets or paths.

## Supported scope

Local/test only, including explicit denial for prod+local/test: exact authored IMAGE_V1 PNG512×512
and VIDEO_V1 MP4 H.264360×640/2seconds. Every artifact visibly says DEMO / NOT AI GENERATED.
Origin DEMO_FIXTURE; development scan warning explicitly differs from real clamd qualification.
No actual generated assets, AI/moderation success, measured usage/cost or publishing is invented.
Arbitrary uploads/JPEG/provider bytes/remote URLs are unsupported here; a live producer plus isolated
decoder/normalizer/moderation/scan policy requires its own qualification. Evidence uploads/AI inputs
continue under existing contracts; no source kind is removed from approved MVP scope.

## Create → preview → review → reference → approve

`POST /api/v1/brands/{brandId}/media-assets`:

```json
{"fixture":"VIDEO_V1"}
```

Returns201, Location `/api/v1/media-assets/{assetId}` and MediaDetails. No filename, URL, actor,
checksum, provider or status accepted. Persisted STAGING intent reserves bytes/count before private
I/O; complete verified bytes and actual scan receipt become AVAILABLE/PENDING. Scan failures never
become usable. Crash/revocation/uncertain PUT leaves inaccessible retained intent/object; no automatic
retry or quota refund. Create has no invented Idempotency-Key contract; a new request is a new asset.

`GET /api/v1/brands/{brandId}/media-assets?page=0&size=20` and
`GET /api/v1/media-assets/{assetId}` expose bounded metadata/audit: exact fixture/origin/checksum/
format/size/dimensions/duration, scan engine/signature/result/time, actual requested/review/archive
actors/times and lockVersion. `deadline` is initial ingestion completion deadline, not a delivery TTL.
Metadata remains readable under actual permissions after archive; raw storage references stay private.

`GET /api/v1/media-assets/{assetId}/preview` allows verified CLEAN AVAILABLE bytes for explicit
unapproved owner review. `GET /api/v1/media-assets/{assetId}/bytes` requires APPROVED_DEMO; PENDING
cannot be delivered as an approved asset or attached. Both recheck actual access/parents/asset
version after physical read/checksum verification, use bounded whole objects and return no-store,
private, nosniff, sandbox CSP, attachment server UUID filename and explicit origin/moderation headers.
Range rejects400; no redirect, presigned URL or cached304 bypass. New requests after revocation fail;
bytes already released after a valid final check cannot be recalled.

`POST /api/v1/media-assets/{assetId}/review`:

```json
{"expectedVersion":1,"approved":true}
```

Separate human decision after physical verification. Records APPROVED_DEMO or REJECTED and actual
actor/time. Earlier receipt is immutable. True is not provider moderation. Review is one-way;
replay/stale/current already-reviewed state409. Use returned versions rather than assuming numbers.

`PUT /api/v1/posts/{postId}/revisions/{revisionId}/media`:

```json
{"expectedVersion":0,"assetIds":["00000000-0000-0000-0000-000000000001"]}
```

Same Brand, actual reviewed/available assets only. IMAGE one PNG; CAROUSEL2–10 distinct PNGs;
VIDEO/REEL one MP4; STORY one PNG or MP4, retaining ADR0031 platform/format checks. Internal demo
rules are not a claim about current external publishing limits. Empty list explicitly removes refs
from a DRAFT and makes no media-readiness claim; old Posts without media retain prior behavior.

DRAFT replacement200 increments revision lockVersion/human edit audit. APPROVED replacement201
creates a new DRAFT/revisedFrom under the same Post identity; old approved references never change.
PostResponse adds `mediaAssetIds` only when nonempty, preserving old empty-media JSON shape/digests.
Manual content edit inherits compatible verified media; incompatible/missing/archived refs require
explicit removal/replacement. Existing Post approval repeats kind/physical/currentness checks.
CONTENT regeneration intentionally creates a new DRAFT without carrying old media; previous revision
and receipts retain refs. Calendar scheduling remains separate editorial metadata, never publishing.

## Archive, retention and bounds

`POST /api/v1/media-assets/{assetId}/archive` body `{"expectedVersion":2}` irreversibly revokes
preview/bytes/new use. Exact original metadata/review/scan and Post references remain. Existing
approved history stays immutable; removing archived media creates a new DRAFT. A late staging
completion cannot overwrite archive, and a late approval cannot replace a newer Post selection.

Hard delete is deferred by existing product policy. All assets/failed/staging reservations retain
bytes and count; archive does not refund them. No cleanup reuses IDs or deletes referenced history.
S3 IAM grants Put/Get on `media/*`, no Delete. Existing quarantine cleanup has no media capability.
Caps:10MiB/object,1000 retained intents/100MiB per Brand,120s ingestion validity,2 concurrent creates
plus2 physical verifications per application instance,30s verification validity, page0–10000/size1–100. Empty-media operations
do not consume media I/O slots. Adapter I/O timeouts remain bounded separately; expiry never authorizes
a late result. Real load, deployment IAM/TLS and live media adapter qualification remain separate.

Task18 must capture exact revision/media identities/provenance and repeat current eligibility and
physical checks for export. No exported public URLs or media moderation policy is introduced here.
