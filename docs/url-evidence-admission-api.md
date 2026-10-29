# URL textual review/admission API

Scope03, [ADR0021 policy](adr/0021-source-kind-ai-eligibility.md), [ADR0022 implementation](adr/0022-url-text-admission.md).
Все actions требуют authenticated actual active OWNER/member. Responses `Cache-Control: no-store`.

| Method/path | Поведение |
| --- | --- |
| GET `/api/v1/evidence/{evidenceId}/url-review` | Physical verified exact textual preview; не consent |
| POST `/api/v1/evidence/{evidenceId}/url-review/admissions` | Explicit consent к точным selectors;201 |
| GET `/api/v1/evidence/{evidenceId}/url-review/admissions/{admissionId}/snapshot` | Current exact receipt + JSON projection; physical/source/access checks повторяются |
| POST `/api/v1/evidence/{evidenceId}/ai-admission/{admissionId}/withdrawals` | Общий append-only withdrawal всей sourceVersion;201; stale/archived input можно отозвать |

POST body содержит только: snapshotId,latestScanId,sourceVersion,brandVersion,workspaceVersion,
sourceChecksum,contentChecksum,scanExpiresAt,policyVersion=`url-text-review-v1`. Все mandatory.
Exact selectors берутся из preview; actor/time server-side. Unknown properties запрещены.
Клиент не передаёт URL/content/role/storage key. Preview дополнительно показывает exact metadata,
warnings, fetchJobId/fetchAttempt, fetchedChecksum/fetchedAt/extractedAt и checkedAt.

`text` — canonical `url-text-v1` JSON string, не raw HTML. Projection и UTF-8 hash одинаковы в
preview/consent/current read. Admission возвращает id, immutable source/context/hash/actor/time,
snapshotId, originalScanId/latestScanId/expiry и `aiConsumptionEnabled=true`. Lease/storage refs
не выходят наружу. Human review не является malware certification или privacy approval #120.

400 malformed/unknown input;401 anonymous;403 role/actor/current membership;404 scoped missing;
409 unsupported/legacy/stale/withdrawn/expired/replay/history cap;503 bounded storage/extraction
verification unavailable. Общий Problem Details не раскрывает internals. Source/context/scan
может измениться после ответа: subsequent consumers04 обязаны повторять собственные guards.

Re-review разрешён при новом parent context или latest CLEAN/expiry context, если sourceVersion
не withdrawn. Старые receipts сохраняются; duplicate tuple409. Max1000/evidence. Нет URL editing,
refresh/regrant API и silent legacy backfill. Deduplicated old registration остаётся old acquisition.
`FETCHED` и `CLEAN` сами по себе admission не создают. Screenshot/visual review сюда не входит.

## Consumer04

URL textual generation/Identity подключены по [ADR0023](adr/0023-url-analysis-identity.md).
`aiConsumptionEnabled=true` означает поддержку consumer, а не разрешение live processor egress.
Exact admission/scan/context guards продолжают применяться; legacy URL без snapshot ineligible.
