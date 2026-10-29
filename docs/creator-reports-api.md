# Creator performance/recommendation reports

Утверждённый product contract: [ADR0039](adr/0039-creator-performance-reports.md).
Implementation22, issue #105. Qualification/results: [report22](prompt22-reports-report.md).

## Submit / status / result / download

```text
POST /api/v1/brands/{brandId}/reports
Idempotency-Key: creator-report-1
Content-Type: application/json

{
  "strategyId": "00000000-0000-0000-0000-000000000001",
  "expectedStrategyVersion": 1,
  "fromDate": "2026-10-01",
  "untilDate": "2026-10-17",
  "timezone": "Europe/London",
  "platform": "INSTAGRAM",
  "bucket": "PERIOD",
  "recommendationIds": []
}
```

202 + `Location: /api/v1/jobs/{jobId}`; same accepted key/payload returns the same operation.
No external AI/model/analytics call. Key1..128 ASCII `[A-Za-z0-9._:-]`, scope actual actor+
Workspace+CREATOR_REPORT+SHA256(key); digest includes Brand and the complete normalized request.
Different payload409, including cross-Brand collision within that scope. Before acceptance,
capture failure rolls back Job/key/result together; it does not claim uncertain provider spend.

```text
GET /api/v1/jobs/{jobId}
GET /api/v1/reports/{id}
GET /api/v1/reports/{id}/result
GET /api/v1/reports/{id}/download
```

Current authenticated OWNER, local membership and ACTIVE Account/Workspace/Brand required.
Foreign/missing report or Job404; role/revocation403; no token401. Strict request DTO excludes
actor/tenant/approval/provider audit writes. No JPA entity/provider payload serialized.
Status contains capture actor/time, IDs/checksums, attempts/controlled error, resultState and
relative result/download paths. It contains no captured input/private storage reference.
JSON `/result` is a dedicated CreatorReportSnapshot; download is escaped static HTML UTF-8.
No arbitrary HTML/URLs/resources/scripts, PDF, email or external delivery. Server-generated UUID
filename, `no-store, private`, CSP `default-src 'none'; sandbox`, nosniff; Range400.
The approved authenticated endpoint explicitly refines old #105 signed-result-URL wording.

## Data and historical semantics

One explicit full approved/locked Strategy with genuine approval, expected lockVersion and
immutable content checksum; latest revision is not silently substituted. Later Strategy lock/
source version change refuses publication/full-result/download. Another new Strategy revision
does not rewrite this historical source. No DRAFT/legacy/unapproved/foreign source acceptance.

Date range≤28 local days, named timezone, local from inclusive/until exclusive, captured UTC
window and tzdb rules version. One platform; report groups by historical pillar of the explicit
Strategy. Existing ADR0037 formulas/sample/freshness rules apply unchanged. DAY/PERIOD exact
windows supported; daily reach is not summed into fabricated unique PERIOD reach. Incompatible
windows, missing/zero/inconsistent/sparse/stale/unknown attribution explicitly disclosed.
NO_DATA is a valid empty historical report with null/undefined rates and no generated advice.

Capture reads at most1000 candidate observations;1001st fails closed. All groups are captured
using bounded pages over the same parent-locked normalized rows; no hidden50-group truncation.
Max1000 groups. Each observation includes exact immutable ID/revision/value checksum, source
and ingestion time/origin, optional historical attribution and Post content revision/checksum.
Raw imports, import checksum/actor, emails/account names, Identity text, prompts and storage
keys are excluded. Rehydration recomputes rates from captured counters before result publication.

Up to10 explicit recommendation IDs, same Brand/Strategy/platform/exact PERIOD/timezone.
Each keeps original eligibility time/source version/snapshot checksum, HEURISTIC/rule/provider,
UNCALIBRATED/HYPOTHESIS/uncertainty and original10..100 supporting observations. These are not
replaced with latest corrections. Relation to report-time selected metrics is explicit:
MATCHES_CAPTURED_METRICS or HISTORICAL_DIFFERS_FROM_CAPTURED_METRICS. Report does not run apply,
claim calibrated probability/causal lift or reevaluate current advice eligibility.

Later metric correction/context edit keeps the existing report bytes and supporting IDs intact.
A new report captures newer metrics and can disclose a different relation to the same old
advice. The result always says IMMUTABLE_HISTORICAL_SNAPSHOT_NOT_CURRENT_ANALYTICS. This is a
descriptive metrics/history export, not an Identity/evidence model input or approval operation;
it contains no withdrawn evidence text/bytes. Source withdrawal still blocks recommendation
generation/apply under ADR0038; reading an old report does not restore eligibility or consent.

## Retention / recovery / bounds

Full JSON result and HTML download available only after successful publication and before
`capturedAt +24h` (exclusive). At expiry, status remains SUCCEEDED with resultState EXPIRED and
no result/download path; replay does not renew lifetime or create a new identity. Bytes and
snapshot/history retained, including failed/expired reports, until a separately approved deletion/
backup policy. Download expiry is not physical deletion. Already downloaded bytes cannot be revoked.

100 retained reports/Brand. Each reserves at most2MiB **combined snapshot+HTML payload**;
snapshot≤1MiB, artifact bounded by remaining reservation. No dependencies or object-store dual
write: private PostgreSQL text/bytea and Job completion commit together. No server filename/key
chosen by client. Capture≤2 concurrent/instance,5s transactions; normalized JDBC query≤3s.

Job CREATOR_REPORT separate from ingest/AI_GENERATION/CSV_EXPORT:3 attempts,60s lease,
5-minute deadline from capture, retry2–10s only for transient pure rendering failures.
No remote provider outcome/spend inferred. New worker can recover expired lease from DB;
lost/late attempt cannot publish. Rendering outside DB transaction; current account/access/
source version/checksum checks before rendering/publication/read/download. Lock order:
Account→Workspace→Brand→source→report→Job. Terminal recovery can lock without granting access.
One polling worker/instance, DB fences across instances. Default disabled; enable explicitly
`CADENCE_REPORTS_WORKER_ENABLED=true` / `cadence.reports.worker-enabled=true`.

503 report_unavailable for bounded admission/query infrastructure failure;409 for invalid
source/state/quota/expired result; invalid body400. Job codes REPORT_RENDER_FAILED (bounded
retry), REPORT_SOURCE_UNAVAILABLE, REPORT_OUTPUT_INVALID, REPORT_DEADLINE_EXCEEDED; unknown
internal reasons mapped to REPORT_FAILED. No raw SQL, exception messages, paths or payload logs.
On outage fix infrastructure then retry the same accepted key; do not delete immutable history
or mark a failed Job successful. Expired/failed retained quota requires a separately reviewed
retention decision, not an undocumented operator deletion bypass.
