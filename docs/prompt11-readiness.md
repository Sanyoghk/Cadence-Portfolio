# Задание 11 — точка входа после Spring migration

Проверка prerequisites: 2026-10-16. Ниже сохранён исходный checklist после migration.
Реализация и фактическая qualification11: [отчёт](prompt11-recovery-report.md);
операторские сценарии: [runbook](ai-jobs-recovery-runbook.md).
Baseline durable generation: `4833e92571886d2f698061b2195e1c89ee4d2800`.
Migration qualified locally: full1385/0failures/errors, Linux35/35, OSV194/0findings,
mandatory reports и packaged startup PASS. Нового remote CI нет.
Квалификация нового stack: [отчёт migration](spring4-migration-report.md).

## Scope и prerequisites

Применять prompt00, AGENTS.md, [ADR0028](adr/0028-durable-ai-workflow-contracts.md),
[ADR0027](adr/0027-backend-first-ai-adapters-last.md) и
[runtime contract](durable-ai-generation.md). Задание10 доставило базовый durable workflow;
11 завершает recovery/operational часть **local draft A**, #107 Task2.4.
Actual GitHub issue number для A не назначен: использовать
[exact issue draft](ai-workflow-issue-drafts.md), не выдумывать номер и не создавать duplicate.
#107 прочитан заново; remote checklist отстаёт от local slices и не является доказательством
отсутствия реализации. #121/#120 остаются open; выбор provider не prerequisite11.

## Что уже есть — не реализовывать повторно

| Contract | Code / proof |
| --- | --- |
| Durable capture, scoped202/Location/status/result, minimal key replay | `AiGenerationService`, `AiGenerationUseCase`, `AiGenerationController`; `AiGenerationContract` |
| Atomic claim, lease/attempt fencing, one committed result | `AiGenerationService.claim/current/publish`, V43; `PostgreSqlAiGenerationTest` |
| Pre-dispatch lease recovery, bounded retry/deadline | `claim`, `retryBeforeDispatch`; `restartBeforeDispatchClaimsNewAttemptAndRejectsOld`, `deadlineNeverRenewedByRetry` |
| Post-dispatch ambiguity, no blind retry | durable `AiGenerationAttempt.dispatchedAt`; `lostLeaseAfterDispatchIsUnknownAndNeverRedispatched`, `providerFailureIsUnknownWithoutBlindRetry` |
| Duplicate delivery and concurrent claim | `simultaneousRedeliveryDoesNotCancelTheCurrentCall`, `twoWorkersOnlyOneClaim`, `redeliveryAfterSuccessDoesNotPublishTwice` |
| Source/access/version withdrawal fences and physical I/O | `AiGenerationSources`, `IdentitySourceVerification`; shared API/PG contracts including mixed PDF/URL/social |
| Bounded model execution | Fixed2-thread executor without backlog, remaining lease timeout, no unbounded replacement threads |
| Human approval separately; production disabled | Existing copy/approval flow; local/test fake capability guard |

## Remaining11 — acceptance matrix to implement and qualify

| Gap | Acceptance / evidence required |
| --- | --- |
| Attempt completion provenance | Сохранить согласованные finish time/outcome/safe reason каждой attempt; immutable dispatch witness; migration/runtime constraints; без выдуманного legacy provider usage |
| Controlled operational error codes | Различать pre-dispatch unavailable, expired, revoked/stale, invalid output, uncertain provider outcome без раскрытия exception/input/credentials |
| Operational visibility | Связать Job/operation/attempt через opaque IDs; metrics с bounded labels, без prompts/source text/tokens/user content и неограниченных per-ID labels |
| Restart qualification | Existing deterministic lease tests не доказывают recovery после kill/restart процесса; добавить воспроизводимые crash-before-dispatch, after-witness, during-publication, after-commit recovery scenarios |
| Bounded poller and overload | Проверить synchronous `AiGenerationScheduling.tick → runOnce`, DB contention, batch100 fairness, capacity2 and ignored-interrupt behavior; доказать отсутствие hot loops и unbounded queues при retries/backoff |
| Recovery/revocation races | Расширить deterministic PostgreSQL tests completion/recovery/late stale worker; сохранить parent-first locks и SQL publication seals |
| Runbook | Воспроизвести states isolated fixtures; read-only diagnosis, без прямого UPDATE terminal state и утечки content; явные действия для OUTCOME_UNKNOWN |
| Budget seam for12 | Unknown execution/spend остаётся unknown; без выдуманных cost/latency/moderation и скрытого reservation release; budget implementation не входит в11 |

Семантика отмены уже определена ADR0028: **публичной cancellation API в10–12 нет**.
Withdrawal/archive запрещает будущую публикацию, но не отменяет уже отправленный remote call.
Не добавлять обещание CANCELLED или provider cancellation без reviewed contract.

## Required qualification

Сначала targeted failure injection, затем полный `mvnw.cmd clean verify` с Docker,
mandatory PostgreSQL/evidence report gates, Python10, renderer29, complete Linux filesystem
suite and packaged startup with worker enabled. Добавить meaningful tests для оставшихся gaps;
сохранить assertions tests10. Проверить OpenAPI/runtime/docs и совместимость истории V43.
Изменения схемы — V44+, существующие migrations не переписывать. Spotless перед local commit.

Из этого handoff не следуют изменения provider activation, approved source scope, privacy, pricing, retention TTL,
public cancellation, whole-issue push policy, merge или deployment.
При старте11 проверить actual HEAD/status и процессы; записанные counts являются историческим evidence.
