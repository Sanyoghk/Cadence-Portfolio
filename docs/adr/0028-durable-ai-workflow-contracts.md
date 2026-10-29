# ADR0028 — Durable generation, source capture, idempotency и budgets

Дата: 2026-10-15. Baseline `f2f60c9`, mvp. Задание09.
Статус: ACCEPTED technical design внутри утверждённого provider-independent scope.
Это контракт для10–12, не runtime implementation и не выбор AI/provider/privacy policy.
Product limits, перечисленные как pending ниже, не считаются утверждёнными.
Связи: [ADR0026](0026-approved-identity-strategy-source.md),
[ADR0027](0027-backend-first-ai-adapters-last.md), [scopes/drafts](../ai-workflow-issue-drafts.md).

## Фактическая архитектура

`DurableJob` хранит status/attempts/leaseOwner/leaseExpiresAt; `DurableJobQueue` умеет
claim/recovery, но общий claim не фильтрует type. `GetJobUseCase` и repository ownership query
обслуживают только BRAND_EVIDENCE_INGEST. Global unique idempotency_key у queue не является
HTTP tenant/actor/request-digest idempotency contract. Generic enqueue find-then-save сам по
себе не доказывает concurrent replay. Existing ingest workers/locks нельзя менять вслепую.

`AnalyzeEvidenceUseCase` и `BuildStrategyUseCase` synchronous, process Semaphore(2).
Analysis публикует signature + SUCCEEDED receipt/citations; Identity создаётся отдельным
from-analysis action и отдельно утверждается. Strategy публикует DRAFT из exact latest
APPROVED Identity. Attempt deadline60s; private physical verification window30s.
Queue не заменяет эти application/source guards и не даёт distributed budget.

## Компоненты и разделение ответственности

- Generation operation: immutable operationId, actorId, workspaceId, brandId, kind,
  capturedAt/deadline, requestDigest/version, policyVersion, exact source snapshot и result link.
- Job: scheduling/lease/attempt, internal payloadReference только server-issued operation ID.
- Attempt: unique(operationId, attemptNumber), dispatch witness, provider call correlation,
  started/finished timestamps, outcome, validated provenance и usage evidence.
- Budget ledger: reservation/settlement/reconciliation отдельно от content approval.
- Controller принимает bounded DTO; application авторизует/captures; domain validates;
  repository/SQL защищают scope, one-result и immutable provenance; adapter только I/O.

Переиспользовать таблицу/модель jobs через typed AI claim и новую FK-bound operation связь;
не вызывать общий claimNext для AI worker. Изолировать type dispatch всех активных pollers,
чтобы ingest worker не claim AI и наоборот. Не вводить вторую broker infrastructure.
Если изменение shared queue требует locks, сначала доказать отсутствие inverse order с ingest.

## API contract (исходный design09; routes реализованы slice10)

| Endpoint | Contract |
| --- | --- |
| POST /api/v1/brands/{brandId}/analysis-jobs | body: expectedVersion, mode DECLARED или EVIDENCE; 202 + relative Job Location |
| POST /api/v1/brands/{brandId}/strategy-jobs | body: expectedVersion (Brand.lockVersion); server-selected latest APPROVED Identity; 202 |
| GET /api/v1/jobs/{jobId} | additive typed generation view: operation kind/status, safe failure code, times, result reference |
| GET /api/v1/analysis-results/{analysisId} | scoped validated successful result/citations, no private refs; existing from-analysis action остаётся |

POST требует Idempotency-Key (ASCII allowlist [A-Za-z0-9._:-], 1–128 chars), unknown fields
rejected. Actor/scope/source IDs/provider/cost/status не writable. DECLARED не получает
выдуманные citations; EVIDENCE выбирает существующие current admissions server-side.
Response: jobId, operationId, status, submittedAt, deadline, relative statusUrl. No-store.
Текущий authenticated active OWNER + DB membership обязателен на каждом HTTP boundary;
foreign/missing Job/result одинаково404; anonymous401, role403; stale source/key conflict409;
invalid DTO400, configured quota429, disabled/unavailable capability503. No raw provider errors.

Existing synchronous endpoints сохраняют response/status contracts. В12 они должны проходить
тот же budget/admission gate, иначе новый async API обходится через legacy route. Если сохранить
общие guards невозможно, deprecation/removal требует отдельного явного API решения, не silent202.
GET Job не выдаёт result после access revocation. История результата не делает withdrawn evidence
доступным для нового consumption: применять existing history/source disclosure policy.
Generated analysis по-прежнему требует explicit copy → Identity DRAFT → approval;
async conversion не означает auto-copy или auto-approve. Strategy result только DRAFT.
Public cancellation API не добавляется в10–12: withdrawal/archive не обещают отменить уже
ушедший provider request, но блокируют publication и дальнейшие calls.

## Capture → dispatch → publication

1. Request authentication; transaction locks actual Account → Workspace → Brand → Identity
   (если Strategy) → ordered evidence. Проверить expectedVersion, active parents, current
   OWNER/membership; выбрать exact eligible sources и bounded immutable snapshot.
2. В той же transaction связать operation, scoped idempotency mapping и Job. Snapshot содержит
   input schema/digest version, exact source ID/version/checksum/admission/projection,
   original fetch/extraction/scan/provider origin и review actor/time, parent versions.
   Для Strategy — ADR0026 approval/content/original-analysis links. Private bytes не копировать
   в queue payload, raw credentials/JWT не сохранять. Не создавать RUNNING analysis receipt
   при submission: queue wait не является model latency.
3. Worker nonlocking bounded discovery → короткая claim transaction. Получить binding из DB;
   повторить actual actor/scope/lifecycle checks; зафиксировать lease/attempt и reservation.
   Application worker entry point использует internal typed context из persisted operation,
   не подставленный HTTP actor и не искусственный OWNER principal с доверенным old JWT.
   UI-facing beans остаются secured; shared domain policy применяется и HTTP, и worker.
4. Private PDF/URL verification вне locks → fresh metadata/lease/permission check → durable
   dispatch intent BEFORE external call → bounded model I/O вне transaction. Model не получает
   arbitrary fetch/tools/private references. Source changes не вызывают reselection.
5. Validated output → physical verification вне locks → final transaction с fresh source,
   permission, deadline и lease witness. Atomic result/receipt/citations/Strategy DRAFT,
   operation success, Job success и settlement when known. Любой failure откатывает весь publish.
   Unique operation→result защищает replay даже после process crash. Approval отдельно.

Новая operation хранит exact captured scope, но не весь historical membership list как permission.
Worker всегда проверяет актуальную DB membership конкретного workspace и того же actor.
Новый OWNER не наследует pending operation старого actor. Смена source/revision требует нового
request/key и нового capture; нельзя продолжить старую operation с новым content.

## Lock order и database invariants

Для новых AI paths: Account → Workspace → Brand → Identity → evidence (UUID order)
→ operation → Job → budget rows (canonical key order). Idempotency mapping создаётся
в submission после parent locks; unique race читается в новой transaction после rollback.
Claim/recovery discovery не держит Job lock до parent locks. Recovery/settlement не берут
после budget/Job lock более ранний parent lock. SQL triggers должны соблюдать тот же порядок.
Существующие ingest lock orders не переписываются этим ADR; typed filters исключают shared AI rows.

Новые migrations, без изменения V1–V42: typed FK scope consistency, unique operation/job/result,
unique actor/workspace/operation-kind/key-digest, bounded attempts/deadlines/checksums,
immutable actor/source/digest/policy/dispatch records, allowed status transitions и budget
nonnegative/exact arithmetic. Runtime grants не позволяют переписать history или mint approval.
Проверки в SQL не заменяют physical bytes verification. Новые rows связываются с существующими
receipts; legacy receipts не получают invented operationId/lease/cost/actor.

## State machine и recovery

Atomic publication нельзя получить простым вызовом существующего REQUIRES_NEW complete(),
после которого отдельно succeed(Job): crash между ними создаст inconsistent status. В10 нужен
общий transaction coordinator или transaction-participating shared domain publication service,
без ослабления guards sync path. Atomic write/result-link проверяется failure injection.
Discovery/recovery batch ≤100, stable ordering и bounded DB lock timeout; bounded polling
backoff. Запись terminal state/recovery authorised worker возможна после actor revocation,
но не content publication: cleanup authority узкая и не основана на права́х бывшего caller.

Operation QUEUED → RUNNING → SUCCEEDED либо terminal SUPERSEDED / FAILED / EXPIRED /
OUTCOME_UNKNOWN. RUNNING → RETRY_WAIT → RUNNING допустим только при доказанном safe retry.
SUCCEEDED означает один committed result, не APPROVED. Job scheduling status остаётся
отдельным: PENDING/FAILED(retry wait)/RUNNING/SUCCEEDED/DEAD. Terminal operation maps DEAD
кроме SUCCEEDED; API показывает semantic operation status, не выдаёт retryable FAILED за terminal.

| Событие | Поведение |
| --- | --- |
| Crash до dispatch intent | Recover bounded retry, тот же source/digest; новая attempt |
| Crash после dispatch intent, даже до фактической отправки | Консервативно OUTCOME_UNKNOWN; blind retry запрещён |
| Timeout/disconnect/lease expiry после dispatch | Unknown result/spend; hold reservation; no auto retry |
| Provider доказал no execution/no charge | Safe retry в remaining deadline и retry budget |
| Invalid output, access/source withdrawn или changed | No publication; FAILED/SUPERSEDED, но фактическая стоимость учитывается |
| Crash во время DB publication | Transaction rollback либо complete atomic result; restart читает committed operation |
| Duplicate delivery / старый worker с тем же workerId | Attempt number + lease expiry + operation state fence отклоняет запись |
| Поздний ответ после deadline/lease loss | Не публиковать; доверенные usage evidence допускаются отдельным idempotent accounting path |

Не обещаем exactly-once external execution. Достижимо at-most-one local publication;
provider idempotency/reconciliation capability проверяется позднее #120. Unknown terminal
не оживляется в SUCCEEDED: reconciliation дополняет accounting, а новая generation — новый request.
Сохранять legacy TEXT SUPERSEDED semantics; общий FAILED не должен скрыть source revocation.
Существующие synchronous receipt statuses остаются прежними, semantic operation status новый.

## Idempotency и bounded history

Scope = actual actorId + workspaceId + operationKind + client key digest. Digest запроса —
versioned canonical DTO с brandId, expectedVersion, mode и всеми model-affecting options;
его формат отделён от существующих source digest v1/v2/v3. Same key + same request возвращает
ту же operation (202 pending,200 terminal), не выбирает новые sources, не делает новый reservation.
Same key + different request409. Перед replay — актуальная authorization; response не cache-bypass.
Разные actors/tenants не делят ключ. Canonicalization однозначна, unknown fields rejected.

Минимальный scoped key mapping обязателен уже10, финансовый ledger и uniform sync guards —12.
Approved edit/branch НЕ становится идемпотентным автоматически: existing expectedVersion,
new-DRAFT semantics сохраняются; расширение idempotency на edit — отдельный contract.

Без утверждённой retention policy не вводить автоматический TTL удаления key/history. Пока
mapping retained, key не переиспользуется. Bounded persisted count/queued operations на scope,
pagination, server-configured caps и fail-closed при cap защищают storage. После будущего purge
нужен отдельный tombstone/key-reuse contract; истечение JWT не удаляет operation или hold.

## Budget reservation и uncertain accounting

Account = actual requesting CadenceUser; tenant = Workspace. Перед dispatch атомарно резервировать
во ВСЕХ применимых account/workspace/global лимитах, чтобы создание новых workspaces не обходило cap.
Лимиты: queued count, running/dispatch slots, per-operation upper usage bound и aggregate period spend.
Policy version, period boundaries UTC, currency/unit и quote/tariff version immutable на reservation.
Cost = integer minor/micro units с declared scale, checked arithmetic; не binary float и не mixed currency.

RESERVED → SETTLED(actual measured/verified cost) либо RELEASED(proven no execution) либо
HELD_UNKNOWN. Dispatch intent фиксируется с reservation до I/O. Для known actual сохраняется
measured usage и separately quoted/estimated cost; provider metadata не равно verified invoice.
Если upper quote отсутствует, live dispatch запрещён. Retry резервирует дополнительный bound,
если нет доказательства provider same-call/no-extra-charge. Period rollover не освобождает hold:
unresolved holds также учитываются в current admission safety balance; original period не rewrite.

Settlement уникален по trusted call/attempt evidence, duplicate callbacks не списывают повторно.
Actual > reserved фиксируется честно как overrun и блокирует новые calls в затронутом scope;
нельзя clamp actual до cap. Late failed/superseded output тоже мог стоить денег. Финансовая
reconciliation не имеет права publish content либо менять старый content provenance.
Истечение lease освобождает local worker slot, но не неизвестный remote spend/in-flight hold.
После bounded operation deadline нет бесконечных retries; unresolved holds требуют отдельной
проверяемой reconciliation, не silent timeout release.

Latency: отдельно queueWait, measured attempt duration и provider-reported latency (nullable).
Usage/cost state UNKNOWN, ESTIMATED, MEASURED с origin; moderation NOT_RUN/UNKNOWN/REPORTED
с provider/policy attribution. UNKNOWN ≠0, NOT_RUN ≠passed. Fake usage = SYNTHETIC,
изолированная accounting namespace; не реальные платежи. Legacy nullable, no backfill.

## Limits и статус решений

| Параметр | Решение |
| --- | --- |
| Source input | Сохранить existing max5 / total20000 UTF-8 bytes и kind/citation bounds |
| Deadline | Для первой async версии общий60s от submission, включая queue/retries; не продлевать при restart |
| Physical window | Не более30s и всегда ограничена remaining operation deadline |
| Attempt lease | Не позднее operation deadline; lease не разрешает publication после expiry |
| Retry ceiling | Technical ceiling3 attempts; только safe pre-dispatch/proven no-execution; каждый в remaining60s |
| Process capacity | Сохранить2 как local protection; это не distributed quota |
| Product quotas/pricing | Не утверждены: daily/monthly spend, user plans, валюты, retention periods |
| Production activation | Disabled до explicit bounded policy и adapter qualification; missing policy запрещает dispatch |
| Offline validation | Явные synthetic limits fixtures проверяют boundaries, не объявляются тарифом продукта |

60s сохраняет existing conservative validity window, но queue saturation может давать EXPIRED
без model call. Увеличение времени — отдельное reviewed policy change с tests, не побочный
эффект внедрения очереди. Receipt attempt deadline и operation deadline проверяются совместно;
новый attempt не получает разрешение перешагнуть original deadline. Ни один pending product
limit не блокирует implementation механизма10–12 с fail-closed configuration.

## Threat model / review и обязательное доказательство

Защищаем tenant content, consent, immutable history и budgets. Callers — недоверенный OWNER,
provider и stale worker; trust boundaries HTTP→application, DB→worker, storage/provider→validator.

| Threat | Protection | Verification для10–12 (ещё не исполнена) |
| --- | --- | --- |
| BOLA/forged actor/old membership | server actor capture + fresh DB checks на API/worker/publish | anonymous/role/foreign/removed-owner, raw SQL negatives |
| Prompt injection / exfiltration | data-only sources, no tools/fetch, bounded validated output | malicious webpage/source IDs/commands не создают egress |
| Stale lease/source/withdrawal | exact snapshot + physical checks + atomic fences | two workers, both race orders, lost lease, same workerId new attempt |
| Double spend / replay | scoped digest/unique operation + serial reservation/settlement | concurrent same/different key/payload, period rollover, crash after dispatch |
| Resource exhaustion | bounded body/queue/retries/history/output, policy caps | overflow, cap N/N+1, hostile malformed output, queue starvation |
| Privacy/log leak | no tokens/raw prompts/private refs in Job DTO/logs | error/log/API contract assertions, no provider diagnostics |
| SQL bypass | immutable FK/check/unique/transitions/runtime grants | real PostgreSQL restricted role + populated migration |

Bearer-only auth сохраняется; cookie/BFF не добавляется, текущая причина CSRF disable не меняется.
Без новой dependency; Spring transactions/JPA/PostgreSQL/существующие ports достаточны.
Если один application guard обойдён, SQL должен сохранять scope/one-result/history; SQL не
доказывает безопасность decoder/провайдера. I/O и commit не атомарны: уже переданные bytes
нельзя отозвать; финальные fences предотвращают stale publication, не отменяют прошлый egress.

## Alternatives и qualification

In-memory queue теряет operations при restart. Blind reuse generic Job API допускает неверный
ownership/type claim. Новая broker system увеличивает complexity без текущей необходимости.
Выбран relational operation + existing typed queue, explicit state/financial ledger: больше SQL
invariants, зато воспроизводимые recovery/replay/races. Не превращаем весь controller в long TX.

Для implementation: unit/API/direct-bean negatives, PostgreSQL/runtime/migration/concurrency,
failure injection, full Docker/Python/Linux/renderer и startup. Existing TEXT/PDF/URL/social,
Identity copy/approval и Strategy history regressions обязательны. Для этого ADR — docs-only
checks; приведённые scenarios являются acceptance, не результатами тестов.

## Implementation10 — уточнение technical bounds (2026-10-15)

[Runtime contract](../durable-ai-generation.md), [qualification report](../prompt10-durable-generation-report.md).
Выбран lease20s (не позже original60s deadline), fixed model executor2 без backlog,
wait до remaining lease; snapshot/result64KiB. History cap default1000/workspace,
config1..10000 — технический ceiling local/test, не commercial quota/retention approval.
Private persisted schemaVersion1 сохраняет structured fields отдельно от model JSON.
Original provenance остаётся в immutable admission/approval FK links; legacy rows не backfill.

V43 seals Job/operation/attempt/source relations; generic queue не claim/recover AI jobs.
State machine и minimal scoped replay реализуются в10; extended operational recovery11,
budget accounting12 и real adapter/privacy qualification остаются обязательными.
Непрозрачная port exception консервативно OUTCOME_UNKNOWN: текущий port не доказывает,
произошёл ли remote execution. Typed no-execution/reconciliation evidence добавляется с adapters,
без придуманного zero cost и без automatic charged retry.

## Implementation11 — recovery/operations (2026-10-16)

V44 завершает attempt history: nullable historical finish/outcome/reason, immutable dispatch
witness, schema0 для existing attempts и schema1 для новых. Deferred SQL seals требуют
completion evidence вместе с новым terminal Job; runtime grants разрешают только transition
fields. Migration не реконструирует прошлый provider outcome. Deployment требует остановки
V43 writers перед migration/grants и запуском новой версии, не rolling mix старого и нового кода.

Новый Job `errorCode` — bounded diagnostic category, не provider text. Для invalid returned
analysis сохранён existing10 `OUTCOME_UNKNOWN`, теперь с `INVALID_OUTPUT`: local validation
точно отклонила результат, но port не даёт trusted execution/billing evidence. Это техническое
уточнение таблицы failure classifications выше ради status compatibility, не разрешение retry
и не замена будущего accounting state. SUPERSEDED также не означает отсутствие затрат.

Discovery исключает valid leases/future retries до LIMIT100. Capacity2 без backlog дополнена
проверкой занятых provider threads: ignored interruption не разрешает новые dispatch.
Default scheduler остаётся последовательным; инфраструктурные ошибки дают bounded
2/4/8/16/30s polling backoff и safe WORKER_UNAVAILABLE event без exception text.

Completion counters имеют только enum outcome labels; correlation IDs остаются в logs,
не metric labels. Logs/counters публикуются afterCommit, process-local и best-effort;
PostgreSQL — durable source of truth. Existing Spring JMX предоставляет opt-in internal
MBean без нового HTTP/admin endpoint и без новой dependency/remote listener.
[Runbook](../ai-jobs-recovery-runbook.md) описывает four child-JVM crash drills и диагностику.
Квалификация и remaining boundaries: [report11](../prompt11-recovery-report.md).

## Implementation12

Engineering details shared sync/async ledger, synthetic policy и unknown holds закреплены
[ADR0030](0030-generation-budget-ledger.md). Actual qualification/status — [report12](../prompt12-budget-report.md).
Это не выбор provider, commercial quotas, live namespace или разрешение production activation.
