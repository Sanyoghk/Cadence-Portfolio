# Задание18 — deterministic durable CSV export

Дата2026-10-16. Baseline `abaeeb17ed914ba1cbbff3816df341c5a1d975d1`, branch `mvp`,
working tree до начала чистое. Prerequisites14–17 проверены по HEAD/code/ADR/reports;
10–17 не реализовывались повторно. Actual GitHub #103/#107 прочитаны16 октября:
#103 прямо требует approved CSV через durable Job; #107 исторически также связывает CSV
с #98, но task18 реализует конкретный acceptance #103, не media provider #98.

Статус: **QUALIFIED LOCAL18**. Full gates завершены на финальных неизменных исходниках;
production/live readiness и remote CI этим не объявляются.

## Что было сделано / как работает

Creator явно выбирает current approved/scheduled Posts за период до28 local days. Backend
сохраняет exact revision/schedule links, actor/time, request и snapshot checksums в durable
CSV_EXPORT Job. Worker получает bounded lease, проверяет источники, строит CSV вне transaction
и атомарно сохраняет private bytes/SHA256 вместе с SUCCEEDED. Скачать может только актуальный
OWNER с действующим доступом; свежая проверка entire batch обязательна при каждом download.

Старый файл не выдаётся как актуальный после нового DRAFT, reschedule/cancel, unavailable
Strategy, archive/access/media change. Исторический SUCCEEDED и исходные receipts сохраняются.
Полный контракт: [ADR0035](adr/0035-durable-csv-export.md), [API/runbook](csv-export-api.md).

## Архитектурное решение

Новый dedicated domain/application/controller workflow использует DurableJob, reviewed
parent locks и MediaVerification, не AI cost/provider pipeline. Immutable source FKs вместо
копирования mutable current query в worker; PostgreSQL bytea вместо нового object-store
namespace.2MiB/100 retained operations bounds позволяют atomic publication без orphaned object
reconciliation. Цена решения — ограниченный объём export и удержание DB quota до отдельной
approved retention/delete policy. Download expiry24h не означает автоматическое удаление.

Idempotency scope actor+Brand+CSV_EXPORT; normalized sorted selection tokens/interval/timezone
bind request digest. Replay возвращает ту же operation, другой payload409; новый key не
обходит общую quota. Pure rendering retry safe; lease/attempt fencing и immutable first
publication запрещают stale worker overwrite. Worker внешних provider calls не выполняет.

Сохраняется ADR0031/0033 historical creative policy: withdrawal raw AI evidence не удаляет
уже human-approved creative work и не превращает export в новый AI consent. Выбранные asset,
revision, schedule и Strategy проверяются заново. CSV явно APPROVED_CREATIVE_V1 snapshot;
TEXT_ONLY не притворяется готовым media asset, DEMO_REFERENCES не actual generated media.

Проверки parent locks выполняются до загрузки managed export graph: scalar ID projection
предотвращает использование старой JPA entity после ожидания mutex. Локальный кеш entities
не является источником актуальных permissions/lifecycle.

## Security Review / threat model

Защищаем private approved content, original audit/receipts, scoped Job и private file. Callers:
current OWNER; недоверен HTTP, database user text и retained bytes. Trust boundaries: DTO/API,
actual DB authorization, private storage, renderer и final publication. Defense in depth:
strict DTO → membership/scoped lookup → exact graph/domain checks → private physical checks →
SQL FKs/checks/triggers/runtime grants. Обход HTTP validation не отменяет DB integrity.

- Threat: BOLA/forged stale membership/role/actor. Protection: actual Account role/active/current
  membership + OWNER method guards + scoped lookup; actor server-owned. Verification: anonymous,
  VIEWER/EDITOR, foreign export/Job/source, stale token and revocation tests.
- Threat: formula execution. Protection: Unicode whitespace/control/format-aware leading
  `=+-@` neutralization, leading TAB/CR/LF prefix, затем quote every cell/double quotes.
  Verification: parameterized formulas, quotes/newline/Unicode, actual mixed platform CSV.
- Threat: stale/partial/duplicate publication. Protection: immutable links/checksums, current
  latest approved/head/source checks, exact worker/attempt/lease/deadline and atomic DB commit.
  Verification: mixed invalid batch, crash, two workers, lost lease, concurrent edit/revocation,
  SQL forged success and history rewrite denied.
- Threat: private bytes/key/path exposure or archived media reuse. Protection: no bearer URL,
  server UUID filename, no-store/nosniff/CSP, no Range, physical verification outside tx with
  final recapture. Verification: checksum delivery, private media failure/retry/archive race,
  foreign download and strict input negatives.
- Threat: resource exhaustion. Protection:84 selected Posts,28 local days,2MiB artifact,
 100 retained intents/Brand,3 attempts/5min deadline,60s lease, bounded worker/physical capacity.
  Verification: quota/caps/output overflow/deadline/expiry tests. Gateway rate qualification
  remains separate; bounded quota does not mean unlimited replay traffic is free.
- Injection/secrets/dependencies: parameterized JPA/JDBC, no shell/URL fetch/model/tool call,
  no new dependency, no raw exception logging/API; controlled error codes and fixed poll warning.
  Explicit Bearer authentication/no cookie sessions retains existing CSRF contract; unchanged
  SecurityFilterChain/CORS. Existing legacy history not backfilled as approved/exported.

## Изменённые файлы

Полный перечень каждого файла с назначением: [manifest](prompt18-changed-files.md).
V1–V50 неизменны; V51 — две новые private tables, immutable item graph, actor/current source,
quota, artifact hash/lease/publication seals и SQL/runtime grants. DTO/OpenAPI/API/report
и оба plans обновляются вместе; source SHA manifest привязывает tests к фактическому исходнику.

## Runtime Flow

```text
POST explicit selections + Idempotency-Key
 → CsvExportController / strict CsvExportRequestDto
 → CsvExportUseCase OWNER/scope
 → CsvExportService → CsvExportSources actual actor/parent/source capture
 → MediaVerification: tx capture → private reads outside tx → tx recapture
 → immutable CsvExportBatch/items + CSV_EXPORT DurableJob → 202 + Job Location

worker tick → bounded candidate IDs → parent-first atomic lease claim
 → source/media preflight → CsvExportRenderer outside transaction
 → fresh source/media capture → exact lease/attempt/deadline
 → artifact/SHA256 + Job SUCCEEDED in one DB commit

GET download → actual requester authorization + expiry + exact source/media verification
 → final recapture → private no-store CSV attachment (no URL/key capability)
```

## Acceptance mapping #103 / #107

| Actual #103 requirement | Code/contract и проверка |
| --- | --- |
| DTOs/pagination | Existing bounded Posts/calendar, новый strict CsvExportRequestDto + typed CsvExportDetails; OpenAPI exact routes/DTO checks |
| OWNER + Workspace authorization | CsvExportUseCase → actual actor/membership + scalar scoped target; foreign Job/export/source и stale token tests |
| Own content edit/approval | Qualified14/17 PostLifecycleUseCase + unchanged Post regression suites; CSV не bypass approval |
| Field/all regeneration identity/provenance | Qualified15 Content jobs/immutable generation receipts; CSV links exact revision/batch/slot, human attribution не provider output |
| Timezone/optimistic scheduling | Qualified16 dual tokens/DST/atomic ledger; export exact schedule head и original/view timezone + ordered snapshot |
| Approved CSV through durable Job | Task18 submission202/Location → atomic claim/recovery/fencing → one private artifact → scoped status/download |
| Validation/lifecycle/cross-Workspace tests | CSV API/domain/PostgreSQL/runtime, entire-batch stale/archive/access/media denial, old-source full regression gates |

Эта матрица не объявляет live adapters/production hosting готовыми и не закрывает GitHub issue.
Remote #107 historical checkboxes не подменяют local code/tests; local plans отмечают actual scope,
а push/remote exact-source-SHA CI и whole-issue synchronization остаются отдельной границей.

## Тесты

Фактически до полного запуска:

- `mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile`: PASS.
- Targeted1 CSV:53tests,1failure/1error,20 PostgreSQL skipped Docker unavailable; НЕ qualification.
  Исправлены MockMvc fixture authentication/anonymous context, Docker Desktop запущен.
- Targeted2 domain/API/PostgreSQL:59/59,0skips.
- Targeted3 expanded + OpenAPI/migration:85tests,1 OpenAPI enum-order failure; схема исправлена,
  assertion не ослаблялся.
- Targeted4:88tests,1 inherited Post fixture error: host Instant.now опережал DB clock,
  SQL attribution guard правильно отказал. Fixture теперь использует DB Clock; migration не ослаблена.
- Targeted5:79/79,0failures/errors/skips; CSV PG41,HTTP23,domain11,V50→V51migration1,OpenAPI3.
  После этого добавлены2security scenarios, scheduler safe backoff и final formatting; full run
  ниже проверяет финальные исходники, прежние runs не подменяют его.

Точные targeted command selectors/логи сохранены локально в `var/prompt18-targeted*.log`.
Финальный full run: **BUILD SUCCESS**, `2026-10-16T22:25:35+01:00`, duration `14:09 min`.
159 suites / 1810 tests, 1806 executed pass,
0failures/errors,4 Windows filesystem skips; отдельно Linux37/37 без skips.
CSV PG43/HTTP24/domain11/V50→V51migration1; MediaPG49,calendar45,ContentPG25,PostPG29,
actual separate-JVM AI recovery8 — PASS. JaCoCo LINE94.51%,BRANCH71.67%, JAR/Spotless/compilation PASS.
Fresh Python10/10,renderer29/29,Linux37/37; mandatory PostgreSQL/MinIO/ClamAV/renderer/full-pipeline
report gates PASS без required integration skips. Packaged startup с CSV+AI workers enabled:
health200,23 protected anonymous probes401,discovery401/fixed Bearer; собственный PID остановлен,
port18100 освобождён. Current runtime/test SBOM194 packages, fresh OSV0 findings + completeness
verifier PASS; source651 hashes совпали до/после tests/SBOM/startup.

JAR SHA256: `09c1e16897690db5762b7e73963825b6f0664bf66c2bc8de9a7967ddb9e415c5`. Saved evidence: `var/prompt18-verification/summary.json`, Surefire XML,
JaCoCo/SBOM/OSV; `var/prompt18-full.log`, `var/prompt18-startup.log`, Linux/Python/renderer logs.
Warnings: existing deprecated JSON2/Testcontainers/S3 APIs/CycloneDX metadata warnings;
Hikari retries к уже остановленным test DB contexts. Они не скрывают failures/skips.

Additional executed commands:

```powershell
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt17-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt18-verification/osv-results.json
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt18-verification/osv-results.json
.\var\prompt18-startup.ps1
git diff --check
```

OSV freshly сканировал неизменённый dependency graph предыдущего SBOM; после full заново
создан текущий SBOM и независимо проверено совпадение всех194 packages + coverage scan records.
Это не выдача старого scan за новый. Maven online нужен plugin resolution; source/compiled
product не менялся. Required report/Python tooling само также проверено tests.


Первый полный прогон завершился22:09:57+01:00:1810tests,0failures,2errors,4Windows skips,
BUILD FAILURE (16:08). Оба errors — старые PDF/URL migration fixtures, два независимых
`clock_timestamp()` давали deadline `started_at+60s+1µs`. Аналогичный третий PDF Identity
fixture исправлен тем же стабильным `statement_timestamp()` по существующему social pattern.
Production CHECK, migrations и assertions не ослаблялись. Failed logs/reports/source manifest
сохранены в `var/prompt18-full-first.log`, `var/prompt18-first-full-reports` и
`var/prompt18-source-before-full-first.json`; это не qualification pass. CSV PG43/HTTP24/domain11/
migration1 и actual AI child-JVM8 прошли внутри этого полного запуска. После targeted regression5/5
выполнен заново `clean verify` с новым freeze; финальная qualification относится только к нему.

Точные команды основных проверок (PowerShell):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CsvDocumentTest,CsvExportApiIntegrationTest,PostgreSqlCsvExportTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CsvDocumentTest,CsvExportApiIntegrationTest,PostgreSqlCsvExportTest,OpenApiContractTest,PostgreSqlPostMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CsvDocumentTest,CsvExportApiIntegrationTest,PostgreSqlCsvExportTest,PostgreSqlCsvExportMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlPdfAnalysisMigrationTest,PostgreSqlPdfIdentityMigrationTest,PostgreSqlUrlAnalysisMigrationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt18-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt18-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt18-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
```

Python standard `csv.reader(...encoding='utf-8-sig',newline='')` независимо прочитал actual
test-produced CSV: header+4 rows,41 columns,Instagram/TikTok, multiline Unicode/formula prefix.
Файл содержит только synthetic fixtures, не реальные пользовательские данные.

Startup verification harness: initial health200/23protected401/discovery401 прошли, но immediate
socket rebind после Stop-Process дал harness error. Listener затем не обнаружен. Script исправлен
на ожидание собственного PID + отсутствие LISTEN, final packaged startup повторён полностью и
прошёл; application code/JAR не менялись. После SBOM доказано совпадение651 source hashes и541
compiled class/resource hashes с freeze и содержимым JAR. Это отделяет artifact checks от source
changes; новые полные Maven tests после изменения только ignored harness не требовались.

## Security Tests / edge cases

Concurrent same-key replay one operation; key/payload conflict; unsupported selected source
отклоняет всю batch; unknown fields/actor injection/path key; owner/editor/viewer/anonymous;
foreign resource/Brand; stale current approval/schedule; exclusive deadline/expiry; retained
quota; physical unavailable retry до DEAD; media archived during download; late render after
edit/access/parent/source revoke; crash Error до publication, expiry/reclaim и old ticket reject;
две atomic claims; lost lease while rendering → replacement worker wins exactly once; SQL
selection/history/type/success guards; actual runtime role positive publication/download.
CSV four weeks/Instagram+TikTok/order/DST view/Unicode/quotes/CRLF/formula prefix; parent/source
checks не скрывают unsupported live media/provider readiness.

## Непроверенные аспекты / production considerations

CSV crash injection реализована через `Error` после durable claim и новую DB claim/attempt;
это детерминированный тест persisted recovery, не отдельный CSV child-JVM test. Existing
AI crash/restart regression запускает8 actual separate-JVM scenarios. Production process
rollout/recovery smoke для CSV следует повторить по runbook в целевой среде.

Remote exact-SHA CI/push, hosted IAM/TLS/AppArmor/load/backups/restore, live Clerk и
spreadsheet-specific import behavior не подменяются local qualification. Real AI/media adapters
не выбраны; production demo media по ADR0034 disabled. Retention/delete policy и production
data-volume migration rollout требуют отдельной qualification. Для больших exports потребуется
согласованный bounded streaming/object-store contract; этот scope не добавляет его молча.

## Что мне важно понять / что изучить

1. Durable Job: intent переживает process crash, HTTP202 не утверждает, что файл уже готов.
2. Lease fencing: одного RUNNING мало; worker обязан предъявить current attempt/owner/deadline,
   иначе опоздавший процесс может перезаписать результат нового.
3. Snapshot/currentness: immutable источник сохраняет историю, свежая проверка решает, можно
   ли выдать её сейчас. SUCCEEDED не является вечным разрешением скачать private данные.
4. CSV injection: синтаксически корректный quoted CSV ещё может запускать formula в spreadsheet;
   нейтрализация content и CSV escaping решают разные задачи.
5. Atomic publication: private DB artifact и Job success фиксируются одним commit, поэтому
   crash не создаёт успешную операцию с отсутствующим файлом.

## Факты, выводы, assumptions

Подтверждённые факты: final full/independent gates и implementation inspection выше; current
qualified scope14–17 — исторические prerequisites, не новый full run. Инженерный вывод:
bounded DB artifact снижает publication complexity и сохраняет текущую authorization.
Assumptions/дальнейшая qualification: generic CSV manual import, production operational limits,
retention rollout и hosted environment; local tests не доказывают commercial readiness.

Local commit: `feat(content): qualify durable private CSV export` (SHA указан в финальном
сообщении и доступен через `git log -1`). Push/external writes/closure/merge/deployment
не выполнялись.19 не начато. #103/#107 остаются open до whole-issue push/exact-SHA CI;
#121/#120 provider/visual gaps остаются open по ADR0027.
