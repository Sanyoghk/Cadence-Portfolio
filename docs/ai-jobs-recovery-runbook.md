# AI Jobs: recovery и диагностика

Контракт: [ADR0028](adr/0028-durable-ai-workflow-contracts.md),
[runtime](durable-ai-generation.md). Только provider-independent backend;
production AI adapters по-прежнему disabled. Public cancellation API отсутствует.

## Безопасная диагностика

1. Через authenticated OWNER `GET /api/v1/jobs/{jobId}` проверить status, deadline,
   attempts и `errorCode`. Access проверяется заново; чужой Job не раскрывается.
2. В разрешённой operator SQL session использовать параметризованный read-only запрос ниже.
   Не выбирать snapshot, result_json, user content, payload или credentials. Не менять
   immutable history вручную. Обычный пользователь не получает SQL/operator доступ.
3. Сопоставить opaque job/operation/attempt с `ai_attempt_completed` в logs. Эти события
   записываются только afterCommit; crash между commit и log может потерять событие.
   Источник истины — PostgreSQL, не logs/counters.
4. `ai_worker_poll_failed code=WORKER_UNAVAILABLE` требует проверки доступности DB,
   connection pool и contention через штатный monitoring. Событие не содержит SQL exception.
   Polling backoff: 2/4/8/16/30s; после успеха снова fixedDelay1s.

```sql
-- Bind :job_id as UUID using the operator's SQL client.
SELECT o.id AS operation_id, o.job_id, o.kind, o.status,
       o.submitted_at, o.deadline, o.completed_at,
       j.attempts, j.next_attempt_at, j.lease_expires_at,
       a.attempt_number, a.started_at, a.dispatched_at,
       a.finished_at, a.outcome, a.reason, a.completion_schema
FROM ai_generation_operations o
JOIN jobs j ON j.id=o.job_id
LEFT JOIN ai_generation_attempts a ON a.operation_id=o.id
WHERE o.job_id=:job_id
ORDER BY a.attempt_number;
```

| Состояние | Действие |
| --- | --- |
| RUNNING, valid lease | Дождаться bounded call; не запускать второй вызов вручную |
| Expired lease, no dispatch | Worker создаст новую bounded attempt при current source/access; прежняя RETRY_SAFE |
| Expired lease после dispatch | Worker завершит OUTCOME_UNKNOWN; автоматического retry нет |
| RETRY_WAIT / INPUT_UNAVAILABLE | Проверить storage availability; retry сохраняет exact capture, общий deadline60s |
| SUPERSEDED / SOURCE_OR_ACCESS_CHANGED | Старый captured input больше не допустим; повторная generation требует нового review/source/request по policy |
| EXPIRED | Истёк original deadline; не продлевать lease/deadline в SQL |
| OUTCOME_UNKNOWN / INVALID_OUTPUT | Невалидный returned output обнаружен server-side; publication отсутствует, расходы неизвестны |
| OUTCOME_UNKNOWN / PROVIDER_OUTCOME_UNKNOWN | Нет достоверного результата исполнения/списания; не считать failure бесплатным |
| SUCCEEDED | Один committed result, но human approval остаётся отдельной операцией |

Withdrawal/archive блокируют дальнейший egress/publication при повторной проверке.
Они не могут отозвать уже переданные bytes и не означают remote cancellation.
Новый request/key не примиряет предыдущий unknown outcome. Будущий budget12 обязан
сохранять hold неизвестных расходов, включая superseded calls с dispatch witness.
Ни status, ни finish time не являются измеренной provider latency/cost/moderation.

## Metrics и ограничения

`AiGenerationTelemetry` хранит process-local counters по шести enum outcomes и pollFailures.
`completedCounts()` доступен как internal bean и JMX operation, `getPollFailures()` — JMX attribute
при явном `spring.jmx.enabled=true`. ObjectName `com.cadence:type=AiGenerationMetrics`.
Новые HTTP endpoints и remote JMX listener не добавлены. Remote JMX/TLS/authentication
не активировать без отдельной operational qualification. Counter labels не содержат IDs.
Counters обнуляются после restart; использовать SQL для durable totals, не для финансового ledger.

Стандартный scheduler обрабатывает один call за tick, максимум100 ready/recoverable candidates.
Ожидающие retry и valid leases не занимают первые100. Executor ограничен двумя threads без
очереди; если оба provider threads игнорируют interruption, новые claims/dispatch откладываются,
а recovery/expiry остаются доступны. Перезапуск освобождает локальные threads, но не доказывает
остановку remote execution. Distributed quotas/стоимость остаются scope12.

## Воспроизводимые isolated recovery drills

Требуются Java21, Maven и Docker. Использовать только disposable Testcontainers PostgreSQL.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress test '-Dtest=PostgreSqlAiRecoveryProcessTest,PostgreSqlAiGenerationTest,PostgreSqlAiCompletionMigrationTest,AiGenerationIntegrationTest,AiGenerationSchedulingTest'
```

`PostgreSqlAiRecoveryProcessTest` запускает отдельные JVM с test-only `AiRecoveryProcess`:

- `claim`: halt81 после committed claim, до dispatch; новая JVM получает новый attempt,
  проверяет прежний checksum и публикует один result.
- `dispatch`: halt82 внутри fixture adapter после committed witness; новая JVM ставит
  OUTCOME_UNKNOWN, повторный dispatch отсутствует.
- `publication`: PostgreSQL test trigger задерживает transaction публикации. Test parent
  видит PgSleep, принудительно завершает только свой child process и его DB backend,
  затем новая JVM проверяет rollback и отсутствие result.
- `commit`: halt83 после успешного publication commit; новая JVM читает тот же result,
  не создавая новый attempt или provider call.

Fixture Clock детерминированно пересекает lease через21s; deadline не продлевается.
Это реальный restart application process и persisted PostgreSQL state, но не outage самого DB
или сети provider. Test-classpath-only launcher не входит в production JAR. Child credentials
только disposable Testcontainers, передаются environment, не command arguments.
Логи сценариев: `target/recovery-process-logs/`; они содержат исключительно synthetic fixtures.

Нельзя выполнять fixture trigger, pg_terminate_backend или UPDATE terminal rows на shared/production DB.
Для production неизвестных outcomes нужен будущий trusted provider reconciliation contract #120/12.

## Обновление V43 → V44

Остановить старые workers перед migration, применить V44 migration identity, затем
`docker/postgres/runtime-permissions.sql` для новых completion columns и запустить новую
версию. Не смешивать старые и новые writers: V43 worker не записывает finish evidence,
поэтому новые SQL seals справедливо отклонят его terminal commit. Это planned maintenance
cutover, не обещание rolling downgrade compatibility. Незавершённые старые attempts остаются
recoverable по witness/lease; terminal legacy rows сохраняют NULL completion evidence.
`completion_schema=0` обозначает только уже существовавшие rows; новые inserts обязаны быть1.
Production rollout этой задачей не выполняется.

## Budget recovery после V45

Перед V45 остановить pre-V45 writers; затем migration и `docker/postgres/runtime-permissions.sql`.
Не удалять ledger/operation/key history и не переводить HELD_UNKNOWN в RELEASED вручную.
Lease expiry означает loss of publication authority, а не доказательство no execution/no charge.
Legacy pending operations без reservation не backfill: новый request допускается только обычным admission.

Read-only диагностика (через отдельный authorized operator DB session, не публичный endpoint):

```sql
SELECT state, usage_origin, count(*), sum(reserved), sum(actual)
FROM generation_reservations GROUP BY state, usage_origin;
SELECT count(*) FROM generation_reservations WHERE actual > reserved;
```

Не интерпретировать NULL actual как0. HELD_UNKNOWN удерживает spend и remote dispatch slot после
restart/rollover. Новые operations могут получать429/BUDGET_EXCEEDED. Проверить отсутствие
current provider activity; real reconciliation evidence/adapter остаётся #120 scope. Нельзя
исправлять content outcome через accounting: terminal OUTCOME_UNKNOWN не становится SUCCEEDED.
Internal `GenerationBudgets.reconcile` принимает только typed trusted evidence, не client DTO;
повтор exact evidence no-op, конфликт rejected. У synthetic fixtures namespace/units не смешиваются
с MEASURED. Overrun сохраняется честно и блокирует scope; automatic reset отсутствует.

[ADR0030](adr/0030-generation-budget-ledger.md) содержит limits и lock order;
[report12](prompt12-budget-report.md) — actual tests, включая child-JVM recovery ledger assertions.

## Playbook V46 / snapshot2 upgrade

Перед upgrade остановить admission/старые generation workers; не смешивать версии producer/worker
при смене Strategy snapshot contract. Применить V46 migration и обновлённый `docker/postgres/runtime-permissions.sql` до запуска
нового worker. Новые Strategy captures используют schemaVersion2; analysis schema1 не меняется.
Не изменять snapshot/checksum старых operations. Terminal history/replay остаются историческими.
Pending schema1 Strategy capture после upgrade fail-closed до provider egress; дождаться terminal
state, заново проверить current approved Identity и отправить новый request/key. Не освобождать
HELD_UNKNOWN вручную. UI должен отправлять полный v2 Playbook при редактировании v2 revision;
legacy upgrade — explicit full edit, а не migration backfill. См. [report13](prompt13-playbook-report.md).

## Content V48 / snapshot3

CONTENT использует тот же AI_GENERATION poller, lease/attempt fencing и accounting, не ingest worker.
Перед upgrade остановить старые workers/admission, применить V48 и runtime grants, затем запускать
новую версию. Analysis schema1 / Strategy schema2 остаются совместимыми; private history не rewrite.
Live Content adapter остаётся disabled, local/test demo — отдельный adapter setting.

`content_generation_batches.operation_id` — unique immutable receipt. Его SHA-256 связывает original
`ai_generation_operations.result_json`; generated Post revision содержит batch/slot link. Source
request/captured Strategy/Identity/evidence lineage находятся в private operation snapshot, клиенту
возвращается bounded provenance DTO. Не редактировать эти данные SQL и не переписывать approval.
`SUPERSEDED` у Post revision означает заменённый DRAFT, у Job — отказ от stale publication.

До dispatch recovery допускает максимум3 attempts в60s; после dispatch uncertain outcome не
redispatch. `HELD_UNKNOWN` не ноль и не освобождается из-за отсутствия опубликованных Posts.
Если synthetic completion уже durably settled, последующий rollback publication не отменяет usage.
При retry/replay использовать существующий Job/key; новую operation нельзя выдавать за безопасный
повтор неизвестного provider outcome. Human review не является разрешением обойти source checks.

Проверка восстановления: `PostgreSqlAiRecoveryProcessTest` выполняет claim/dispatch/publication/
commit crash в отдельной JVM для DECLARED и CONTENT; restart проверяет один result или explicit
OUTCOME_UNKNOWN, immutable snapshot и coherent accounting. Content publication crash обязан
откатить весь batch/Posts. [API](content-generation-api.md), [ADR0032](adr/0032-content-generation-jobs.md).
