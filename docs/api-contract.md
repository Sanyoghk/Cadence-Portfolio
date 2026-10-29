# API contract — issue #111

## Цель и публикация

Текущий additive CSV contract: [private durable export](csv-export-api.md),
[ADR0035](adr/0035-durable-csv-export.md). Three new operations, strict DTO и typed CSV_EXPORT
Job view; old schemas сохранены. Разделы ниже описывают initial #111 baseline, а не отменяют
последующие Identity/Strategy/Content/calendar/media contracts и их actual OpenAPI operations.

Версионируемый contract описывает существующие workflows: Workspace, Brand,
TEXT/URL/FILE/social evidence, ingest Job polling и deterministic sample analysis.
Исходник: [`cadence.json`](../src/main/resources/openapi/cadence.json).
`GET /api/v1/openapi` возвращает этот же OpenAPI 3.1.1 JSON, требует Bearer JWT
и server-side `OWNER`; `Cache-Control: no-store`. Только `/api/v1/health` публичен.
Health подтверждает liveness процесса, а не доступность всех providers.

Новые Identity/Strategy/Post/export/metrics/recommendation APIs реализуются своими
issues. Их нельзя считать существующими только из-за target architecture. Любой
новый `/api/v1` controller обязан расширить OpenAPI и соответствующие tests:
`OpenApiContractTest` сравнивает полный набор HTTP methods/routes с Spring mappings.

Contract выбран как checked-in JSON без нового runtime dependency. Это требует
явного review при изменении API; route/DTO/enum drift и фактические controller
responses проверяются автоматически. `OpenApiContractAssertions` проверяет
используемый subset schema (references, types, required/extra fields, alternatives,
enum, constants, limits, patterns, UUID и UTC). Это не универсальный JSON Schema
validator и не доказательство поддержки любого внешнего client generator.

## Authentication и authorization

Explicit `Authorization: Bearer ...`; session/cookie authentication отсутствует.
Поэтому CSRF token не нужен для текущего Bearer API; добавление cookie auth потребует
нового security review. JWT issuer/audience/authorized-party/expiration проверяет
decoder, роль и membership устанавливаются сервером. CORS не заменяет authorization.
`EDITOR` и `VIEWER` зарезервированы и запрещены для business operations.

- `401`: отсутствующий/invalid/expired token; `WWW-Authenticate: Bearer` с безопасным
  `invalid_token` при необходимости. Malformed Bearer protocol может дать `400`
  с `invalid_request`; decoder diagnostics в header не раскрываются.
- `403`: роль или ownership Workspace/Brand не разрешает операцию.
- Job polling скрывает foreign/missing/orphan/unsupported Jobs одним `404`.
  UUID является идентификатором, а не доказательством доступа.

Проверки выполняются filters → controller method security → scoped use case/repository.
DTO не позволяет менять role/status/lease через mass assignment: unknown request
fields игнорируются Jackson, а состояние меняют только объявленные DTO fields.

## Единый error contract

MVC advice, Bearer entry point/access denied handler, firewall rejection и upload
admission используют `ApiProblems`. Representation — `application/problem+json`,
`Cache-Control: no-store`. Формат основан на [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html).

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "urn:uuid:00000000-0000-0000-0000-000000000111",
  "code": "validation_failed",
  "retryable": false,
  "errors": [{"field": "name", "code": "required"}]
}
```

`instance` — уникальный opaque ID ошибки; не содержит URL/query/PII. Для неожиданного
500 сервер записывает ID и class ошибки, без её message/cause/SQL/input/stack trace.
Полный distributed tracing остаётся в #116. Клиент принимает решения по HTTP status
и `code`; `title/detail` — display text, не строка для программного сравнения.
`errors` присутствует при DTO validation, sorted/distinct, максимум 50 entries:
`required`, `size`, `non_negative`, `type_mismatch`, `invalid`. Rejected values и
произвольные validation messages не возвращаются. Domain/parameter validation без
field binding использует `invalid_request` с фиксированным описанием.

| HTTP | code | retryable | Причина |
|---|---|---|---|
| 400 | invalid_request / malformed_body / validation_failed / evidence_malware_rejected | false | Parameters, JSON, DTO или malware verdict не разрешают input |
| 401 | authentication_required | false | Нужен действующий token |
| 403 | access_denied | false | Role/scope denial |
| 404 | resource_not_found | false | Resource/поддерживаемый Job не найден |
| 405 | method_not_allowed | false | Method не поддерживается; `Allow` сохраняется |
| 406 | not_acceptable | false | Response media недоступен |
| 409 | resource_conflict | false | Version или state conflict |
| 413 | body_too_large | false | Configured body limit |
| 415 | unsupported_media_type | false | Request media не поддерживается |
| 429 | evidence_quota_exceeded | true | Workspace retention/admission quota |
| 500 | internal_error | false | Unexpected failure, безопасное описание |
| 503 | service_unavailable / evidence_storage_unavailable / evidence_scan_unavailable / social_evidence_unavailable / upload_capacity_unavailable | true | Dependency/capacity outage |

`retryable` означает возможность повторить позднее, не немедленный бесконечный retry.
Используйте bounded backoff; quota может потребовать retention/operator action.
`Retry-After: 1` возвращается при upload capacity saturation; остальные 503 могут
его не иметь. Не повторяйте state mutation без учёта idempotency/version contract.

## Pagination и UTC

Один `PageResponse`: `content/page/size/totalElements/totalPages/first/last`.
Page начинается с 0, default 0; допустимо 0–10000. Size default 20, допустимо 1–100.
Offset ограничен максимумом 1000000 строк; это ограничивает один abuse vector,
но не гарантирует дешёвую query при любом объёме данных. Для больших tenants
нужны performance qualification и затем cursor pagination с отдельным contract.

- Workspaces: sort `name` (default) или `industry`.
- Brands: sort `name` (default), `createdAt`, `updatedAt`.
- Direction `asc`/`desc` (case-insensitive input, canonical lowercase в OpenAPI).
- Evidence: fixed `capturedAt DESC`.
- К каждому sort добавляется unique `id` в том же направлении. Это устраняет
  неопределённый порядок при равных именах/timestamps. Offset pagination не является
  snapshot: concurrent insert/delete может сдвинуть страницы.
- Arbitrary/nested sort fields, negative/too-deep page, invalid size/direction — 400.

Все API timestamps — `Instant`, ISO-8601 с `Z`, variable fractional seconds.
Не полагайтесь на фиксированное количество digits. PostgreSQL audit columns
`created_at/updated_at` используют TIMESTAMPTZ(6), JDBC time zone UTC. PostgreSQL
хранит момент времени, display offset session не меняет этот момент.
V22 явно интерпретирует legacy timestamps как UTC; это согласовано только для
существующих local demo данных. Их первоначальная timezone не восстанавливается.
См. [migration и recovery](database-migrations.md).

## Mutation, async и privacy

PATCH/archive требуют `expectedVersion >= 0`; optimistic locking отклоняет stale
updates с 409. Запрос не меняет сам `lockVersion`. Archive повторяется согласно
existing lifecycle rules; это не физическое удаление истории.

Evidence registration: `202`, `Location: /api/v1/jobs/{jobId}`, no-store.
TEXT/URL передаются JSON, FILE — multipart part `file`, social — provider enum.
Duplicate content определяется workspace-scoped application checksum rules и
возвращает существующий Evidence/Job; общего `Idempotency-Key` header пока нет.
URL fetch/scanning/extraction работает durable Jobs с fencing и quotas #117.
Accepted, FETCHED, CLEAN или artifact не означают READY content-safety approval.

FILE: PDF/PNG/JPEG, максимум 10 MiB/file, 11 MiB/multipart request. Non-multipart
body default 128 KiB, configurable до 1 MiB; admission/filter и parser limits
независимы от domain text limit 20000 и URL limit 2048 characters.
Private ClamAV CLEAN требуется до FILE registration; INFECTED даёт controlled
400 `evidence_malware_rejected`, unavailable verdict даёт safe 503. Поздние durable
scan receipts независимо защищают extraction/provenance. URL scanning выполняется
после durable fetch. Ни один CLEAN не заменяет content-safety approval READY.
Private FILE source reference теперь `null` в DTO; сохранённый object key нужен
worker/storage layer и не возвращается клиенту. URL/TEXT references доступны только
authorized owner. Job payload/error/lease, fetched bytes, receipts, artifacts и
provider credentials исключены из внешнего contract. File download API отсутствует.

`POST /api/v1/cadence/brands/{brandId}/analyze` пока возвращает deterministic sample
BrandSignature и требует активных Brand/Workspace. Real AI identity — #114.

## Threat model и review

Защищаем tenant data, membership, tokens, internal infrastructure и server resources.
Вызвать HTTP может любой клиент; граница доверия (trust boundary) — JWT, параметры,
body, uploaded bytes и diagnostics внешних providers.

| Threat | Protection | Verification |
|---|---|---|
| BOLA / forged UUID or role | Filters, stored role, method security, scoped repository | Existing multi-tenant HTTP/use-case negatives плюс contract assertions |
| Token/SQL/input disclosure через errors | Fixed descriptions/codes, sanitized Bearer challenge, opaque instance | ApiSecurityErrorsTest, ApiProblemContractTest, provider-failure tests |
| Private FILE key disclosure | DTO returns null, entity remains internal | Upload/list contract checks recursively assert FILE reference is null |
| Mass assignment | Explicit request DTO and use-case arguments | Internal status/role/version input cannot change stored state |
| Resource exhaustion / sort traversal | Existing body/upload budgets, bounded page/size, field allowlist | Admission tests, ApiPaginationTest, HTTP deep-offset negatives |
| Lost updates / ambiguous audit history | expectedVersion + @Version, Instant + explicit V22 conversion | Existing stale mutation tests, PostgreSQL populated upgrades/session-zone tests |
| Contract drift | Reviewed source + exact route/DTO/enum inventory + actual response checks | OpenApiContractTest и all common workflow controller tests |

Нет новых dependencies и security matcher bypass. Commercial deployment требует
TLS, approved Clerk/provider infrastructure, edge abuse/rate limits, proper CORS,
backup/restore, observability и performance qualification (#87/#118/#116).
Transport/parser errors до попадания в Spring и reverse-proxy errors должны
квалифицироваться отдельно на выбранном production edge.

Specification: [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html).

## Strategy API slice — 2026-10-05

Generated DRAFT creation, scoped revision read и bounded Brand history list расширяют
reviewed OpenAPI. OWNER only; foreign Strategy concealed404; Brand routes scoped403.
[Strategy contract и границы незавершённого #102](strategy-api.md).

## Generation budgets — task12

Async analysis/Strategy submission и legacy synchronous analysis/evidence/Strategy используют
общий durable admission. Budget/history cap: HTTP429 `generation_budget_exceeded`; missing/disabled
policy: controlled503. Worker budget refusal: terminal FAILED / `BUDGET_EXCEEDED`, provider не вызван.
Same actor/Workspace/kind/key + same digest возвращает original operation, даже при заполненном cap;
changed request409, replay не обходит actual authorization. Generation keys не применяются к
approved-edit/new-DRAFT. Нет public settlement/cancellation/debug endpoint.

Accounting namespace сейчас только SYNTHETIC в isolated local/test. NULL actual означает UNKNOWN;
успешный fake output не является measured live usage/moderation/provider latency.
[Policy/state machine](adr/0030-generation-budget-ledger.md), [qualification](prompt12-budget-report.md).

## Complete Playbook13

Strategy GET/list exposes contentFormatVersion1/2, LEGACY_PARTIAL/COMPLETE, nullable typed
playbook and separate editedBy/editedAt. Generation format/checksum refers to the original
output only. Full v2 PATCH is required for v2 revisions; partial replacement returns409,
unknown fields/malformed fields400, stale expectedVersion409. Approved edits remain new DRAFT
branches, not implicit idempotent operations. Async Strategy jobs capture schema2 and publish
only full v2; existing scope/budget/source/lease guards apply. See [Strategy API](strategy-api.md)
and [ADR0029](adr/0029-full-playbook-contract.md). No new public admin/debug/egress endpoint.
