# Strategy REST API — #102 / complete Playbook13

Дата: 2026-10-05. Контракт дополняет #97 и включает REST lifecycle #102.

## Endpoints

| Operation | Endpoint | Result |
|---|---|---|
| Generated draft create | POST /api/v1/brands/{brandId}/strategies | 201 StrategyResponse + relative Location |
| Revision read | GET /api/v1/strategies/{strategyId} | 200 StrategyResponse |
| Retained history list | GET /api/v1/brands/{brandId}/strategies | 200 StrategyPage |
| Replace editable content | PATCH /api/v1/strategies/{strategyId} | 200 DRAFT update; 201 new DRAFT + Location for APPROVED/LOCKED |
| Approve committed draft | POST /api/v1/strategies/{strategyId}/approval | 200 immutable APPROVED revision |

Все операции требуют authenticated OWNER. Brand routes используют established403
для неизвестного/чужого Brand. Strategy read скрывает foreign/missing ID одинаковым404.
Workspace scope берётся исключительно из CadencePrincipal; request не выбирает его.
GET доступен и для archived parents, поскольку история сохраняется.

POST не принимает content/status/provenance contract: body не потребляется.
Это generation action над последней APPROVED Identity revision ([ADR0026](adr/0026-approved-identity-strategy-source.md)).
Missing/DRAFT/LOCKED latest revision даёт safe409; fallback к старой APPROVED или Brand.signature отсутствует.
Проверяются actual DB active OWNER/membership, ACTIVE Workspace/Brand, parent versions,
Identity ID/version/lockVersion/checksum/approval audit и inherited analysis evidence eligibility.
Snapshot → physical/fresh preflight → model outside transaction → physical/fresh completion → DRAFT.
Новая revision/approval/edit, withdrawal, изменённые scan/expiry/bytes/access отменяют publication.
Модель получает bounded approved Identity content; source IDs/private references ей не передаются.
Capacity2/process, generation deadline60s, physical verification window30s.
Production adapter disabled; fake только local/test; provider failure503, source conflict409.

Synchronous POST сохраняется для совместимости и использует общий durable budget12.
Async POST `/api/v1/brands/{brandId}/strategy-jobs` поддерживает scoped idempotency/Job202
по [durable contract](durable-ai-generation.md). Повторная successful sync POST создаёт другую revision. После timeout
клиенту следует перечитать history перед новым запросом, поскольку прежний commit
мог завершиться. retryable означает возможность повторить операцию, а не exactly-once.
Реальный provider нельзя включать для production до budgets/Jobs/deployment gates.

## DTO и pagination

StrategyResponse содержит id, brandId, workspaceId, version, lockVersion, status,
cadence, contentPillars, nullable generation, revisedFromId, approvedBy, approvedAt,
createdAt, updatedAt. version — номер контента; lockVersion — concurrency token edit/approval.
Generation содержит id/provider/modelVersion/promptVersion/warnings и nullable identitySource:
identityId, version, lockVersion, checksum, approvedBy, approvedAt, nullable analysisId, requestedBy.
Это immutable исторический receipt, не разрешение повторного AI egress. Ручной approved source
имеет analysisId=null. Evidence-derived lineage ссылается на original immutable receipt/citations.
Legacy generated rows имеют identitySource=null; новая migration ничего не backfill.
Legacy/manual revisions имеют generation=null; provenance не выдумывается.
Raw signature, source snapshots, credentials и internal deadlines не возвращаются.
Audit timestamps — UTC Instant; bestTimes — LocalTime в cadence.timezone.

Default pagination: page=0,size=20,sort=version,direction=desc.
page0..10000,size1..100; sort version/createdAt/updatedAt; direction asc/desc.
UUID — tie-breaker. Bounded offset page не обеспечивает snapshot при конкурентной
записи; list содержит все revisions, а не только draft/latest.

ReadStrategyUseCase копирует lazy collections в immutable StrategyDetails внутри
read-only transaction. Controller преобразует snapshot в StrategyResponse уже после
transaction. OSIV=false и Entity fetch policy не расширены для коллекций. На страницу
возможны дополнительные collection queries, но её размер ограничен100. Batch fetching
можно обосновать позднее измерениями; collection join fetch с pagination здесь не нужен.

## Редактирование и approval

PATCH принимает полный replacement **только** editable content, а не частичный JSON Merge Patch:

```json
{"expectedVersion":0,"cadence":{"postsPerWeek":4,"timezone":"Europe/London","bestTimes":["09:15:00","18:30:00"]},"contentPillars":[{"name":"Community","weight":100,"description":"Audience stories"}]}
```

Проверяются 1..10 pillars, уникальные case-insensitive names, сумма weights=100,
weights0..100; trimmed nonblank name≤255/description≤1000 без control characters;
postsPerWeek1..21; IANA timezone; 1..7 unique LocalTime с precision≤microsecond.
HTTP DTO bounds и StrategyContent независимо защищают вход application/domain layer.
Unknown JSON fields Strategy PATCH отклоняются, включая nested v2 records и cadence/pillars.
id/status/version/provenance/lineage/actor/time отсутствуют в writable DTO.

expectedVersion — **lockVersion** полученной StrategyResponse, не business version.
Stale token →409; invalid/missing input →400; foreign/missing Strategy →404;
EDITOR/VIEWER →403; no Bearer →401. Ошибки используют safe Problem Details/no-store.
ACTIVE Workspace и Brand обязательны для writes; история archived parents остаётся readable.

DRAFT изменяется на месте с увеличением lockVersion. APPROVED/LOCKED остаётся неизменным;
PATCH создаёт новый DRAFT max(brand.version)+1, revisedFromId=source.id, audit=NULL,
generation=NULL. Старый immutable generation receipt остаётся у source: ручная редакция
не выдаётся за новый model output. Несколько edits одной approved revision создают
отдельные branches; это разрешено. Repeated PATCH approved source не idempotent.

Approval body: `{"expectedVersion":1}`. Actor берётся из authenticated CadencePrincipal,
time — server Clock. Проверяется текущий active OWNER и DB membership, затем committed
DRAFT content. Повторное approval →409. Это отдельная REQUIRES_NEW/READ_COMMITTED
transaction; replacement collections не объединяются с approval. Клиент сначала PATCH,
затем подтверждает **возвращённый** lockVersion. Чужой ID и отсутствующий ID дают одинаковый404.

Короткие locks: edit Workspace→Brand→Strategy; approval Account→Workspace→Brand→Strategy.
Brand lock общий с #97 completion: generation и revision allocation не сталкиваются
по unique(brand_id,version). PostgreSQL V27 защищает lineage, actor/time и deferred
content checks вместе с V24 history/collection guards. Runtime роль получает UPDATE
только approved_by/approved_at дополнительно к прежним editable columns.

V27 не придумывает actor/time/lineage для старых записей: legacy approved audit=NULL.
Их provenance/history сохраняются; новое approval требует complete validated content.
DB проверки modern content относятся к generated/revised/newly-approved rows;
старые manual drafts доступны для нормализации через PATCH перед approval.

Broader Playbook fields, UI, approved/versioned Brand Identity source link, citations,
durable AI Jobs/budgets и real adapters остаются отдельным scope #107/#120.
Архитектура: [ADR-0011](adr/0011-strategy-rest-lifecycle.md).

## Complete Playbook v2 — задание13

Новая generation (sync/async) требует `StrategyGenerationResult.playbook` format2. Существующие
GET/list paths возвращают `contentFormatVersion`, `completeness`, nullable `playbook`,
`editedBy`, `editedAt`, `readyContentSource`. Original generation содержит nullable
`contentFormatVersion` и `outputChecksum`; checksum относится к исходному model output,
а не к последующей human редакции. Branch сохраняет original source через revisedFromId.

PATCH envelope остаётся `expectedVersion`, `cadence`, `contentPillars` + optional `playbook`.
`playbook` format2: schemaVersion, pillarIds (соответствуют contentPillars по порядку),
audienceAssumptions, channels/goals, toneSpectrum, competitiveWhitespace, fourWeekOutline.
Полная schema и bounds: [ADR0029](adr/0029-full-playbook-contract.md), OpenAPI PlaybookContent.

- v1 revision + old content PATCH → прежний partial contract; no fabricated fields.
- v1 revision + full playbook → explicit upgrade, in-place DRAFT или new DRAFT для approved/locked.
- v2 revision + missing playbook →409; unknown fields/invalid bounds →400.
- v2 full PATCH → atomic replacement; approved branch →201+Location; stale expectedVersion →409.
- human edit получает actual current OWNER/time; clients не могут назначить audit/provider.
- approval проверяет полный committed aggregate и current actor/parents; approved immutable.

`readyContentSource=true` означает complete APPROVED v2 с approval audit. Он не заменяет
будущий Content authorization/capture contract14–15 и не разрешает автоматический AI egress.
Audience/whitespace — assumptions/hypotheses; human review проверяет смысл. Week1 не имеет
календарной даты до Content planning; timezone/bestTimes — рекомендации, не publishing commands.

Migration V46, runtime grants и inherited history квалифицируются в [report13](prompt13-playbook-report.md).
