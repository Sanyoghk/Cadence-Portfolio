# Задание13 — complete Strategy Playbook

2026-10-16. Branch mvp, starting clean HEAD8c1bee772635bfdefb16cdaf536e34ab75641f9b.
Prerequisites06/09–12 и actual contracts сверены; #107 перечитан read-only.
Scope: Task3.3, local draft P; отдельный issue number не выдуман.
Статус: **QUALIFIED LOCAL** для согласованного backend scope13. Live adapter/production activation не заявлены.

## Что сделано и как работает

Единый versioned Playbook дополняет existing pillars/cadence audience assumptions, channel
goals/mix, tone spectrum, competitive whitespace и четырьмя неделями outline. Все новые поля
проходят domain validation до publication и при edit/approval/read. Legacy v1 остаётся partial.

## Архитектурное решение

[ADR0029](adr/0029-full-playbook-contract.md) содержит утверждённую семантику и границы.
Normalized owned collections сохраняют один атомарный aggregate. Альтернатива — свободный JSON
без SQL validation — не обеспечивает reference integrity и независимую защиту истории.
Trade-off: больше таблиц/deferred checks; bounded collections и pagination ограничивают нагрузку.
Не добавлены отдельные lifecycle для каждого поля или новые dependencies.

## Runtime Flow

OWNER submit → existing scoped idempotency/budget/source capture → durable Job → worker lease
→ current approved Identity/evidence/access checks → model I/O вне DB transaction → validated
v2 result → accounting → final source/deadline/lease checks → atomic Strategy DRAFT → review/edit
expectedVersion → separate human approval → immutable revision/history.

Strategy fields не становятся executable commands. Topic/intent — plain data. Content scheduling,
startDate/DST/export и current source policy остаются задачами14–16.

## Security Review

Threat: foreign references/BOLA. Protection: actual OWNER/membership, existing source fences,
local slot/pillar/audience/channel IDs и composite SQL FK. Verification: shared lifecycle/generation
negative suites и новые malformed/reference tests.

Threat: history overwrite/forged provider attribution. Protection: approved immutable collections,
new DRAFT branch, immutable original generation receipt, separate human actor/time; no legacy backfill.
Verification: SQL bypass, expectedVersion races, legacy upgrade and read tests.

Threat: oversized/untrusted provider output. Protection: bounded typed fields, sum100, exact weeks,
slots and deterministic largest-remainder allocation, UTF-8 payload cap, no fetch/tools/commands.
Verification: malformed enums/weights/timezones/refs, unknown fields, Unicode/control characters,
aggregate overflow, incomplete provider output safe rejection.

Threat: stale worker/access/source. Protection: existing durable Jobs/budget/lease fencing and
pre-egress/completion guards reused. Verification: async H2/PostgreSQL race suites.

Authentication/CSRF/TLS unchanged: explicit Bearer-only stateless API, no cookie auth shortcut.
Secrets/prompts/provider diagnostics не добавлены в logs; новых dependencies нет.

## Тесты и обнаруженные проблемы

Initial targeted40/40 passed: domain legacy, provider contracts, build use case и real PostgreSQL
Strategy build. Первый lifecycle run выявил OpenAPI nullable declaration mismatch; исправлено
описание nullable через anyOf по существующему JSON Schema contract, validation не ослаблена.
Lifecycle161/161 прошёл после исправления schema. Дополнительный review выявил реальную ошибку
перестановки local IDs (immediate unique collision при JPA indexed update). Mapping изменён на
owned bags с explicit entryOrder + OrderBy и atomic replacement; SQL FK/unique checks сохранены.
Regression test и весь PostgreSQL lifecycle/domain затем прошли40/40.
Первый full1457:1failure/1error/3Windows skips. Два старых fixtures исправлены: полный v2 edit
в runtime provider test и корректный count created_at/updated_at с отдельной проверкой edited_at
TIMESTAMPTZ. Remediation11/11 passed. Final full завершён успешно отдельно; старый failed run не считается pass.

## Непроверенные аспекты / Production considerations

Live model/provider/latency/moderation/cost/semantic quality не qualified. Production adapter disabled.
Human review обязателен: schema validation не доказывает правдивость assumptions или marketing
эффективность. readyContentSource — structural approval marker, не authorization token.
Deployment load/backup/restore/live accounts требуют отдельной qualification.

## Что мне важно понять

Нельзя дописать отсутствующие legacy fields и назвать их AI output: это изменило бы историю.
Поэтому версия формата, исходный generation receipt и последующий human edit хранят разные факты.
Approved revision immutable; изменение создаёт новую DRAFT, которую нужно отдельно approve.

## Что стоит изучить

1. Aggregate — единая группа данных, которая сохраняется и утверждается атомарно.
2. Deferred constraint — SQL проверка итогового состояния transaction после замены collections.
3. Largest remainder — детерминированное округление channel mix до целого количества slots.
4. Provenance — история происхождения: model output, human edit и approval не смешиваются.
5. Optimistic locking — expectedVersion отвергает редактирование устаревшей revision.

## Факты, выводы, assumptions

Факты: только перечисленные executed checks и actual code inspection.
Вывод: versioned aggregate сохраняет единый Identity→Strategy смысл без fabricated legacy data.
Assumptions: real adapter и Content capture policies будут qualified отдельно.
No push/remote CI/closure/merge/deploy; #121/#120 остаются open.

## Выполненные команды

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=StrategyGenerationContractTest,StrategyTest,StrategyContentTest,BuildStrategyUseCaseTest,PostgreSqlStrategyBuildTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PlaybookContentTest,StrategyApiIntegrationTest,PostgreSqlStrategyApiTest,AiGenerationIntegrationTest,PostgreSqlAiGenerationTest,PostgreSqlStrategyMigrationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PlaybookContentTest,PostgreSqlStrategyApiTest,PostgreSqlAiGenerationTest,PostgreSqlStrategyMigrationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlStrategyApiTest#fullReplacementCanReorderLocalIdsWithoutLosingReferences' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlStrategyApiTest,PlaybookContentTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=FullEvidenceProviderIntegrationTest,PostgreSqlUtcMigrationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt13-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt13-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt12-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt13-verification/osv-results.json
```

Python10/10, renderer29/29, Linux filesystem35/35 passed без skips. OSV scan194 packages
завершён; exact inventory validation против нового SBOM PASS,0 findings. Во время прерывания
автоматический approval review не смог запустить Linux/OSV из-за лимита; эти команды были
реально выполнены после возобновления. Это не security rejection и не обойдённая проверка.

## Acceptance matrix

| Требование | Реализация / доказательство |
| --- | --- |
| Audience/channel/tone/whitespace/outline | PlaybookContent + normalized values, ADR0029; domain positive/negative tests |
| Coherent Identity source | Existing approved Identity source capture unchanged; sync/async source/access/withdrawal suites |
| generate→edit→approve→history | StrategyApiContract H2/PostgreSQL; immutable approved and new-DRAFT branch |
| Human edit versus provider output | editedBy/editedAt versus original generation format/checksum; revisedFromId lineage |
| Legacy without fabricated fields | LEGACY_PARTIAL, populated old database upgrade; explicit full upgrade; partial v2 PATCH409 |
| Bounded malformed output | Unknown/numeric/enum/local-reference/Unicode/cap tests; partial provider output no publication |
| Async version and budget | schema2 Strategy snapshot; incomplete result OUTCOME_UNKNOWN + HELD_UNKNOWN; one result/lease/race suites |
| PostgreSQL/runtime | Composite FK/unique/deferred whole-content guards; approved tampering denied; real runtime-role provider test |
| Reorder/edit race | Explicit collection order with atomic replacement; regression and concurrent edit/approval/version allocation |
| Full qualification | Final full1457/0failures/errors,3Windows skips covered Linux35/35;startup/reports/coverage/SBOM PASS |

## Security Tests / Edge Cases

- Foreign resource/role/stale membership и arbitrary local IDs запрещены.
- Wrong weights/timezone, missing/duplicate weeks, unknown fields, missing numeric values,
  duplicate slot IDs, invalid dayOffset и invalid tone spectrum отклоняются.
- Largest-remainder allocation сохраняет total slots; tie-break INSTAGRAM перед TIKTOK.
- Unicode code-point N/N+1 и aggregate overflow проверены; shared old pillar bounds сохраняются.
- Partial old provider result не превращается в complete v2; unknown cost не сбрасывается.
- Concurrent approved edits создают разные revisions; edit/approval accepts one expectedVersion.
- Late completion/source/access revocation не публикует результат; human approval отдельно.
- SQL mutations approved content/provenance/foreign references/missing goals/downgrade denied.
- Reordered IDs проходят без потери references и без ослабления SQL constraints.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Contract, актуальный план, API/recovery или qualification report. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Column-scoped runtime grants и controlled owned-collection replacement. |
| [docs/adr/0029-full-playbook-contract.md](../docs/adr/0029-full-playbook-contract.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/ai-jobs-recovery-runbook.md](../docs/ai-jobs-recovery-runbook.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/ai-workflow-issue-drafts.md](../docs/ai-workflow-issue-drafts.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/api-contract.md](../docs/api-contract.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/durable-ai-generation.md](../docs/durable-ai-generation.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/mvp-prompt-progress.md](../docs/mvp-prompt-progress.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/prompt13-playbook-report.md](../docs/prompt13-playbook-report.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/strategy-api.md](../docs/strategy-api.md) | Contract, актуальный план, API/recovery или qualification report. |
| [docs/strategy-generation.md](../docs/strategy-generation.md) | Contract, актуальный план, API/recovery или qualification report. |
| [src/main/java/com/cadence/application/ports/StrategyGenerationResult.java](../src/main/java/com/cadence/application/ports/StrategyGenerationResult.java) | Validated full v2 generation/publication через существующие source/budget/lease guards. |
| [src/main/java/com/cadence/application/results/StrategyDetails.java](../src/main/java/com/cadence/application/results/StrategyDetails.java) | Full typed API/read DTO, completeness и отдельная human attribution. |
| [src/main/java/com/cadence/application/services/AiGenerationService.java](../src/main/java/com/cadence/application/services/AiGenerationService.java) | Validated full v2 generation/publication через существующие source/budget/lease guards. |
| [src/main/java/com/cadence/application/services/AiGenerationSnapshot.java](../src/main/java/com/cadence/application/services/AiGenerationSnapshot.java) | Immutable async Strategy schema2; analysis schema1 compatibility. |
| [src/main/java/com/cadence/application/services/StrategyBuildTransactions.java](../src/main/java/com/cadence/application/services/StrategyBuildTransactions.java) | Validated full v2 generation/publication через существующие source/budget/lease guards. |
| [src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/BuildStrategyUseCase.java) | Validated full v2 generation/publication через существующие source/budget/lease guards. |
| [src/main/java/com/cadence/application/usecases/ManageStrategyUseCase.java](../src/main/java/com/cadence/application/usecases/ManageStrategyUseCase.java) | Actual human actor/membership, expectedVersion, explicit upgrade/branch. |
| [src/main/java/com/cadence/domain/PlaybookAudience.java](../src/main/java/com/cadence/domain/PlaybookAudience.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookChannel.java](../src/main/java/com/cadence/domain/PlaybookChannel.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookContent.java](../src/main/java/com/cadence/domain/PlaybookContent.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookGoal.java](../src/main/java/com/cadence/domain/PlaybookGoal.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookHypothesis.java](../src/main/java/com/cadence/domain/PlaybookHypothesis.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookPillarId.java](../src/main/java/com/cadence/domain/PlaybookPillarId.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookSlot.java](../src/main/java/com/cadence/domain/PlaybookSlot.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/PlaybookTone.java](../src/main/java/com/cadence/domain/PlaybookTone.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/domain/Strategy.java](../src/main/java/com/cadence/domain/Strategy.java) | v2 content/lifecycle/atomic replacement, edit audit и structural ready marker. |
| [src/main/java/com/cadence/domain/StrategyGenerationProvenance.java](../src/main/java/com/cadence/domain/StrategyGenerationProvenance.java) | Immutable original output format и canonical checksum. |
| [src/main/java/com/cadence/domain/StrictPlaybookValue.java](../src/main/java/com/cadence/domain/StrictPlaybookValue.java) | Typed bounded aggregate или normalized owned value и strict input fields. |
| [src/main/java/com/cadence/infrastructure/strategy/DeterministicStrategyGenerationAdapter.java](../src/main/java/com/cadence/infrastructure/strategy/DeterministicStrategyGenerationAdapter.java) | Validated full v2 generation/publication через существующие source/budget/lease guards. |
| [src/main/java/com/cadence/presentation/StrategyController.java](../src/main/java/com/cadence/presentation/StrategyController.java) | Full typed API/read DTO, completeness и отдельная human attribution. |
| [src/main/java/com/cadence/presentation/dto/StrategyResponse.java](../src/main/java/com/cadence/presentation/dto/StrategyResponse.java) | Full typed API/read DTO, completeness и отдельная human attribution. |
| [src/main/java/com/cadence/presentation/dto/UpdateStrategyRequest.java](../src/main/java/com/cadence/presentation/dto/UpdateStrategyRequest.java) | Full typed API/read DTO, completeness и отдельная human attribution. |
| [src/main/resources/db/migration/V46__complete_strategy_playbook.sql](../src/main/resources/db/migration/V46__complete_strategy_playbook.sql) | Normalized schema, legacy preservation, SQL content/history/audit/capture guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI v2 schema, nullable history fields и строгий replacement contract. |
| [src/test/java/com/cadence/domain/PlaybookContentTest.java](../src/test/java/com/cadence/domain/PlaybookContentTest.java) | Domain malformed/boundary/allocation/legacy/schema regression tests. |
| [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/support/AiGenerationContract.java](../src/test/java/com/cadence/support/AiGenerationContract.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/support/StrategyApiContract.java](../src/test/java/com/cadence/support/StrategyApiContract.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |
| [src/test/java/com/cadence/support/StrategyFixtures.java](../src/test/java/com/cadence/support/StrategyFixtures.java) | Lifecycle/concurrency/runtime/migration qualification и полные v2 fixtures. |

## Финальные qualification результаты

- `spotless:check clean verify`: BUILD SUCCESS,10:16,2026-10-16 04:27:51 BST.
  141 suites,1457 tests:1454 passed,0failures/errors,3Windows filesystem skips.
- Required PostgreSQL/MinIO/ClamAV/renderer/full-evidence report validators PASS без skips.
- Linux filesystem35/35; Python report tests10/10; renderer29/29.
- JaCoCo gates PASS: lines8451/8935 (94.58%), branches3770/5230 (72.08%).
- All554 source/build hashes unchanged during final run; test recovery launcher absent from JAR.
- Packaged JAR startup local/fake + worker/JMX: health200,4 protected anonymous routes401,
  discovery401/fixed Bearer. Только собственный startup PID остановлен после проверки.
- Fresh OSV194 Maven dependencies/0findings; independently matched against newly generated
  runtime+test SBOM. Это time-specific dependency scan, не production/SAST/container certification.
- Known warnings: Spring Jackson2 compatibility converter deprecated; Hikari reports closed
  Testcontainers connections during teardown. Они не привели к failures/errors; migration beyond
  compatibility module остаётся отдельной maintenance задачей.

```powershell
python -X utf8 var/prompt13-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\var\prompt13-startup.ps1
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt13-verification/osv-results.json
git diff --check
```

Ignored evidence: `var/prompt13-verification/summary.json`,source-manifest,copied Surefire/JaCoCo,
current SBOM/OSV; `var/prompt13-release-full.log`, startup и остальные gate logs.
Local commit: `feat(strategy): qualify complete versioned Playbook` (содержит этот отчёт).
Push/remote CI/issue updates/closure/merge/deploy не выполнялись; следующий14 не начат.

Подтверждённые факты: перечисленные executed tests, source-hash comparison, schemas/grants/code
inspection. Инженерный вывод: единый versioned aggregate и separate provenance сохраняют
coherent review/approval/history при совместной работе с существующими Jobs/budgets.
Assumptions/remaining qualification: live provider evidence/quality/privacy/operational limits,
Content source capture14–15, scheduling16 и production deployment/load/backup.
