# Задание19 — bounded manual/CSV metrics import

Дата2026-10-16. Baseline `9d3a60aab7c3b4c8c1f8684bcf2b2d7dfb25c4a8`, branch `mvp`,
working tree до начала чистое. Prerequisites14–18 проверены по HEAD/code/ADR/reports;
ранее delivered10–18 повторно не реализовывались. Actual GitHub #99/#107 прочитаны16 октября.
#99 `[Task 6.3a] Implement analytics synchronization flow` включает более широкий live-provider
scope, чем разрешённое задание19; #107 Task6.1 и оба плана начинают с manual/CSV.

Статус: **QUALIFIED LOCAL19**. Все required local gates завершены на неизменных исходниках.
Это не объявление live synchronization или production readiness и не closure whole #99.

## Что было сделано / как работает

Creator отправляет manual JSON или CSV с actual scoped Post UUID/platform, source observation
time, interval и nullable counters. Backend проверяет bounded syntax/mapping/access и сохраняет
exact raw human assertion, checksums, actor/DB time и durable METRICS_IMPORT Job. Worker
повторно разбирает immutable raw bytes вне transactions и публикует batch normalized observations
с correction lineage/result links вместе с SUCCEEDED в одной transaction. Данные доступны
через bounded Post query и scoped Job/import status. Human assertions не становятся verified
platform/provider data; missing не превращается в zero. Legacy PostMetric сохранён отдельно.

Полный contract: [ADR0036](adr/0036-manual-csv-metric-import.md), [API/runbook](metrics-import-api.md).

## Архитектурное решение

Additive V52: raw `MetricImport`, normalized immutable `MetricObservation` и ordered receipt
links вместо изменения mutable legacy PostMetric, связанного с SocialPost. Новая mapping
использует stable ContentPost, поэтому creative edit/Strategy history не переписывают измерения.
Отдельный parser/application service/usecase/controller/results/repositories сохраняют layers.
Reviewed parent locking из AiGenerationSources переиспользуется как технический primitive;
никакого AI/provider I/O metrics service не вызывает. Generic Job queue исключает новый type.

Unified async path для manual и CSV даёт один publication/recovery/idempotency contract ценой
queue latency. Existing DurableJob lease/retry/fencing переиспользованы; processing чистое,
поэтому повтор безопасен и не имеет uncertain provider side effects. Bytea raw storage в DB
при128KiB/1000retained imports позволяет coherent atomic receipts, без нового storage/parser
dependency и orphan cleanup. Quotas/short parent locks ограничивают нагрузку; horizontal workers
сериализуют публикацию одного Brand, разные Brands независимы. Parsing2slots/instance,
worker1slot/instance; DB crash/restart recoverability через persisted state/expired lease.

Natural key Post/platform+observedAt внутри tenant/Brand. Exact normalized repeat с новым receipt
ссылается на original observation. Changed values требуют exact current `correctionOf`, новая
revision immutable. Request replay actor/Brand/METRICS_IMPORT+key binds origin/exact raw digest;
whitespace difference с тем же key409. Это deliberately exact contract, а не semantic CSV replay.
Interval counts не cumulative snapshots; analytics aggregation/formulas в20 должны отдельно
обрабатывать overlap, source quality, samples и null. Здесь rates не добавлены.

## Security Review

Threat: BOLA/foreign Post/import/Job, поддельный role/tenant/actor, stale membership token.
Protection: dedicated input schema, OWNER at controller/usecase, actual persisted actor/membership,
scoped Brand/Post lookup, current ACTIVE parents; captured worker actor перечитывается.
Verification: HTTP auth/role/foreign negatives, cross-Brand Post mapping, PostgreSQL revocation
during parsing и runtime role positive/forbidden writes.

Threat: formula/URL execution, numeric coercion/overflow, malicious encoding/parser exhaustion.
Protection: no evaluation/network/shell,128KiB/200rows/11columns/128chars, strict UTF-8/header,
JSON nesting4/string128/number16, duplicate/unknown fields rejected,2parser slots fail-closed;
nullable Long0..10^12, explicit source interval and UTC microsecond bounds.
Verification: unit parser vectors и PostgreSQL HTTP malformed headers/numbers/timestamps/UTF-8,
oversize payload, maxLong count round-trip, bound/slot tests. SQL parameterized; no dependency.

Threat: concurrent replay/correction, partial batch publication, lease loss/revocation/archive.
Protection: Account→Workspace→Brand→sorted Post→import→Job, current rechecks, max3attempts/
60s lease/300s deadline, immutable history/checksums, unique keys, deferred publication seals,
restricted runtime grants; result links/observations/Job commit atomically.
Verification: PostgreSQL two-workers/concurrent replay/import dedup/correction winner, late
worker after reclaim, latch-controlled revocation/archive/lease loss, rollback after earlier row,
SQL observation without publication, forged Job success/history updates and runtime grants.

Threat: private business metrics/raw bytes/audit or infrastructure leak.
Protection: raw bytes never in response/logs, DTO only/no-store, fixed safe error codes/log message,
no filenames/URLs/storage keys, separate source/ingestion time; current access on all reads.
Verification: API asserts rawInput absent, typed OpenAPI, inspection of error/logging paths.
Authentication/security filter unchanged: explicit Bearer headers, stateless; no cookie CSRF
surface added. Existing CORS policy unchanged. No secrets/new dependency/provider selection.

## Изменённые файлы

Полный перечень с назначениями — [manifest19](prompt19-changed-files.md).
Старые Flyway checksums и legacy domain/API behavior сохранены. Existing OpenAPI schemas/routes
semantic-equal HEAD; только `/jobs` typed response union расширен и добавлены metrics routes.

## Runtime Flow

```text
Bearer authentication → MetricImportController → ImportMetricsUseCase
 → actual Account/Workspace/Brand authorization → bounded parser вне DB tx
 → fresh authorization/sorted Post mapping → immutable raw receipt + PENDING Job
 → worker atomic claim/attempt → captured raw checksum → parser вне DB tx
 → current access/parents/Post + lease/attempt/deadline
 → dedup or append correction → observations + result links + SUCCEEDED (один commit)
 → scoped status / bounded current or historical observation read
```

## Тесты

Фактические предварительные runs:

```text
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=MetricRowTest,MetricImportParserTest,MetricImportApiIntegrationTest,PostgreSqlMetricImportTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=MetricRowTest,MetricImportParserTest,MetricImportApiIntegrationTest,PostgreSqlMetricImportTest,PostgreSqlMetricImportMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,PostgreSqlCsvExportMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=MetricRowTest,MetricImportParserTest,MetricImportApiIntegrationTest,PostgreSqlMetricImportTest,OpenApiContractTest' test
```

Compilation PASS. Предварительные runs72/72,91/91,84/84; subsequent full включает последние
добавленные boundary/concurrent-correction/wire-malformed tests. Нет ослабления assertions ради
green: старый CSV migration test явно targets V51, новый проверяет V51→V52; inventory52/UTC60.

Финальные фактически выполненные команды:

```text
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt19-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt19-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt19-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L target/bom.cdx.json --all-packages --format=json --output-file=var/prompt19-verification/osv-results.json
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt19-verification/osv-results.json
.\var\prompt19-startup.ps1
python -B var/prompt19-integrity.py
python -B var/prompt19-doc-check.py
git diff --check
```

Full BUILD SUCCESS `2026-10-16T23:12:37+01:00`,15:32min:

| Проверка | Фактический результат |
| --- | --- |
| Full Maven |164suites/1897tests;1893executed PASS,0failures/errors,4Windows filesystem skips|
| Linux filesystem |37/37,0skips, закрывает четыре Windows symlink skips|
| Metrics H2 API suite |32/32, включает inherited Post lifecycle regression|
| Metrics PostgreSQL suite |45/45, включает inherited Post lifecycle regression|
| Metrics parser/domain/migration |6/6 +3/3 +1/1|
| Calendar/CSV/media PostgreSQL regressions |45/45 +43/43 +49/49|
| Content PostgreSQL / AI child-JVM restart |25/25 +8/8|
| Required PostgreSQL/storage/MinIO/ClamAV/renderer reports |PASS, без пропуска required integration tests|
| Python report tests / rebuilt renderer tests |10/10 +29/29|
| Spotless / JaCoCo |PASS; LINE94.44%, BRANCH71.58%|
| Current SBOM / fresh OSV |194Maven dependencies,0findings; completeness gate PASS|
| Packaged startup |health200;26anonymous protected operations401; discovery401/fixed Bearer; own PID stopped/port released|

Scanner2.5.1 SHA256 проверен до запуска:
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
671 source/build hashes неизменны на протяжении full/SBOM/startup.560 compiled resources
сохранились после SBOM и совпадают с packaged JAR; test recovery launcher не packaged.
JAR SHA256 `35b722c79fa5f93a016654a9c44a6069d0749b683da484fc509f02354ccb7c0e`.
Evidence сохранены локально в ignored `var/prompt19-verification/`, logs `var/prompt19-*.log`;
helper scripts там же — local evidence, основные reusable gates находятся в `scripts/`.

Warnings: существующие Jackson2/S3/Hibernate deprecated APIs, JVM instrumentation warning,
Hikari closed connections после остановки Testcontainers contexts и expected negative sandbox/
SQL rejection logs. Они не подменяют actual report results. Ошибка вспомогательной OpenAPI
comparison была false positive из-за Windows locale decoding `git show`; повторное сравнение
explicit UTF-8 подтвердило semantic equality старых schemas/routes. Product source не менялся
во время full и никакой failing product test не ослаблялся ради green.

## Security Tests / Edge Cases

Проверяются auth/role/foreign resources, client audit fields, wrong platform/encoding/header,
fractions/string coercions/negative/overflow/formula/URL, source future/invalid/microsecond
precision, raw/row/column/field/query caps, unknown vs0,10^12 round-trip, out-of-order history,
duplicate raw/replay key collision, dedup independent imports, current/stale/foreign corrections,
atomic rollback, multiple worker claims/redelivery, expired leases/three exhausted attempts,
simulated process loss Error before publication, late worker during parser, access/archive
races, runtime least privilege и exact legacy preservation. Семантика source time и ingestion
time не смешивается. Explicit deadline boundary проверяется domain publish; PostgreSQL real
clock/fencing проверяется expired-lease и lost-worker races.

## Непроверенные аспекты / production considerations

Нет live platform synchronization/credentials/provider mappings/cursors/rate responses или
partial provider failure recovery: broader #99 остаётся OPEN. Port boundary для будущего
qualified AnalyticsSyncService/SyncAnalyticsUseCase записан в ADR, не выдан за working adapter.
Human counters не доказывают actual reach/conversions/publication. Provider/AI/media не выбраны.
Реальный отдельный metrics worker process kill/restart не заявлен: deterministic Error/lease
reclaim и independent DB workers покрывают этот путь; existing AI child-JVM restart suite —
regression другого workflow, не доказательство live metrics provider recovery.

До коммерческого activation: API gateway rate/concurrency/load qualification, worker sizing,
DB capacity/alerts/backups/restore, approved legal retention/delete policy и live auth/environment
qualification. Retained raw quotas не освобождаются автоматически. У worker explicit defaultfalse;
его надо осознанно включить. Local full gates не являются remote CI/production readiness.

## Что мне важно понять / что изучить

1. **Происхождение данных (data provenance):** raw receipt показывает, что и кто прислал;
normalized values удобны для query, но не повышают достоверность assertion до provider truth.
2. **Идемпотентность (idempotency):** повтор того же scoped request возвращает тот же Job;
отдельная dedup policy предотвращает duplicate observations даже с другим import key.
3. **Неизменяемая история (append-only history):** correction создаёт новую запись с exact
previousId; старые counts/audit доступны для объяснения предыдущих выводов.
4. **Ограждение worker (lease/attempt fencing):** право работать ограничено attempt и сроком;
worker после lease loss не получает право публикации только потому, что закончил parsing.
5. **Атомарность (atomicity):** либо весь batch с Job success commit, либо ничего; без этого
frontend/analytics могли бы принять частичные metrics за полный import.

## Git / issues / факты и assumptions

Законченный slice сохраняется local commit `feat(analytics): qualify bounded metrics imports`;
его SHA можно получить через `git log -1 --format=%H -- docs/prompt19-metrics-import-report.md`.
Push/remote issue writes/closure/merge/deploy
не выполнялись. #99 не закрывается по manual/CSV, #121/#120 provider scopes остаются OPEN.
Task20+ не реализованы. Подтверждённые факты — реальные commands/reports/hash checks выше.
Инженерный вывод: additive immutable workflow сохраняет legacy и обеспечивает coherent replay/
correction/recovery. Assumptions: human data честно labeled unverified; business meaning interval
counts соблюдает caller; реальные platform mappings и операционный rollout требуют отдельной проверки.
