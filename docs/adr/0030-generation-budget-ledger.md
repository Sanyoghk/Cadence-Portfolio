# ADR0030 — Реализация общего generation budget ledger

Дата: 2026-10-16. Engineering refinement принятого ADR0028, задание12 / local draft B.
Product pricing, live transmission, provider selection и commercial activation не утверждаются.

## Решение и alternatives

Один `generation_budget_mutex` сериализует короткие admission/accounting transactions после
parent/operation/Job locks. Все account (actual CadenceUser), Workspace и global проверки
выполняются атомарно до reservation. Альтернатива — отдельные изменяемые counters/buckets:
она масштабируется лучше, но требует recovery/reconciliation counters и дополнительного lock order.
Для MVP выбран один mutex и aggregate queries по immutable ledger с indexes. Network/model I/O
никогда не удерживает mutex; поздний accounting path не захватывает parent locks.

Async scoped key mapping10 сохраняется: actual actor + Workspace + kind + key digest;
canonical `ai-request-v1` включает Brand ID, kind/mode, expectedVersion. Replay после actual
authorization возвращает прежнюю operation и не резервирует повторно. Conflict409, foreign404,
unknown fields400. Sync POST остаются отдельными server request IDs; approved-edit/new-DRAFT
branching сохраняет expectedVersion, без придуманного idempotency contract.

## Capture и состояния

`GenerationReservation` связывает request ID, optional async operation ID, actor, Workspace,
Brand, kind и request digest. Immutable: policy version + bounded numeric policy snapshot,
quote version, namespace, unit/scale, UTC period и original deadline/upper bound.
Для sync analysis request ID равен original analysis receipt ID, deadline читается из receipt;
для sync Strategy — generation ID и digest command + approved Identity source.

- RESERVED: estimated upper bound зарезервирован; remote execution не отмечено.
- HELD_UNKNOWN: durable dispatch intent записан перед model I/O; actual NULL означает UNKNOWN.
- SETTLED: отдельно сохранены validated usage origin, actual integer amount, evidence digest/time.
- RELEASED: только из RESERVED, при доказанном отсутствии dispatch. Timeout не release.

Async reservation и operation/Job создаются одной transaction. Dispatch witness attempt и
ledger фиксируются одной transaction; PostgreSQL deferred seals сверяют их в обе стороны.
Pre-dispatch retry не выполняет model call и использует ту же reservation. Post-dispatch
ambiguous outcome не retry-safe; reservation остаётся held до trusted reconciliation.

Known synthetic completion сохраняется перед content publication; rejected/superseded output
не отменяет учёт вызова. Reconciliation не меняет operation/result/history. Повтор одинакового
settlement no-op, отличающийся settlement conflict. Actual > reserved сохраняется без clamp,
дальнейшие calls затронутых scopes запрещаются, включая global, без silent reset.

Period rollover исключает лишь старые SETTLED amounts из нового period spend. Все RESERVED/
HELD_UNKNOWN остаются в admission safety balance независимо от original period; overrun
остаётся blocker. Неизвестный remote in-flight call удерживает distributed dispatch slot;
локальная lease/thread capacity и accounting slot — разные ограничения.

## Fixture policy, не тариф

Только `(local | test) & !prod`, `cadence.ai-budget.mode=synthetic` и adapter=fake.
Отсутствующая/невалидная policy запрещает dispatch. Synthetic unit `SYNTHETIC_CREDIT`, scale0,
quote `synthetic-fixed-v1`: успешный fake call =1 synthetic credit. Это не token count, invoice,
измеренная live cost, moderation или provider latency. `GenerationUsage` различает MEASURED и
SYNTHETIC; текущий synthetic ledger отвергает MEASURED и смешанные units/scales.
`GenerationQuote` и domain/SQL contract также принимают neutral LIVE quote + MEASURED usage
с matching unit/scale и отдельной namespace; это typed accounting contract, не выбранный тариф.
Current admission/dispatch service разрешает только synthetic namespace; LIVE dispatch запрещён.
Live policy, quotes и provider evidence reconciliation будут qualified с #120.

Fixture defaults: upper1; spend global1000000/account100000/workspace10000; queued1000/100/100;
running100/20/10; retained1000000/10000/1000. UTC day — только fixture period. Limits bounded:
upper<=10^9, spend<=10^12, queued<=10000, running<=1000, retained<=10^6. Values must be positive.
Amounts integer, aggregate checked through exact decimal arithmetic, never floating point.
History/key TTL отсутствует; retained cap закрывает рост, не стирая operation replay.

## Совместимость и upgrade

V1–44 не изменяются. V45 additive, без legacy cost/reservation backfill. Перед migration/grants
остановить старые generation writers; mixed V44/V45 writers не поддерживаются. Legacy pending
operation без reservation не допускается к новому dispatch. Existing terminal history остаётся
неизменной. Runtime запрещено переписывать capture, settlement/history и mutex identity.

Source/access checks сохраняются; synchronous evidence/Strategy повторяют preflight после
budget admission. Если процесс останавливается после intent, но до I/O, система консервативно
держит UNKNOWN: database commit не может доказать, был ли внешний вызов. Public cancellation,
settlement/debug/admin bypass endpoints не добавляются.

## Threat model

Защищаем tenant content, scoped operation mapping и ограниченные compute/spend resources.
Callers API недоверенные OWNER; worker/provider responses могут повторяться и опаздывать.

| Threat | Protection | Verification |
| --- | --- | --- |
| Replay/collision/BOLA | actual authorization, actor/tenant/kind key scope, request digest, unique mapping | existing replay/foreign tests + one reservation assertions |
| Race across accounts/workspaces | shared database mutex, all-scope atomic admission | PostgreSQL concurrent actors/global cap and account cross-Workspace tests |
| Crash resets spend | intent before I/O; UNKNOWN retained across lease/restart/rollover | real child JVM tests + deterministic unknown/rollover tests |
| Forged cost/late publication | no HTTP settlement; typed internal evidence; SQL history seals; independent publication fences | SQL/runtime tampering, duplicate settlement, stale worker tests |
| Overflow/resource exhaustion | exact integer/decimal arithmetic, bounded quote/config/history, distributed slots | Long.MAX_VALUE overrun, boundaries, retained/cap tests |
| Information disclosure | fixed429/code and safe Job BUDGET_EXCEEDED; no raw provider data | HTTP safe error and existing logging tests |

Required qualification/results: [report12](../prompt12-budget-report.md). This ADR alone is not
proof of executed gates or production readiness.
