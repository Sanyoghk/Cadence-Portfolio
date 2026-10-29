# ADR0039 — Creator performance/recommendation reports

Статус: **APPROVED PRODUCT PACKAGE / QUALIFIED LOCAL**. Дата: 2026-10-17.
Baseline: `575b2f61cb64aaa767ee35a9ca792b58041cef51`, clean `mvp`.
Задание22; actual #105/#107 прочитаны. Prerequisites19–21 qualified locally.

Пользователь явно утвердил рекомендованный пакет в текущем чате17 октября2026:
«Прежде чем начинать 23 утверди рекомендованный пакет для 22». Утверждение относится
к format/retention/delivery ниже. Оно не означает реализацию или qualification report workflow.

## Утверждённый пакет

1. **Format:** читаемый static HTML UTF-8 report для download и structured JSON read API.
   HTML без JavaScript, external resources, arbitrary links или client HTML. JSON — отдельный
   response DTO, не JPA entity/provider payload. PDF, email и integrations не входят в scope.
2. **Download/retention:** result доступен24h от capture, exclusive expiry. После expiry
   нельзя скачать/read full result заново. Immutable audit/source snapshot и bytes сохраняются
   по существующей bounded-retention модели ADR0035: максимум100 reports/Brand, каждый до2MiB;
   failed/expired reports занимают quota. Automatic deletion отсутствует до отдельной принятой
   deletion/backup policy. Download expiry не означает physical deletion.
3. **Delivery contract clarification:** durable Job + authenticated relative download endpoint,
   с fresh account/membership/Brand access check на каждом обращении. Публичного signed bearer
   URL нет. Это явное уточнение старой формулировки #105 «Job/signed result URL», согласованное
   с уже реализованной private delivery ADR0035; remote issue не меняется молча.

Alternative formats: JSON-only проще и подходит future frontend, но хуже читается человеком;
CSV удобен для таблиц, но теряет структуру recommendations/uncertainty. HTML+JSON сохраняет
читаемый report и typed API без renderer/dependency/egress. Alternative retention7days расширяет
окно доступа, но увеличивает exposure; automatic deletion требует отдельного решения о том,
какие audit/source data могут удаляться. Public signed links увеличивают риск утечки capability.

## Независимый engineering contract / proposed implementation

Capture scoped Brand + explicit approved Strategy + platform + local date range/timezone,
DAY/PERIOD analytics semantics ADR0037, max28local days. Existing bounded normalized reader,
formulas, coverage/missing/stale/zero/sparse disclosure переиспользуются. Отчёт descriptive;
missing не превращается в zero, insufficient sample не порождает новую recommendation.
Empty data — явный bounded NO_DATA report, не invented metric/recommendation.

Selected recommendation IDs (bounded explicit list) принадлежат same Brand/Strategy/platform/
window. Original immutable recommendation capture используется как historical supporting
evidence, а не заменяется current metrics. Later corrections показываются отдельно: original
recommendation evidence сохраняет свои IDs/versions/checksums и eligibility time. Не запускать
AI/model или apply при создании отчёта. HEURISTIC/UNCALIBRATED остаются явными.

Durable capture→worker→one result. Parent-first locks Account→Workspace→Brand→source;
actual actor и source checks до capture, перед worker publication/download. Rendering pure,
вне DB transactions. Exact lease owner/attempt/deadline fencing, bounded retry/restart.
Idempotency: actor+Workspace+REPORT+key digest и request digest; repeated identical request
возвращает existing operation, different payload409. Results не объявляются current analytics:
они объясняют capture time, source versions и позднейшие изменения.

No raw receipts, prompts, private storage references, secrets, email, account names или
лишние персональные данные в artifact. Source observation IDs/checksums/counters/windows,
Strategy revision/checksum и recommendation rule/model metadata нужны для explainability.
Parent/access revocation запрещает новые reads/downloads; bytes уже скачанного файла отозвать
невозможно. Stable report не переписывается из-за subsequent corrections.

## Threat model / qualification contract

Защищаем tenant business metrics, recommendation evidence, private report и immutable history.
Caller — authenticated current OWNER. Trust boundaries: HTTP, persisted user data, queue,
worker output, artifact download. Defense in depth: strict DTO + usecase authorization +
scoped queries + domain caps + independent PostgreSQL guards/runtime permissions.

| Threat | Protection | Required verification |
| --- | --- | --- |
| BOLA/foreign report/Job/recommendation | actual scope/actor/parents at every stage | anonymous/role/foreign/status/download/revocation tests |
| XSS/HTML injection | fixed markup + text escaping + attachment/no-store/CSP/nosniff | markup/control/Unicode malicious fixtures |
| Invented/misleading evidence | original exact supporting capture; independent current section | correction/context/source-change/history tests |
| Duplicate/stale publication | unique key/result + atomic Job completion + lease/attempt fences | replay/two workers/expired lease/crash/restart |
| Resource exhaustion | range/row/group/ID/output/retained caps, bounded worker/query/timeouts | oversize/range/1001rows/output/quotas |
| Audit mutation/SQL bypass | immutable snapshot/checksums, SQL parent/source/link guards | populated migration + direct SQL/runtime negatives |
| Data exposure | dedicated export DTO, no raw/private/personal payloads, safe errors | payload/header/log inspection, DTO tests |

Full Maven/Docker/PostgreSQL/security, Python report gates, applicable Linux filesystem/
renderer suites, startup и dependency qualification обязательны после implementation.
Product package утверждён. Implementation, runtime/SQL/security tests и full local qualification22
завершены; фактические commands/results находятся в [report22](../prompt22-reports-report.md).
Production deployment и operational multi-host failure qualification остаются отдельно.


## Execution22 — engineering refinement

Implementation: [API/recovery](../creator-reports-api.md), [qualification](../prompt22-reports-report.md).
V55/private PostgreSQL snapshot+artifact, same DurableJob foundation, separate CREATOR_REPORT kind.
2MiB is the combined snapshot+HTML payload reservation; snapshot≤1MiB. Max100 retained including
pending/failed/expired. No automatic deletion. Exact DAY/PERIOD/pillar groups support one explicit
approved/locked full Strategy; latest source is never substituted. Captured source version changes
deny publication/result/download; later metric corrections preserve original data and bytes.
No-data is explicit, no new advice generated. Original recommendation evidence is historical;
report is not an Identity/evidence consumer, consent, apply or current eligibility assertion.
Report export excludes raw evidence/Identity text; original withdrawal never restores advice
eligibility through report access. Model/transmission/privacy/provider scope remains unchanged.
Full local qualification status is recorded only in the linked report after actual gates.
