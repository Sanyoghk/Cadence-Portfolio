# Metrics import API / recovery

Контракт: [ADR0036](adr/0036-manual-csv-metric-import.md). Только current OWNER, Bearer header,
actual account/membership, ACTIVE Workspace/Brand. Привязка к stable nonlegacy ContentPost;
никакой проверки публикации в Instagram/TikTok и никакого получения данных от платформ.

`POST /api/v1/brands/{brandId}/metric-imports`, `Idempotency-Key: metrics-example-0001`.
Content-Type `application/json` или `text/csv;charset=UTF-8`. Maximum131072bytes/200rows.
Return202, no-store, Location `/api/v1/jobs/{jobId}` и `MetricImportDetails`.

```json
{
  "observations": [{
    "postId": "00000000-0000-0000-0000-000000000001",
    "platform": "INSTAGRAM",
    "windowFrom": "2026-01-01T00:00:00Z",
    "windowUntil": "2026-01-02T00:00:00Z",
    "observedAt": "2026-01-02T01:00:00Z",
    "reach": 120,
    "engagement": null,
    "saves": 0,
    "signups": null,
    "profileToSiteClicks": null,
    "correctionOf": null
  }]
}
```

UUID в примере заменить actual scoped Post UUID. Dates должны быть historical относительно
DB submission time; поддерживаются microseconds. Хотя бы один count обязателен0..10^12.
Missing/null = unknown,0 = reported zero; counts не rates и не lifetime cumulative totals.
CSV exact header (11columns, comma, LF/CRLF, double quoting, UTF-8 без BOM):

```csv
postId,platform,windowFrom,windowUntil,observedAt,reach,engagement,saves,signups,profileToSiteClicks,correctionOf
00000000-0000-0000-0000-000000000001,INSTAGRAM,2026-01-01T00:00:00Z,2026-01-02T00:00:00Z,2026-01-02T01:00:00Z,120,,0,,,
```

Raw bytes/SHA256 retained privately; raw files, storage keys, client filenames/URLs не выдаются.
HTTP413 body oversize;400 malformed input/unknown fields/numbers/encoding/future/platform mismatch;
404 unknown/foreign Post/import/Job;403 wrong role/revoked current membership;409 key payload
conflict/quota/archive/query state;503 parser capacity (max2 parses/instance, fail-closed).
Изменение raw whitespace с тем же scoped key —409: replay только exact bytes+origin.

`GET /api/v1/metric-imports/{importId}` и `GET /api/v1/jobs/{jobId}` возвращают typed
METRICS_IMPORT status: PENDING/RUNNING/FAILED/SUCCEEDED/DEAD, attempts, actor/submission/deadline,
origin/schema/rawChecksum/rowCount, ordered `observationIds` только после atomic success,
safe `errorCode`. Нет частично опубликованного batch. Другой authorized current OWNER той же
Workspace может читать историю; idempotency остаётся scoped к инициатору, а не читателю.

`GET /api/v1/posts/{postId}/metrics?from=2026-01-01T00:00:00Z&until=2026-02-01T00:00:00Z&history=false&page=0&size=50`.
Interval half-open по observedAt,2000..2200/microseconds, максимум366days, page0..10000, size1..100. Ответ `items/page/
size/totalElements`; без history — latest correction каждого natural observation. Каждая item
содержит interval/source/ingestion time, unit COUNT, nullable counts, valueChecksum, revision,
previousId, originImportId и фактического ingestedBy. Legacy post_metrics сюда не включаются.

Correction: новый import/новый key, тот же Post/platform/observedAt, `correctionOf` exact current
observation UUID. Старые values immutable. Same latest values без correction дедуплицируются,
разные values без current correction target дают DEAD/METRICS_INPUT_UNAVAILABLE. Stale или
foreign target также DEAD, весь batch rollback.200/202 не означает verified provider origin.

## Worker runbook

Включить `CADENCE_METRICS_IMPORT_WORKER_ENABLED=true` после migrations/runtime grants и
операционной настройки worker. Нет public claim/debug/cancel endpoint. Worker делает bounded
poll1s; infrastructure poll failure — safe fixed log и30s backoff. Claims сериализованы parent
mutex; один worker/instance, independent instances fenced DB Job lease/attempt. Lease60s,
max3attempts, deadline300s; raw parsing вне DB tx, final authorization/Post mapping и ticket
recheck перед одной transaction публикации. Никаких network/provider calls.

При crash: если transaction не commit — observations/result links отсутствуют; после expiry
следующий worker reclaim и повторяет чистый parser. Если commit уже состоялся — Job SUCCEEDED,
повтор не создаёт результата. Старый worker после lease loss не может publish/перезаписать Job.
Transient DB/parser overload — FAILED/METRICS_RETRYABLE, bounded backoff2..10s; access/archive/
input/correction conflict — permanent DEAD/METRICS_INPUT_UNAVAILABLE. Deadline —
METRICS_DEADLINE_EXCEEDED; exhausted leases — METRICS_LEASE_EXHAUSTED. Данные не редактируются
в recovery SQL: inspect scoped API и safe Job/operation IDs; новая операция с осознанно
исправленным input/new key. Retention/quota сохраняются, failed raw receipt не удаляется молча.

Не суммировать overlapping observations и не превращать null в0 в будущих analytics20.
Live AnalyticsSyncService/account mapping/cursor/partial failures остаются broader #99 scope;
выбор platform/AI/media providers не входит в19. Production rate/load/retention/restore
qualification отдельно; включённый worker не квалифицирует live synchronization.
