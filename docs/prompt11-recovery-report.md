# Задание11 — AI Jobs recovery и operational visibility

Дата: 2026-10-16. Ветка `mvp`; starting HEAD `a21e99f9a1ddf836274d80f09522a9899c82c3c3`.
Prerequisite10 — `4833e92571886d2f698061b2195e1c89ee4d2800`; Spring migration qualification
проверена по [отчёту](spring4-migration-report.md), actual clean HEAD и сохранённым gates.
Работа после user resume продолжена без reset/reimplementation10. [GitHub #107](https://github.com/Sanyoghk/Cadence/issues/107) перечитан;
read-only status [#120](https://github.com/Sanyoghk/Cadence/issues/120)/[#121](https://github.com/Sanyoghk/Cadence/issues/121) повторно подтверждён open16 октября;
local draft A остаётся без выдуманного remote issue number. #121/#120 остаются open.

Статус: **QUALIFIED LOCAL — scope11 / recovery часть draft A завершена**.
Full1405/0failures/errors;3Windows skips покрыты Linux35/35. Python10/10,renderer29/29,
mandatory reports, coverage, packaged startup и current-SBOM OSV194/0 findings PASS.

## Что было сделано

Завершены remaining recovery/operations contracts поверх уже существовавшего durable workflow10.
Attempt теперь хранит время завершения, outcome и безопасную причину. Worker обнаруживает только
готовые/recoverable операции, выдерживает зависший adapter без размножения threads и делает
polling backoff при infrastructure failure. Оператор получает correlation и bounded counters.
Реальный аварийный restart JVM проверяет сохранность состояния PostgreSQL и один local result.

## Acceptance matrix11

| Remaining requirement | Implementation / evidence |
| --- | --- |
| Finished attempt provenance | AiGenerationAttempt/V44; completionIsDurableAndSafeCodeContainsNoProviderDetails, recoveryClosesOldAttemptBeforeNewLeaseAndSuccess; SQL immutability/runtime/migration tests |
| Controlled operational codes | Job errorCode; invalid-output, provider-failure, expired lease/deadline tests и strict OpenAPI |
| Correlation / bounded metrics | afterCommit AiGenerationTelemetry; captured log assertions, fixed enum labels, local JMX test |
| Actual restart | PostgreSqlAiRecoveryProcessTest: four fresh JVM crash/recovery scenarios |
| Bounded polling / overload | ready predicate before LIMIT100, ignoredInterruptsDoNotCreateReplacementThreadsOrDispatchExtraWork, AiGenerationSchedulingTest |
| Revocation / late workers / SQL races | concurrentRecoverySealsOneUnknownAttemptAndRejectsLateWorker + existing two-worker/source/access/withdrawal contracts |
| Reproducible runbook | ai-jobs-recovery-runbook.md: parameterized read-only diagnosis, fixture command, unknown outcome handling |
| Cancellation / budget12 seam | no cancel/execute routes; dispatch witness retained, no fabricated usage/zero charge/hold release; budgets not implemented11 |

## Как это работает

`AiGenerationService.claim` восстанавливает expired undispatched attempt, атомарно закрывает
её RETRY_SAFE и получает новый lease при remaining deadline/attempt budget. Если dispatch уже
зафиксирован, recovery завершает OUTCOME_UNKNOWN без повторного вызова. `publish` закрывает
attempt вместе с result/receipt/citations, operation и Job. `cleanup` после revocation может
закрыть историю, но не публиковать content. Устаревший Ticket не меняет новую attempt.

`AiGenerationAttempt.finish` не меняет dispatch/receipt binding и запрещает повторное завершение.
`attempts.flush()` отправляет SQL внутри текущей transaction, но не делает отдельный commit.
V44 защищает те же правила в PostgreSQL; runtime identity имеет grants только на разрешённые
transition columns. Existing V1–43 не менялись. Legacy completion не backfill.

`AiGenerationOperationRepository.candidates` фильтрует valid leases и future retries до LIMIT100,
сохраняя стабильный порядок. Стандартный poller последовательный, вызовы model выполняются вне
transaction в existing fixed executor2/SynchronousQueue. Если threads не реагируют на interrupt,
новые claims откладываются; recovery/expiry продолжаются. Это local safety bound, не product quota.

## Архитектурное решение

Переиспользованы operation/Job/ports и parent-first locks из10. Новая broker infrastructure
не нужна: PostgreSQL уже хранит durable witness и публикует результат атомарно. In-memory-only
completion теряла бы audit после restart; blind retry после witness мог бы повторно списать деньги.

Выбран nullable completion с `completion_schema`: existing rows0, new rows1. Автоматическая
реконструкция прошлых outcome/usage была бы недостоверной. SQL guards допускают historical NULL,
но запрещают новым writers mint schema0. Старые V43 workers необходимо остановить перед V44:
новые terminal seals намеренно не принимают writes без completion evidence. Rolling mix версий
не обещается; deployment не выполнялся.

Для metrics используются существующие Spring JMX и JDK LongAdder, без новой dependency или HTTP
endpoint. Counters имеют фиксированные enum keys; operation/job IDs присутствуют только в logs.
Callback afterCommit предотвращает ложный success event при rollback. Сбой после commit до callback
может потерять event: durable truth в DB, metrics не финансовый ledger.

Invalid returned analysis отличается кодом INVALID_OUTPUT при сохранённом OUTCOME_UNKNOWN из10.
Validation failure известен, trusted usage/execution evidence отсутствует; automatic retry запрещён.
Это уточнение [ADR0028](adr/0028-durable-ai-workflow-contracts.md), не новый provider contract.

## Security Review

Защищаем private source content, согласие, tenant boundaries и immutable generation history.
Callers — OWNER через HTTP, internal worker и недоверенный adapter. Границы доверия
(trust boundaries): HTTP→application, persisted snapshot→worker, provider output→validation,
worker→PostgreSQL. Повторные checks и SQL seals дают эшелонированную защиту (defense in depth).

- **Threat:** late/stale worker или повторная доставка публикует второй result.
  **Protection:** actual lease/attempt/source/access checks, parent-first locks, atomic publication,
  immutable terminal operations и completion seals.
  **Verification:** concurrent recovery, old Ticket rejection, existing two-workers/redelivery tests,
  PostgreSQL child-JVM crash before/after dispatch, during publication и after commit.
- **Threat:** direct SQL под runtime ролью переписывает witness/history или объявляет standalone success.
  **Protection:** V44 BEFORE guards + deferred transaction seals + column grants.
  **Verification:** completion rewrite/detachment negatives, restricted runtime positive/negative tests,
  populated V43 migration сохраняет original witness и unknown historical completion.
- **Threat:** model timeout/exception провоцирует платный повтор или ложное освобождение расходов.
  **Protection:** dispatch intent сохраняется до call; post-dispatch ambiguity terminal/no retry.
  **Verification:** timeout, ignored interrupt, crash-after-witness, provider failure, no duplicate result.
  Ledger здесь отсутствует: future12 должен учитывать все dispatch witnesses, включая SUPERSEDED.
- **Threat:** BOLA, role/actor forgery, revoked evidence/access, malicious model/source.
  **Protection:** unchanged secured use cases и fresh source/physical checks before egress/publication;
  server-selected sources, exact citations, no arbitrary tools/fetch.
  **Verification:** shared H2/PostgreSQL contracts и full regression suite; unknown request fields,
  foreign404, anonymous401, role403, withdrawn/changed source и membership races.
- **Threat:** resource exhaustion при зависшем provider или DB outage.
  **Protection:** fixed threads2 без backlog, max100 ready discovery, attempts3, deadline60s,
  lease20s, poller backoff2–30s; existing snapshot/input/history bounds.
  **Verification:** ignored-interrupt saturation, no third claim/dispatch, deterministic backoff,
  deadline/retry and two-worker tests. Distributed budgets остаются12.
- **Threat:** exception/provider text или source content попадают в operational logs/metrics/API.
  **Protection:** enum reason/outcome, opaque UUID correlation, no raw exception in worker telemetry,
  bounded metric labels, scoped Job API. Нет public cancellation/debug/admin routes.
  **Verification:** captured log assertions с hostile exception, fixed metric keys/JMX read,
  API/OpenAPI negative tests. Это не обещание blanket sanitization всех сторонних framework logs.

Authentication/CSRF/CORS не менялись: bearer-only authentication, server-side authorization,
frontend не является guard. Новые SQL statements параметризованы; dynamic fragments в tests
состоят из fixed allowlisted test strings. Child ProcessBuilder не использует shell/user input;
launcher находится только в test classpath, credentials disposable и передаются environment.
SSRF/file boundaries не расширены. Новые dependencies не добавлены.

## Изменённые файлы

| Файл | Изменение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Актуальная точка продолжения, Task2.4 и remaining12/13. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Синхронное состояние русского плана и dependencies. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Column grants для completion; immutable fields по-прежнему запрещены. |
| [docs/adr/0028-durable-ai-workflow-contracts.md](../docs/adr/0028-durable-ai-workflow-contracts.md) | Уточнение completion/schema compatibility, safe codes и observability. |
| [docs/ai-jobs-recovery-runbook.md](../docs/ai-jobs-recovery-runbook.md) | Read-only diagnosis, bounded polling, four JVM drills, V44 cutover. |
| [docs/ai-workflow-issue-drafts.md](../docs/ai-workflow-issue-drafts.md) | Mapping scope A10–11 и отдельные B12/P13. |
| [docs/durable-ai-generation.md](../docs/durable-ai-generation.md) | Additive errorCode, completion и accounting seam. |
| [docs/prompt11-readiness.md](../docs/prompt11-readiness.md) | Исторический checklist связан с actual report11. |
| [docs/prompt11-recovery-report.md](../docs/prompt11-recovery-report.md) | Этот отчёт: architecture, security, files, verification и limits. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Missing/skipped/failed новые reports не дают false green. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Обязательные reports новых migration/process-restart suites. |
| [src/main/java/com/cadence/application/exceptions/BrandAnalysisUnavailableException.java](../src/main/java/com/cadence/application/exceptions/BrandAnalysisUnavailableException.java) | Контролируемый internal flag invalid returned output без raw cause. |
| [src/main/java/com/cadence/application/repositories/AiGenerationOperationRepository.java](../src/main/java/com/cadence/application/repositories/AiGenerationOperationRepository.java) | Ready/recoverable predicate до LIMIT100. |
| [src/main/java/com/cadence/application/services/AiGenerationService.java](../src/main/java/com/cadence/application/services/AiGenerationService.java) | Completion transactions, safe codes, capacity check, afterCommit telemetry. |
| [src/main/java/com/cadence/application/services/AiGenerationTelemetry.java](../src/main/java/com/cadence/application/services/AiGenerationTelemetry.java) | Bounded counters, opaque correlation, opt-in JMX без нового endpoint. |
| [src/main/java/com/cadence/application/services/BrandIngestService.java](../src/main/java/com/cadence/application/services/BrandIngestService.java) | Разделение port exception и local output-validation failure; прежний safe exception contract. |
| [src/main/java/com/cadence/domain/AiGenerationAttempt.java](../src/main/java/com/cadence/domain/AiGenerationAttempt.java) | Finish/outcome/reason/schema и immutable lifecycle. |
| [src/main/java/com/cadence/infrastructure/analysis/AiGenerationScheduling.java](../src/main/java/com/cadence/infrastructure/analysis/AiGenerationScheduling.java) | Последовательный bounded polling/backoff и safe infrastructure event. |
| [src/main/resources/db/migration/V44__ai_attempt_completion.sql](../src/main/resources/db/migration/V44__ai_attempt_completion.sql) | Additive migration, history guards и deferred attempt/Job seals. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI generation Job errorCode enum/null. |
| [src/test/java/com/cadence/infrastructure/analysis/AiGenerationSchedulingTest.java](../src/test/java/com/cadence/infrastructure/analysis/AiGenerationSchedulingTest.java) | Deterministic backoff/recovery и local JMX access. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiCompletionMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiCompletionMigrationTest.java) | Populated V43 → V44, original witness, nullable legacy completion. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiGenerationTest.java) | SQL completion negatives и restricted runtime completion. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java) | Реальные child JVM crash/restart, rollback/one-result проверки PostgreSQL. |
| [src/test/java/com/cadence/support/AiGenerationContract.java](../src/test/java/com/cadence/support/AiGenerationContract.java) | Shared H2/PG concurrency, overload, privacy/codes, cancelled/debug-route negatives. |
| [src/test/java/com/cadence/support/AiRecoveryProcess.java](../src/test/java/com/cadence/support/AiRecoveryProcess.java) | Test-only isolated launcher и deterministic crash injection, отсутствует в app JAR. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Exact migration inventory44 вместо43; существующие integrity assertions сохранены. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Actual qualified11 ledger и remaining12/13. |

## Runtime Flow

```text
Scoped POST → captured immutable operation + Job →202
  → ready discovery → actual access/source claim → leased attempt
  → retained bytes verification outside locks → durable dispatch COMMIT
  → model I/O outside transaction, bounded wait
  → current physical/source/access/lease validation
  → result + attempt finish + operation + Job COMMIT
  → afterCommit safe correlation/counters → scoped Job/result
  → human copy/review/approval separately

Crash/restart → persisted witness + lease
  → no witness: bounded safe retry using same captured source
  → witness: OUTCOME_UNKNOWN, no retry and no silent cost release
```

## Тесты

Финальный full run: `var/prompt11-release-full.log`, 2026-10-16 01:42:26 Europe/London,
12:34 min. **138 suites,1405 tests,0 failures,0 errors,3 Windows skips =1402 executed pass**.
Все три skips — symlink cases `FileSystemEvidenceObjectStorageTest`, Linux35/35 без skips.
JaCoCo LINE7721 covered/440 missed = **94.61%**; configured coverage checks PASS.

| Фактически выполненная команда | Результат |
| --- | --- |
| `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=AiGenerationSchedulingTest,AiGenerationIntegrationTest,PostgreSqlAiGenerationTest,PostgreSqlAiCompletionMigrationTest,PostgreSqlAiRecoveryProcessTest'` |79/79,0failures/errors/skips; `var/prompt11-final-targeted.log` |
| `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PostgreSqlPersistenceTest'` |8/8 после exact inventory44; `var/prompt11-inventory-targeted.log` |
| `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check clean verify` |Final full1405, BUILD SUCCESS; Spotless/package/coverage PASS |
| `python -B -m unittest discover -s scripts -p 'test_*.py'` |10/10; `var/prompt11-python.log` |
| `python -B scripts/verify_postgres_report.py` |Required PostgreSQL suites executed без skips/failures |
| `python -B scripts/verify_evidence_reports.py` |Required PostgreSQL/MinIO/ClamAV/renderer/full pipeline PASS |
| `.\scripts\run_linux_storage_tests.ps1` |35/35,0skips; `var/prompt11-linux.log`; те же qualified source/classes |
| `.\var\prompt11-startup.ps1` |Packaged health200,4 anonymous protected requests401, discovery401/fixed Bearer; worker и JMX enabled |
| `python -X utf8 -B var/prompt11-collect.py` |533 stable source/build hashes; XML/coverage snapshot; test-only crash launcher отсутствует в application JAR |

Renderer команды, выполненные без сети и с read-only filesystem:

```powershell
docker build --tag cadence-prompt11-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt11-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Результат29/29, `var/prompt11-renderer-python.log`. Startup helper использовал
`java -jar target/cadence-0.0.1-SNAPSHOT.jar` с local profile, обоими fake adapters,
`--cadence.ai-jobs.worker-enabled=true --spring.jmx.enabled=true`, localhost:18099 и
isolated `var/prompt11-startup-evidence`. Остальные ingest/cleanup/artifact workers явно
отключены в этой startup fixture. Helper завершил только собственный процесс.

Security inventory:

```powershell
.\var\osv-scanner-2.5.1.exe scan source -L var/spring4-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt11-verification/osv-results.json
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt11-verification/osv-results.json
```

Свежий OSV scan выполнен по прежнему dependency inventory, поскольку pom/dependencies
не менялись; затем independent validator проверил его полное покрытие **вновь созданного**
runtime/test SBOM:194 Maven dependencies,0 OSV findings. Scanner SHA256:
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
Initial offline CycloneDX invocation отказался запускать online-only goal; повтор в online
mode BUILD SUCCESS. Это не failure runtime tests. Schema-keyword warnings CycloneDX
(meta:enum/deprecated) не скрыты; отдельный inventory validator PASS.

Evidence сохранён локально в ignored `var/prompt11-verification/`: source-manifest,
138 XML reports, jacoco.xml, summary.json, current bom.cdx.json и osv-results.json.
Новый источник не менялся во время финального run. Docs/diff checks: все28 changed files
описаны в отчёте,202 local links существуют, `git diff --check` PASS. Expected warnings: deprecated/unchecked
существующий test API, JVM class-data-sharing, Hikari connections после остановки disposable
Testcontainers. Required suite skips и test failures отсутствуют, кроме указанных OS skips,
которые проверены отдельно на Linux.


Промежуточные неуспехи не скрываются: первый targeted выявил неверную OpenAPI null schema;
следующий test-only launcher требовал исправления SpringApplication construction/package access;
позднее новый negative-route fixture нуждался в повторной установке principal после MockMvc cleanup.
JMX fixture исправлен: explicit bean map требует отключения autodetection вне BeanFactory.
Один ранний full остановлен для исправления stale errorCode после expiry; второй — после
выявленной ошибки JMX fixture. Они не считаются успешными full runs.
Общий migration suite потребовал обновить точный inventory43→44 после добавления V44;
полный run с прежним inventory завершился двумя assertions и также не считается PASS.
Expected production behaviors/security assertions не ослаблялись. Crash/restart4/4 отдельно прошёл
до финального full run. Исторические migration gates не выдаются за новые11 tests.

## Security Tests

Shared contracts: foreign Job/result404; anonymous401; reserved roles403; forged actor/key/request
и unknown DTO fields; current membership revocation до/во время call; source withdrawal и version
change; malicious citations; capability disabled in prod; missing approved Identity; no public cancel
или execute. SQL negatives: immutable capture/queue/attempt/source bindings, completion rewrite,
standalone completion, original witness preservation, restricted runtime grants. Полный suite
сохраняет TEXT/PDF/URL/social physical/history/copy/approval и Strategy guards.

## Edge Cases

- Crash before dispatch: previous attempt RETRY_SAFE, новый bounded lease, immutable checksum.
- Crash после witness, даже если remote request не дошёл: OUTCOME_UNKNOWN — conservative boundary.
- Crash during publication: rollback всех writes; после commit: тот же result без нового dispatch.
- Concurrent recovery: одна completion, late worker rejected, no duplicate local publication.
- Interrupted wait и ignored interruption: нет replacement thread growth, дополнительные jobs не dispatch.
- Invalid output: код INVALID_OUTPUT; исходный opaque status/no-retry сохраняется.
- Retried unavailable input / exhausted attempts / deadline: same capture и original deadline;
  RETRY_SAFE описывает отсутствие dispatch, но само по себе не разрешает четвёртую attempt.
- Legacy completion: NULL остаётся unknown; SQL не переписывает historical witness.
- Telemetry rollback/crash: counter/log не заменяют PostgreSQL audit; counters reset при restart.

## Непроверенные аспекты

Не квалифицированы real AI adapters, provider idempotency/cancellation/reconciliation, billing,
privacy/retention и external cost/latency/moderation. Synthetic fixtures этого не доказывают.
Crash tests перезапускают реальную JVM при живой PostgreSQL, но не моделируют потерю DB диска,
network partition, production orchestration или нагрузку распределённого deployment.
Новый remote CI/source SHA отсутствует: push этой задачей не выполняется. Deployment/merge отдельно.

## Что мне важно понять

Lease ограничивает право worker записать результат, но не отменяет уже отправленный remote call.
Поэтому надёжное восстановление зависит от сохранённого до вызова witness. Если process погиб,
backend не может узнать по одному timeout, был ли запрос исполнен и оплачен. Он запрещает повтор,
сохраняет неизвестность и допускает только один local commit. Human approval — другой workflow.

SQL deferred seals проверяют согласованность всей transaction: промежуточно operation/Job/attempt
могут обновляться по очереди, но commit невозможен, если итоговые состояния противоречат друг другу.
Метрики полезны для оператора, но crash может потерять событие; деньги и история требуют durable rows.

## Что мне стоит изучить

1. **Lease fencing:** проверка номера attempt, owner и срока защищает от устаревшего worker.
2. **Dispatch witness:** durable запись перед external I/O отделяет доказанно безопасный retry от ambiguity.
3. **Deferred constraints:** PostgreSQL проверяет связанные изменения на commit, сохраняя atomic publication.
4. **Backpressure:** bounded executor и отложенный polling ограничивают накопление работы при сбое.
5. **Observability cardinality:** фиксированные metric labels ограничивают расход памяти; IDs нужны в logs.

## Production considerations

До live activation нужны budget12, provider/privacy qualification120, retention decisions и trusted
usage/cost reconciliation. Применять V44 и runtime grants после остановки старых workers;
сохранить backup/recovery и monitoring PostgreSQL. JMX exporter opt-in, новый network listener
не включён; remote monitoring требует своей auth/TLS qualification. Не UPDATE terminal history.
[Runbook](ai-jobs-recovery-runbook.md) описывает read-only diagnosis и isolated recovery drills.

## Commit / push / issues и границы выводов

Local commit этого законченного slice: `feat(ai): qualify generation recovery and observability`.
Его SHA определяется `git log -1 -- docs/prompt11-recovery-report.md` (self-reference не требует
отдельного report-only commit). Parent: `a21e99f`; рабочая ветка mvp.
Push, remote CI, GitHub mutations/closure, merge и deployment не выполнялись.
#107/draft A сопоставлен в обоих планах; next12, но implementation12 не начат.
#121/#120 остаются open; production adapters disabled. Remote issue ID для A не выдуман.

- **Факты:** actual HEAD/changes, запущенные tests и counts выше; сохранённые SQL witnesses,
  repeated access checks, конкретные child-JVM kills/restarts и committed results.
- **Инженерный вывод:** backend обеспечивает bounded recovery и at-most-one local publication
  в проверенных сценариях; нельзя выводить exactly-once remote execution из этих результатов.
- **Assumptions / отдельная проверка:** доверенная migration/runtime конфигурация deployment,
  production clocks/infrastructure, будущие provider proofs и budget policy.
