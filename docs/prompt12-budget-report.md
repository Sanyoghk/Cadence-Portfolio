# Задание12 — generation idempotency и durable budgets

Дата: 2026-10-16. Starting HEAD `c8ee4e79f702166dafb549d2a20cc759424ca563`, branch `mvp`, clean.
Prerequisites09–11 проверены по actual HEAD, ADR0028, report11 и Spring qualification.
Read-only GitHub #107 перечитан; Task2.5 local draft B, отдельный remote номер не выдуман.

Статус: **QUALIFIED LOCAL** для согласованного backend scope12. Это не production activation.

## Что было сделано / как работает

Общий `GenerationBudgets` применяется к async Brand/Identity/Strategy и трём synchronous
use cases. Reservation создаётся атомарно с новой async operation, replay не дублирует ledger.
Проверяются account/Workspace/global spend, queued, dispatch и retained limits.
Model I/O вне DB transaction. Unknown расход остаётся NULL + HELD_UNKNOWN после crash/timeout.
Успешный fake вызов имеет synthetic cost, отдельно от content publication и human approval.

## Архитектурное решение

[ADR0030](adr/0030-generation-budget-ledger.md) фиксирует alternatives/trade-offs, immutable
capture, lock order, state machine и fixture limits. Один DB mutex проще counters и предотвращает
частичную reservation; цена — сериализация короткого admission и aggregate query cost.
Это provider-neutral control plane с synthetic qualification, не billing и не live adapter.

## Runtime Flow

Request → OWNER/resource authorization → canonical key/digest → replay либо source capture
→ operation/Job + reservation transaction → worker claim/source checks → attempt/ledger dispatch
transaction → model I/O → independent known usage settlement → final eligibility/lease/publication
checks → atomic result/DRAFT. Human approval остаётся отдельной операцией.

Sync: existing capture/preflight → actual actor/resource + shared budget dispatch → повторный
preflight для evidence/Strategy → model → synthetic settlement → existing final publication guards.
Approved edit/new-DRAFT не получает неоговорённой idempotency.

## Security Review

Threat: BOLA/forged actor/key collision. Protection: existing secured boundaries/current membership,
server-derived actor, scoped unique key/digest. Verification: replay/foreign/role/unknown-field tests.

Threat: concurrent overspend / workspace farming. Protection: atomic global/account/Workspace checks
under database mutex; immutable policy snapshot. Verification: two actors/global cap, account across
Workspaces, queued/running/retained caps и PostgreSQL races.

Threat: crash/late worker double debit or publication. Protection: dispatch intent before I/O,
unknown hold, unique request/attempt binding, idempotent settlement и separate content fences.
Verification: restart/failure injection, stale worker, duplicate reconciliation, publication failure.

Threat: SQL/history tampering. Protection: V45 checks, immutable guards, deferred bidirectional
attempt/ledger seals, column-scoped runtime grants. Verification: real PostgreSQL tampering/runtime.

Threat: overflow/data exposure/abuse. Protection: exact integer accounting, bounded aggregates and
policy, fixed errors, no public reconciliation/debug, no new dependencies. Verification: overrun,
HTTP429 и existing safe logging tests. TLS/Clerk/CSRF architecture не изменялась.

## Тесты

Предварительные runs: compile выявил обновляемые constructor fixtures; sandbox запретил Docker/
loopback, поэтому его skipped tests не считаются pass. После исправлений targeted114/114 прошёл.
Следующий run: H2 47/47; PostgreSQL выявил regex repetition bound в policy snapshot, исправлено
отдельной length check + allowlist. Targeted107/107 затем прошёл. Первый full выявил три отказа
старой FullEvidenceProviderIntegrationTest fixture без явного test/synthetic profile; конфигурация
fixture исправлена, real S3/ClamAV/renderer assertions сохранены. При review добавлены независимая
от session DST UTC-period проверка и safe503 для invalid policy. Targeted remediation273/273 затем прошла без failures/errors/skips, включая real providers,
PostgreSQL source/Identity/Strategy suites и сохранённый Strategy503 contract. Aggregate persistence
вынесена в GenerationBudgetLedger; final full gate проверяет final source hashes.

## Security Tests и Edge Cases

Same key→one operation/reservation; changed payload conflict; revoked membership/foreign scope denied.
Pre-dispatch retry retains reservation; expired undispatched release; post-dispatch timeout retains
NULL actual. Rollover holds survive. Exact deadline excluded. Known late/superseded synthetic result
still accounts cost. Conflicting settlement, mixed origin/unit/scale, negative/overflow inputs denied;
actual Long.MAX_VALUE retained honestly and blocks further admission. No history deletion/rewriting.

## Непроверенные аспекты / Production considerations

Provider/model/privacy/commercial limits не выбраны; live activation disabled. Synthetic usage не
доказывает measured provider usage/invoices/latency/moderation. Trusted reconciliation port не имеет
public API; real evidence acquisition/verification и handling overrun approval требуют #120 follow-up.
Deployment load, HA capacity sizing, backup/restore и live credentials отдельно. Global mutex needs
load measurement before scale-out; no silent unknown-hold reset or automatic history purge.

## Что мне важно понять

Operation success и incurred cost — разные факты. Timeout означает отсутствие достоверного ответа,
а не отсутствие работы на стороне provider. Поэтому истечение lease не возвращает деньги в budget.
Idempotency связывает конкретный request со старой operation, а не заново запускает генерацию.

## Что мне стоит изучить

1. Идемпотентность (idempotency): scoped key/digest сохраняет одну operation при повторе.
2. Намерение вызова (dispatch intent): запись до I/O сохраняется после crash.
3. Блокировка строки (row lock): сериализует quota check и запись между разными processes.
4. Неизменяемый журнал (immutable ledger): accounting history нельзя переписать cleanup-операцией.
5. Ограждение попытки (attempt fencing): поздний worker может сообщить usage, но не публиковать content.

## Факты, выводы и assumptions

Факты: final full завершён успешно; 140 suites,1439 tests:1436 passed,0failures/errors,3Windows skips.
Все543 source/build hashes сохранились; test process launcher отсутствует в production JAR.
Python10/10, renderer29/29, Linux filesystem35/35 выполнены без skips; новый OSV scan194 packages
без findings прошёл exact inventory validation против нового final SBOM.
Mandatory PostgreSQL/evidence validators PASS; Spotless и JaCoCo gates PASS.
Coverage: lines8038/8499 (94.58%), branches3655/5080 (71.95%).
Packaged startup local/fake: health200, четыре anonymous protected routes401, discovery401/fixed Bearer.
Warnings Hikari после остановки Testcontainers относятся к закрытым test databases; test failures нет.
Инженерный вывод: durable intent/unknown hold избегают ошибочного освобождения расхода при crash.
Assumptions: trusted adapter evidence и approved live policy появятся отдельным qualified scope;
fixture values не являются продуктовым тарифом. No push/CI/issue closure/merge/deploy в этой задаче.


## Выполненные команды

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=GenerationReservationTest,AiGenerationIntegrationTest,PostgreSqlAiGenerationTest,PostgreSqlGenerationBudgetTest,BuildStrategyUseCaseTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=GenerationReservationTest,FullEvidenceProviderIntegrationTest,PostgreSqlBrandAnalysisTest,PostgreSqlEvidenceAnalysisTest,PostgreSqlIdentityEvidenceTest,PostgreSqlPdfReviewTest,PostgreSqlSocialReviewMigrationTest,PostgreSqlSocialReviewTest,PostgreSqlStrategyApiTest,PostgreSqlStrategyBuildTest,PostgreSqlGenerationBudgetTest,AiGenerationIntegrationTest,StrategyApiFailClosedTest,BuildStrategyUseCaseTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt12-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt12-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt11-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt12-verification/osv-results.json
```

Логи последовательных проверок сохранены в ignored `var/prompt12-*.log`. Первый full1436:
49failures/61errors/3Windows skips — это обнаруженные fixture/contract failures, не successful gate.
Final full: BUILD SUCCESS,16:50,2026-10-16 02:56 BST, `var/prompt12-release-full.log`. No duplicate Maven processes.

## Acceptance matrix12

| Требование | Реализация и доказательство |
| --- | --- |
| Scoped key/request replay | Existing10 mapping retained; AiGenerationContract replay/collision/concurrent replay + one reservation |
| All generation routes | AiGenerationService + AnalyzeBrandUseCase/AnalyzeEvidenceUseCase/BuildStrategyUseCase share GenerationBudgets; sync bypass denied |
| Account/tenant/global limits | One DB mutex; GenerationBudgetLedger exact aggregates; two actors/global cap and account cross-Workspace tests |
| Retry/crash/unknown | Pre-dispatch retry reuses bound; unknown retained across rollover; four actual child-JVM recovery scenarios assert ledger |
| Settlement/overrun | Same evidence no-op; conflict denied; Long.MAX_VALUE honest overrun blocks calls; no publication from reconciliation |
| Neutral records | GenerationQuote + GenerationUsage namespace/unit/scale validation; synthetic and measured cannot mix; LIVE dispatch disabled |
| Deadline/SQL/runtime | Exclusive deadline, UTC/DST period, immutable capture/history, bidirectional attempt seal and runtime column grants |
| Original provenance/history | No V1–44 rewrites or backfill; populated migration assertion; source/copy/approval/legacy API suites retained |
| Compatibility | Synchronous response semantics retained, including strategy_generation_unavailable; approved-edit unchanged |
| Qualification | Targeted273/273; final full1439/0failures/errors; mandatory reports, coverage, startup, current SBOM/OSV PASS |

Fixtures use persisted actual actors/memberships and explicit test profile for synthetic analysis.
S3/MinIO, ClamAV, renderer и runtime-role assertions remain real; fixing fixture prerequisites
не заменяет negative assertions успешными ответами.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Согласованный Task2.5 status и следующий scope13. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Согласованный Task2.5 status и следующий scope13. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Минимальные runtime privileges для ledger и защита mutex identity. |
| [docs/adr/0028-durable-ai-workflow-contracts.md](../docs/adr/0028-durable-ai-workflow-contracts.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/adr/0030-generation-budget-ledger.md](../docs/adr/0030-generation-budget-ledger.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/ai-jobs-recovery-runbook.md](../docs/ai-jobs-recovery-runbook.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/ai-workflow-issue-drafts.md](../docs/ai-workflow-issue-drafts.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/api-contract.md](../docs/api-contract.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/durable-ai-generation.md](../docs/durable-ai-generation.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [docs/prompt12-budget-report.md](../docs/prompt12-budget-report.md) | Contracts, runbook, status/qualification и проверяемые ограничения. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | PostgreSQL ledger suite добавлен в mandatory report gate и test fixtures. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | PostgreSQL ledger suite добавлен в mandatory report gate и test fixtures. |
| [src/main/java/com/cadence/application/exceptions/GenerationBudgetExceededException.java](../src/main/java/com/cadence/application/exceptions/GenerationBudgetExceededException.java) | Контролируемая ошибка quota. |
| [src/main/java/com/cadence/application/repositories/GenerationBudgetLedger.java](../src/main/java/com/cadence/application/repositories/GenerationBudgetLedger.java) | Параметризованные точные агрегаты и чтение исходного deadline analysis receipt. |
| [src/main/java/com/cadence/application/repositories/GenerationBudgetMutexRepository.java](../src/main/java/com/cadence/application/repositories/GenerationBudgetMutexRepository.java) | Получение пессимистической блокировки mutex. |
| [src/main/java/com/cadence/application/repositories/GenerationReservationRepository.java](../src/main/java/com/cadence/application/repositories/GenerationReservationRepository.java) | Поиск по уникальному server request ID. |
| [src/main/java/com/cadence/application/services/AiGenerationService.java](../src/main/java/com/cadence/application/services/AiGenerationService.java) | Атомарные operation/reservation, binding dispatch к attempt, settlement и безопасный release. |
| [src/main/java/com/cadence/application/services/GenerationBudgets.java](../src/main/java/com/cadence/application/services/GenerationBudgets.java) | Общие policy/admission/dispatch/settlement/reconciliation, фактический actor и исходные deadlines. |
| [src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java](../src/main/java/com/cadence/application/usecases/AnalyzeBrandUseCase.java) | Общий budget для declared analysis; отказ admission завершает running receipt. |
| [src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java) | Общий evidence budget и повторный preflight. |
| [src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java) | Общий Strategy budget/digest, повторный preflight, сохранённый503 contract. |
| [src/main/java/com/cadence/domain/AiGenerationAttempt.java](../src/main/java/com/cadence/domain/AiGenerationAttempt.java) | Ограниченный код причины BUDGET_EXCEEDED. |
| [src/main/java/com/cadence/domain/GenerationBudgetMutex.java](../src/main/java/com/cadence/domain/GenerationBudgetMutex.java) | Идентификатор единственного неизменяемого DB mutex. |
| [src/main/java/com/cadence/domain/GenerationQuote.java](../src/main/java/com/cadence/domain/GenerationQuote.java) | Валидация neutral upper estimate, namespace, unit, scale и version. |
| [src/main/java/com/cadence/domain/GenerationReservation.java](../src/main/java/com/cadence/domain/GenerationReservation.java) | Неизменяемые bindings/policy, переходы состояния и типизированный settlement. |
| [src/main/java/com/cadence/domain/GenerationUsage.java](../src/main/java/com/cadence/domain/GenerationUsage.java) | Валидация measured/synthetic amount и evidence digest. |
| [src/main/java/com/cadence/infrastructure/analysis/GenerationBudgetFixtureConfiguration.java](../src/main/java/com/cadence/infrastructure/analysis/GenerationBudgetFixtureConfiguration.java) | Явный seed для local/test fixture; production использует migration. |
| [src/main/java/com/cadence/presentation/ApiExceptionHandler.java](../src/main/java/com/cadence/presentation/ApiExceptionHandler.java) | Контролируемый429 generation_budget_exceeded. |
| [src/main/resources/application-local.properties](../src/main/resources/application-local.properties) | Явные synthetic технические limits, не коммерческий тариф. |
| [src/main/resources/db/migration/V45__generation_budget_ledger.sql](../src/main/resources/db/migration/V45__generation_budget_ledger.sql) | Новый typed ledger, UTC/integrity/history guards и отложенные dispatch seals. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI429, BUDGET_EXCEEDED и поддерживаемая семантика. |
| [src/test/java/com/cadence/application/AnalyzeBrandUseCaseTest.java](../src/test/java/com/cadence/application/AnalyzeBrandUseCaseTest.java) | Явная budget dependency в unit/security fixtures, без application bypass. |
| [src/test/java/com/cadence/application/AnalyzeEvidenceCapacityTest.java](../src/test/java/com/cadence/application/AnalyzeEvidenceCapacityTest.java) | Явная budget dependency в unit/security fixtures, без application bypass. |
| [src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java](../src/test/java/com/cadence/application/BuildStrategyUseCaseTest.java) | Проверка порядка budget/preflight/model/settlement/publication. |
| [src/test/java/com/cadence/application/UseCaseAuthorizationTest.java](../src/test/java/com/cadence/application/UseCaseAuthorizationTest.java) | Явная budget dependency в unit/security fixtures, без application bypass. |
| [src/test/java/com/cadence/domain/GenerationReservationTest.java](../src/test/java/com/cadence/domain/GenerationReservationTest.java) | Unit tests typed usage, unknown/release, deadline, namespace и overrun. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Явная synthetic analysis fixture; реальные S3/ClamAV/renderer/runtime assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiCompletionMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiCompletionMigrationTest.java) | Проверка populated legacy migration без выдуманного ledger. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java) | Accounting invariants после реального child-JVM crash/restart. |
| [src/test/java/com/cadence/persistence/PostgreSqlBrandAnalysisTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandAnalysisTest.java) | Сохранённые account/membership и явный synthetic test profile. |
| [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlEvidenceAnalysisTest.java](../src/test/java/com/cadence/persistence/PostgreSqlEvidenceAnalysisTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlGenerationBudgetTest.java](../src/test/java/com/cadence/persistence/PostgreSqlGenerationBudgetTest.java) | Изолированные SQL/runtime ledger tests, overrun и проверка session-DST. |
| [src/test/java/com/cadence/persistence/PostgreSqlIdentityEvidenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlIdentityEvidenceTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Точный inventory migrations1–45. |
| [src/test/java/com/cadence/persistence/PostgreSqlSocialReviewMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlSocialReviewMigrationTest.java) | Стабильный statement timestamp для точного60s fixture invariant. |
| [src/test/java/com/cadence/persistence/PostgreSqlSocialReviewTest.java](../src/test/java/com/cadence/persistence/PostgreSqlSocialReviewTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlUrlReviewTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUrlReviewTest.java) | Явный synthetic test profile с реальным PostgreSQL; assertions сохранены. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Точный inventory TIMESTAMPTZ. |
| [src/test/java/com/cadence/support/AiGenerationContract.java](../src/test/java/com/cadence/support/AiGenerationContract.java) | Общие H2/PostgreSQL tests replay, caps, независимых actors, rollover, retry и запрещённых действий. |
| [src/test/resources/application-postgres-test.properties](../src/test/resources/application-postgres-test.properties) | PostgreSQL driver при совместных synthetic test + postgres-test profiles. |
| [src/test/resources/application-test.properties](../src/test/resources/application-test.properties) | Явная synthetic configuration тестов. |

## Финальные команды qualification

```powershell
python -X utf8 var/prompt12-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\var\prompt12-startup.ps1
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt12-verification/osv-results.json
git diff --check
```

Все перечисленные финальные gates завершились успешно. Ignored artifacts: `var/prompt12-verification/summary.json`,
source-manifest, copied Surefire/JaCoCo, current SBOM и OSV results; startup и gate logs в `var/`.
Local commit: `feat(ai): qualify scoped generation budgets and accounting` (commit содержит этот отчёт).
Push/remote CI/issue updates не выполнялись; #121/#120 остаются open; task13 не начат.
