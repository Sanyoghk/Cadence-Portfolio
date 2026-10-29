# Задание22 — reviewed contracts и prerequisite decisions

Baseline `575b2f61cb64aaa767ee35a9ca792b58041cef51`, `mvp`, working tree initially clean.
Actual #105/#107 прочитаны17 октября2026. Code prerequisites19–21 проверены; они не
реализуются повторно. Статус: **22 QUALIFIED LOCAL**; actual gates: [report22](prompt22-reports-report.md),
[API/recovery](creator-reports-api.md).

Конкретный product choice и alternatives: [ADR0039](adr/0039-creator-performance-reports.md).
Пользователь17 октября2026 явно утвердил рекомендованный HTML+JSON /24h / bounded retained
history+bytes / durable Job+private authenticated delivery пакет до начала23. ADR0039 фиксирует
это решение. Product code/V55/endpoints/expiry реализованы в задании22; full local qualification завершена: report22.

## Goal / Current architecture / Proposed architecture

Цель — дать creator читаемый и выгружаемый снимок performance с объяснимыми рекомендациями.
PerformanceService/PerformanceReader/PerformanceCalculator уже дают bounded normalized
observations, checked formulas и coverage по ADR0037. RecommendationInsight хранит exact
immutable supporting observations/context/source checksums, HEURISTIC/UNCALIBRATED и
resolution history по ADR0038. CsvExportService/DurableJob дают проверенный образец leased
worker, recovery, atomic result publication и private download по ADR0035.

Реализована отдельная report operation на той же DurableJob foundation, без смешения
REPORT с ingest, CSV_EXPORT или AI_GENERATION. Сохраняется immutable captured normalized
input; pure renderer вызывается вне DB transactions. Current parent/access checks и exact
worker/attempt/deadline fence выполняются до publication и download. Artifact/result и
Job SUCCEEDED записываются атомарно. No AI call, paid usage или invented provider metadata.

Рекомендация в отчёте — **историческая гипотеза**, не команда apply. Она содержит свой
original evidence, rule/model attribution и eligibility time. Current captured performance
показывается отдельно; новые corrected observations не подменяют старые supporting IDs.
No-data/sparse/stale coverage не скрывается и не порождает fabricated recommendation.

## API утверждённого product package

| Операция | Семантика |
| --- | --- |
| POST `/api/v1/brands/{brandId}/reports` | Strict source/date/platform/selection DTO, Idempotency-Key,202 + Job Location |
| GET `/api/v1/jobs/{jobId}` | Existing scoped Job dispatch расширяется report status DTO |
| GET `/api/v1/reports/{id}` | Bounded status/provenance; relative result/download endpoints без storage keys |
| GET `/api/v1/reports/{id}/result` | Dedicated structured captured result, fresh authorization/lifetime guards |
| GET `/api/v1/reports/{id}/download` | Agreed format; server-generated filename, no-store/private/CSP/nosniff; no Range |

Planned DTO не содержит writable actor/tenant/audit/provider/approval fields. Request digest
включает Brand, explicit source ID/version, dates/timezone/platform и selected recommendation
IDs. Idempotency scope — actual actor/Workspace/REPORT/key digest. Replay требует актуального
доступа и возвращает тот же result identity; другой payload409. Нельзя выдавать signed/public
capability из устаревшей формулировки #105 без явного решения о delivery.

Date range максимум28local days; exact UTC/local/tzdb bounds сохраняются. DAY/PERIOD
источники не складываются в выдуманный unique period reach. Max1000 candidate observations
из существующего reader; group/output caps проверяются до publication, partial truncation
запрещено. Supporting recommendation IDs должны принадлежать captured Brand/Strategy/
platform/window; original observation/revision/context/source checksums остаются stable.

Состав export DTO минимален: requested window, capture/formula versions, normalized
counters/rates/coverage, source observation/context/Strategy/checksum references, explanation,
confidence/uncertainty/rule/model metadata. Email, account names, raw import bodies, raw prompts,
Identity content, storage keys и credentials в report не нужны.

## Security considerations / Components affected

Threat model в ADR0039. Authentication — existing Clerk Bearer; authorization — actual OWNER,
membership и Account→Workspace→Brand→source locks. Доверие JWT scope без current membership
не допускается. Parameterized scoped query, strict DTO, safe errors и bounded worker защищают
от BOLA/mass assignment/injection/resource exhaustion. HTML, если выбран, только static markup
с escaping; model/user prose никогда не становится executable markup или backend command.

Новые слои: report request/domain capture + repository + application service/renderer/usecase
+ controller/DTO + scheduling configuration + additive migration. Existing JobController
расширяется scoped report status; SQL guard связывает REPORT Job и result в обе стороны.
Runtime role не получает возможность rewrite source capture или опубликованный artifact.
SecurityFilterChain, dependencies, external providers/mail/renderers менять не требуется.

## Testing strategy / Security testing strategy

| Требование | Проверка implementation (actual results в report22) |
| --- | --- |
| Metrics→report→read/download | HTTP/H2 и actual PostgreSQL; format headers/content/checksum |
| Original recommendation evidence | Known independent fixture, stored IDs/versions/checksums и explicit uncertainty |
| Missing/zero/sparse/stale | No invented values/advice; coverage/state/age/window disclosure |
| Later correction/context change | Stable original capture, independently marked current section; no history rewrite |
| Foreign resource/unknown fields | Anonymous401, wrong role403, foreign Brand/Job/report/recommendation404, mass assignment400 |
| Replay/payload collision | Same request one operation/result; changed payload/scope conflict |
| Two workers / lost lease | Atomic one claim/publication; stale attempt cannot publish even with valid bytes |
| Crash/restart / timeout | Expired lease recovery, bounded attempts/deadline, no partial result or false AI usage |
| Revocation/archive/source change | Fresh checks before publication/download; no publication after required guard fails |
| Oversize/exhaustion | Request/range/ID/row/group/output/retained caps and safe errors |
| Download safety | Current access, expiry exact boundary, controlled filename, no keys/bearer URLs/Range/caching |
| SQL/runtime | Direct immutable history/tampering/FK/source/Job fencing negatives, least-privilege positive flow |
| Migration/startup | Populated latest→new migration, old checksums/data intact, Hibernate validate, packaged startup |

Full `.\mvnw.cmd clean verify`, Spotless/JaCoCo, required PostgreSQL/evidence report gates,
Python report tests, applicable Linux filesystem/renderer runs и fresh dependency scan входят
в qualification22; actual результаты в report22. Прежние2104 tests задания21 не объявляются новым run22.

## Done и remaining scope

Исходный design сохранён; текущий implementation и qualification — в report22. Product choice approved:
HTML+JSON,24h full result access, bounded retained history/bytes без automatic deletion и
authenticated delivery вместо старого signed-link wording. Implementation следует
ADR0039; full local qualification/API/docs/report завершены, slice сохраняется local commit. #105/#107 не менялись
на GitHub; push/CI/closure/deployment не выполнялись. PDF/mail/live integrations не delivered.

## Изменённые файлы / фактическая проверка design slice

Ниже — исторический docs-only этап `db5a0e7`, до approval `5f3e506` и текущей implementation.
Полный актуальный manifest implementation — в [report22](prompt22-reports-report.md).

* `docs/adr/0039-creator-performance-reports.md` — конкретный proposed product choice,
  alternatives, независимый engineering contract и threat model.
* `docs/prompt22-report-contract.md` — current/proposed architecture, API и test matrix,
  remaining boundary и фактическая проверка.
* `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md` — синхронный pending status22 и Task6.3.
* `docs/mvp-prompt-progress.md` — execution ledger без ложного completion.
* `docs/issue-sync.md` — actual #105 contract discrepancy/remaining report scope.
* `docs/astra-medium-mvp-prompts.md` — точка продолжения22 с pending decision.

Docs-only checks: UTF-8/fences/relative links, exact changed-file scope, absence of changes to
source/build/migrations/configuration, `git diff --check`. Maven для design-only slice не
запускается. Local design commit не означает implementation или fulfillment #105.

Подтверждённые факты: actual HEAD/issues/code inspection и docs-only checks.
Инженерный вывод: existing normalized reader и DurableJob foundation пригодны для reuse,
но existing ingest/export queue сама по себе не означает delivered report workflow.
Непроверенное для initial design commit: runtime/SQL/concurrency тогда ещё не реализованы.
Текущие tests/results фиксирует report22; production qualification не заявляется.

Фактически выполнено:

```powershell
python -B var/prompt22-design-check.py
git diff --check
```

Результат:7 docs-only files, UTF-8/fences/365 relative links PASS; source/build/config/
migration changes отсутствуют. Design сохраняется отдельным local commit
`docs(reports): prepare contract pending format and retention decision`.
