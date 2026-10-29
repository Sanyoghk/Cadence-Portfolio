# Durable Brand/Identity и Strategy generation — задания10–11

Контракт: [ADR0028](adr/0028-durable-ai-workflow-contracts.md). Provider-independent scope draft A реализован slices10–11;
budgets12, Playbook13 и provider-dependent #121 остаются отдельными scopes.
Qualification11: [отчёт](prompt11-recovery-report.md), operations: [runbook](ai-jobs-recovery-runbook.md).

## API и поведение

- `POST /api/v1/brands/{brandId}/analysis-jobs`: `{ "expectedVersion": 0, "mode": "DECLARED" }`
  либо `EVIDENCE`. Обязателен `Idempotency-Key`, ASCII `[A-Za-z0-9._:-]{1,128}`.
- `POST /api/v1/brands/{brandId}/strategy-jobs`: `{ "expectedVersion": 0 }`, тот же header.
- Submission возвращает202, `Location: /api/v1/jobs/{jobId}`, `Cache-Control: no-store`.
  Same actor/workspace/kind/key и same request возвращают ту же operation; terminal replay200.
  Changed request409. Ключ хранится только как SHA-256 digest; это correlation, не authentication.
- `GET /api/v1/jobs/{jobId}`: generation view с operationId/kind/status, attempts, timestamps,
  resultId/resultUrl и nullable safe `errorCode`. Existing ingest JSON сохранён; OpenAPI описывает обе формы через oneOf.
- `GET /api/v1/analysis-results/{id}`: validated immutable output/citations. Strategy result
  доступен через существующий `/api/v1/strategies/{id}`. Foreign/missing Job/result404.
- Anonymous401, non-OWNER403, invalid/unknown request fields400, stale version409,
  retained-cap429, disabled capability503. Backend повторяет actual membership проверки.

Analysis сначала публикует signature + successful receipt/citations. Explicit from-analysis
или from-evidence-analysis создаёт Identity DRAFT; отдельная human approval остаётся обязательной.
Strategy создаёт DRAFT из exact latest APPROVED Identity, выбранной при submission.
Новая revision/edit/approval не заменяет captured source. Existing synchronous routes сохранены
с прежними status/response contracts; их общий budget enforcement остаётся заданием12.

## Как устроено хранение

`AiGenerationOperation` хранит immutable actor/workspace/brand, kind, request/key digests,
private snapshot/checksum, submittedAt/deadline, source admission FK links и terminal result.
`AiGenerationSnapshot` schemaVersion1 отделён от model-facing JSON: сохраняет structured
URL/social fields для round trip, не меняя existing text-evidence-v1/mixed-evidence-v2/
social-evidence-v3 digests. Exact admission links сохраняют original immutable extraction,
scan, provider-origin, review actor/time provenance; новые retrieval/approval не выдумываются.
Strategy snapshot содержит `ApprovedIdentitySource` с exact revision/checksum/approval/actor.

`DurableJob` остаётся scheduling record, type `AI_GENERATION`; payload содержит только
`generation:<operationId>`. Generic ingest claims/recovery исключают AI_GENERATION.
`AiGenerationAttempt` хранит unique operation/attempt, worker, immutable lease expiry,
start и dispatch witness + analysis receipt; V44 добавляет finish/outcome/reason. JWT, credentials, private URLs не сохраняются
в Job payload и не возвращаются в job DTO. Snapshot text остаётся sensitive private DB data.

V43 добавляет новые таблицы и guards, не переписывает V1–42, legacy receipts или approvals.
SQL проверяет bindings, checksum, source membership, terminal/history immutability,
job/operation consistency и lease witness; runtime grants разрешают только transition columns.

## Runtime Flow

```text
POST + authenticated principal
  → secured AiGenerationUseCase
  → AiGenerationService submission transaction
  → Account → Workspace → Brand → source locks; current authorization + bounded capture
  → immutable operation + source links + typed Job COMMIT
  → 202 / Location

worker discovery (max100, stable order)
  → short claim transaction → new attempt / lease
  → current metadata → private physical read outside transaction → fresh checks
  → dispatch witness + analysis receipt COMMIT
  → bounded model call outside transaction
  → physical read outside transaction → current source/access/deadline/attempt fences
  → one transaction: result + receipt/citations/Strategy + operation success + Job success
  → explicit user copy/review/approval
```

Worker не создаёт искусственный SecurityContext: actor берётся из immutable operation,
а право этого actor проверяется по актуальной БД. Terminal cleanup после access revocation
может только завершить operation/attempt, не publish content. Parent locks предшествуют
operation/Job locks. После ожидания parent lock operation/Job refresh исключает stale JPA cache.

## Recovery и bounds

| Ситуация | Результат |
| --- | --- |
| Crash/lease loss до dispatch | Новая attempt, тот же snapshot; max3 |
| Private read временно unavailable до dispatch | RETRY_WAIT, backoff1s/2s до4s, max3 |
| Crash/expired lease после dispatch witness | OUTCOME_UNKNOWN, automatic retry запрещён |
| Model exception/timeout/неоднозначная ошибка | OUTCOME_UNKNOWN, без сырого provider error |
| Source/access changed или output пришёл после допуска публикации | Нет публикации; SUPERSEDED |
| Queued/recovered operation истекла | EXPIRED; deadline не продлевается |
| Capability disabled / attempts exhausted | FAILED |
| Transaction публикации упала | Полный rollback, ни orphan result, ни ложного success |
| Duplicate delivery после успеха / старый attempt | Existing result не меняется |

Общий deadline60s от submission, включая очередь/retries. Lease максимум20s и не позже deadline.
Model wait ограничен remaining lease (max20s). Executor2 threads, без backlog; зависший adapter,
игнорирующий interrupt, не вызывает создание бесконечных replacement threads. Process capacity2
не является distributed product quota. Physical verification сохраняет existing30s window и
fresh operation fence: истечение operation/lease запрещает dispatch/publication.

History cap по умолчанию1000 retained operations/workspace (config1..10000) — техническая
защита storage, не согласованный коммерческий тариф и не TTL. Terminal operations не удаляются
по таймеру; private snapshot retention/deletion policy требует отдельного решения до production.
Cost/usage/moderation не backfill: этот slice не имеет budget ledger и не заявляет нулевую цену.
Attempt records — достаточный durable dispatch witness для будущего accounting/recovery11–12.

## Включение в isolated local/test

`CADENCE_AI_JOBS_WORKER_ENABLED=true` включает polling раз в1s. По умолчанию worker выключен.
`CADENCE_AI_JOBS_MAX_RETAINED_PER_WORKSPACE` задаёт technical history cap.
Submission/dispatch допускаются только с active local/test без prod и соответствующим
`cadence.brand-analysis.adapter=fake` / `cadence.strategy-generation.adapter=fake`.
Production и неизвестные adapter configurations fail closed, даже если worker включён.
Real adapters #120 и финансовые/retention policies этим флагом не квалифицируются.

Не делать вручную UPDATE terminal operation в RUNNING/SUCCEEDED и не стирать dispatch intent.
OUTCOME_UNKNOWN требует будущей reconciliation, а не blind retry. После устранения cause
пользователь может submit новый request/key; это новая operation и новое согласование source.
Metrics/operator recovery и crash/restart drills описаны в [runbook11](ai-jobs-recovery-runbook.md).

## Security boundaries

- BOLA/revoked access: current DB actor + scope на HTTP/worker/commit; opaque foreign404.
- Prompt injection: model получает bounded typed data без shell/fetch/tools; citations могут
  ссылаться только на selected admissions и exact quotations/structured field paths.
- Consent/physical races: before-egress и before-publication metadata + retained-byte checks;
  FETCHED/CLEAN не заменяют explicit review/admission. SSRF fetch не добавлен.
- Lost lease/duplicate publication: attempt+worker+expiry и immutable SQL bindings;
  content/result/job commit атомарен. Это at-most-one local publication, не exactly-once remote call.
- SQL bypass/history: constraints, triggers, restricted grants; no invented legacy provenance.
- Abuse/privacy: bounded input5/20000 UTF-8 bytes, snapshots/results64KiB, attempts3,
  deadlines, capacity и retained cap; safe errors/no-store. Production load/rate/budget policy pending.

Подробные фактические проверки и ограничения: [отчёт10](prompt10-durable-generation-report.md).

## Completion и accounting seam11 →12

Каждая новая attempt закрывается атомарно с Job transition: SUCCEEDED, RETRY_SAFE,
SUPERSEDED, FAILED, EXPIRED или OUTCOME_UNKNOWN. Старый undispatched lease получает
RETRY_SAFE перед созданием следующей attempt. Dispatch timestamp и analysis link не меняются
при cleanup. SQL не позволяет закрыть attempt отдельно от coherent operation/Job state.
Legacy schema0 completion остаётся unknown; explicit schema0 insert для новых rows запрещён.

`errorCode` описывает наблюдение: INPUT_UNAVAILABLE, SOURCE_OR_ACCESS_CHANGED,
INVALID_OUTPUT, DEADLINE_EXPIRED, PROVIDER_OUTCOME_UNKNOWN, CAPABILITY_UNAVAILABLE,
PUBLICATION_REJECTED, LEASE_EXPIRED_BEFORE_DISPATCH; GENERATION_FAILED — generic fallback,
когда точная причина отсутствует. Для QUEUED/RUNNING/SUCCEEDED code null. Код не раскрывает
raw exception. INVALID_OUTPUT сохраняет existing OUTCOME_UNKNOWN status и no-retry policy.

`finishedAt-startedAt` — локальный жизненный цикл attempt, включая recovery, не provider
latency. Любой dispatch witness (также при SUPERSEDED) означает потенциальное remote spend.
Ни finish, ни lease timeout не дают права освобождать будущий HELD_UNKNOWN reservation.
Usage/cost/moderation по-прежнему не выдумываются и не backfill.

## Shared budgets12

Reservation/dispatch/settlement реализованы [ADR0030](adr/0030-generation-budget-ledger.md).
`BUDGET_EXCEEDED` — controlled terminal FAILED reason при отказе worker admission до model I/O.
HTTP429 `generation_budget_exceeded` применяется к новым async и synchronous generation requests;
exact key replay не резервирует повторно. Current deployment supports isolated synthetic accounting;
neutral quote/usage records не включают live capability. [Qualification12](prompt12-budget-report.md).

## Strategy Playbook format13

New Strategy capture uses private snapshot schema2; analysis snapshot remains schema1.
The operation's original snapshot/checksum, request digest/idempotency scope and budget are immutable.
New result must be complete Playbook v2; partial output terminates without a result and retains
uncertain cost. Model result never selects source/actor/approval status. Existing pre-egress,
source/access/deadline/lease and final publication guards are unchanged.

Terminal schema1 Strategy operations retain historical reads/replay. Pending schema1 captures
are rejected before provider egress after upgrade; do not rewrite them. Wait for terminal state,
review current source, then submit a new request/key. Full result generation receipt stores
format2 + original canonical output checksum. See [ADR0029](adr/0029-full-playbook-contract.md).
