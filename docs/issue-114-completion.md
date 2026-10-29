# Issue #114 — отчёт о завершении repository scope

Дата: 2026-10-01. Baseline: `bedbf56289acfe6921eea075b703101f03656169`.

## Что было сделано

Старый unconditional sample analyzer заменён provider-neutral port. Production при
отсутствии adapter возвращает контролируемый 503; fake доступен только local/test.
Добавлены validated command/result, persisted attempt status/provenance V23 и
атомарная публикация signature с проверкой версий, archive и deadline.

Согласованный scope пользователя: **port и production fail-closed; реальный adapter
отдельным шагом**. Завершение #114 не означает завершение real AI Brand Identity,
citations, human approval или всего Task 2.3/MVP.

## Как это работает

`BrandAnalysisCommand` переносит нормализованные declared name/industry, UUID attempt
и версии Brand/Workspace. `BrandAnalysisResult` ограничивает формат, размеры и
provenance; lists копируются, raw provider JSON не проходит через application boundary.
`BrandIngestService` превращает exception/null в безопасный unavailable без message/cause.

`AnalyzeBrandUseCase` приостанавливает caller transaction (`NOT_SUPPORTED`). Отдельный
Spring bean `BrandAnalysisTransactions` создаёт новые короткие транзакции
(`REQUIRES_NEW`) для start, complete и fail. Вызов adapter не удерживает DB transaction.
Status/provenance внутренние; public API возвращает прежний BrandSignatureResponse.

## Архитектурное решение

- DIP: application зависит от `BrandAnalysisPort`, infrastructure выбирает adapter.
- Transaction proxy: отдельный bean обеспечивает реальные новые транзакции; self-call
  не прошёл бы через Spring proxy.
- Workspace → Brand locks и source version fence: edit/archive во время обработки
  приводят к SUPERSEDED, сохраняя прежнюю signature.
- Publication и SUCCEEDED receipt находятся в одной transaction: ошибка записи
  откатывает оба изменения. Даже одинаковая signature увеличивает version, поскольку
  Hibernate может пропустить value-identical UPDATE.
- V23 partial unique index допускает один RUNNING на бренд; terminal/source rows
  защищены trigger и отдельными runtime permissions. Deadline — 60 секунд;
  PostgreSQL actual clock независимо запрещает backdated позднюю публикацию.
- Abandoned RUNNING восстанавливается лениво следующим запросом; UUID старой попытки
  не может завершить новую попытку.
- Output checksum использует стандартный SHA-256 и length-prefixed encoding. Это
  однозначное представление content, не подпись provider и не гарантия качества AI.

Alternatives: одна длинная transaction удерживала бы connection во время I/O;
один optimistic lock не ограничивал бы одновременные attempts и не сохранял failures.
Durable Job с retry/cost budgets нужен будущему real adapter; сейчас сохранён
совместимый синхронный HTTP contract. Цена выбранного решения — ledger и три фазы.
Новые dependencies не добавлены. [ADR-0009](adr/0009-brand-analysis-port-and-fenced-publication.md).

## Security Review

Threat: BOLA, forged workspace scope и reserved-role escalation.
Protection: stored OWNER/membership на HTTP, use-case и transaction method boundaries;
scoped lookup перед side effects; deny by default.
Verification: существующие SecurityIntegrationTest/UseCaseMethodSecurityTest,
UseCaseAuthorizationTest и PostgreSqlBrandAnalysisTest foreign scope/brand denial.

Threat: fake analyzer случайно активируется в production.
Protection: explicit local/test environment guard, запрет prod и mixed profiles,
unknown adapter startup rejection; disabled default вне local/test.
Verification: BrandAnalysisConfigurationTest и реальный disabled HTTP 503 test.

Threat: недоверенный provider output, malformed palette, excessive output, mutable alias.
Protection: immutable bounded validated result, allowlisted identifiers/warnings,
новая domain signature; raw payload/credentials/diagnostics не часть port.
Verification: BrandAnalysisContractTest malformed/oversized/aliasing/null cases.
Текст — plain text, не HTML; будущий frontend обязан безопасно выводить его. Model
prompt injection/semantic correctness ещё не квалифицированы: model adapter отсутствует.

Threat: late/concurrent result затирает пользовательские данные; ledger forgery.
Protection: locks, source versions, UUID binding, deadline, atomic publication/receipt,
unique RUNNING, immutable terminal rows и runtime column privileges.
Verification: реальные PostgreSQL concurrent start, source edit/archive, deadline,
recovery, identical publication, DB-clock rollback и SQL negative tests.

Threat: secret/internal-provider disclosure через ошибки.
Protection: фиксированная unavailable exception без cause; существующий safe API
error factory; в ledger только validated provenance/source hash.
Verification: exception с synthetic private-secret не раскрывается; HTTP 503 проходит
OpenAPI contract assertion. Это synthetic fixture, не реальные credentials.

Threat: resource exhaustion и expensive external calls.
Protection: input/output bounds, один RUNNING, короткие transactions, существующие
DB lock/statement timeouts; сейчас external model calls отсутствуют.
Verification: concurrent request не вызывает второй adapter; реальный runtime lock
wait deadline проверен FullEvidenceProviderIntegrationTest. Global request rate/cost
budgets и ledger retention policy остаются production activation gates (#87/real adapter).

SQL injection: новые SQL tests/JDBC используют параметры, migration statements статичны.
Command injection/SSRF: analysis не запускает shell и не делает URL fetch.
CSRF: текущий API использует Bearer authentication, не session cookies; SecurityFilterChain
не менялась. CORS/auth policy не расширялись. В logs не добавлялись provider messages,
tokens или content. Security audit с actor identity остаётся release scope #87;
attempt receipt не объявляется полноценным user audit log.

## Изменённые файлы

[Полный manifest каждого файла с назначением изменения](issue-114-files.md).
Ключевые components: BrandAnalysisPort/Command/Result, BrandIngestService,
AnalyzeBrandUseCase, BrandAnalysisTransactions, BrandAnalysisAttempt, configuration,
V23, runtime-permissions, HTTP error/OpenAPI, PostgreSQL/runtime/security tests и CI gate.

## Runtime Flow

```text
Bearer → stored OWNER + workspace membership → CadencePanelController
  → AnalyzeBrandUseCase (NOT_SUPPORTED)
  → start (REQUIRES_NEW): scoped Workspace/Brand locks → RUNNING + source snapshot
  → BrandIngestService → BrandAnalysisPort (без DB transaction)
  → validated result
  → complete (REQUIRES_NEW): source/archive/deadline recheck
  → BrandSignature + SUCCEEDED provenance → PostgreSQL atomic commit
```

Failure → fail transaction → FAILED, прежняя signature сохранена.
Stale result → SUPERSEDED transaction commit → controlled conflict.
Disabled adapter → FAILED → documented HTTP 503 `brand_analysis_unavailable`.

## Тесты — реально выполненные команды

Java 21.0.11, Docker Desktop Linux engine.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply clean verify
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_postgres_report.py
python scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation
```

- Final Maven: **645 tests, 0 failures/errors, 3 Windows-only filesystem skips**;
  compilation, JAR, Spotless и JaCoCo check passed. **642 executed cases passed**.
- Полная Linux filesystem suite: **35/35 passed**, no skips/aborts/failures, включая
  три Windows-inaccessible symlink cases. Это отдельный Linux filesystem run.
- Scripts Python **10/10**, renderer Python **29/29**.
- Required PostgreSQL/MinIO/ClamAV/renderer/full-pipeline report gates passed без skips.
- PostgreSqlBrandAnalysisTest **13/13**, runtime full-provider suite **8/8**,
  UseCaseMethodSecurityTest **10/10**, disabled HTTP **1/1** в final full run.
- Compose: сохранённая demo БД **V22 → V23**, runtime restrictions применены;
  application/gateway healthy. Реальный HTTP health **200** (text/plain), Workspace
  и OpenAPI без Bearer **401**, `authentication_required`, application/problem+json.
  `max(version)` Flyway в сохранённой БД — **23**.

Первый full run: 643 tests, 2 errors из-за старых fixtures (uncommitted caller
transaction и ожидание sample в postgres-test). Исправлены fixtures, без удаления
или ослабления security assertions. Focused rerun 18/18 passed; final full gate выше.
Test Clock приведён к microsecond precision PostgreSQL: это устранило искусственный
rounding mismatch; actual DB-clock negative regression отдельно подтверждает fence.

Warnings: ожидаемые SQL constraint violations в negative tests, JDK class-sharing/
Commons Logging notices, Hikari cached-context warnings после остановки test containers
и cleanup deferral в failure fixtures. Final build success; warnings не скрыты.

Ignored logs: `var/issue114-final-verify.log`, `var/issue114-python-scripts.log`,
`var/issue114-renderer-python.log`, `var/issue114-linux-filesystem.log`,
`var/issue114-compose-startup.log`, `var/issue114-compose-smoke.log`.

## Security Tests

Запрещены: fake в prod/mixed profiles, unknown adapter; foreign/malformed scope,
foreign brand и reserved roles; malformed/excessive output/null/secret diagnostics;
second concurrent analysis; stale/expired result; SQL source/terminal rewrite,
receipt DELETE, duplicate RUNNING, terminal completion без timestamp; backdated
publication после реального DB deadline. Runtime privileges проверены настоящим
неадминистративным DB user, не только Mockito.

## Edge Cases

Identical signatures всё равно увеличивают version; provider failure/null сохраняют
прежнюю signature; edit/archive во время обработки сохраняют SUPERSEDED receipt;
expired RUNNING заменяется новой попыткой, late old callback не публикует результат;
ошибка success receipt откатывает уже выполненный signature UPDATE.
Checksum различает списки с неоднозначным `List.toString()` representation.

## Непроверенные аспекты

Нет real AI adapter/provider credentials: real model output, privacy/cost/latency,
rate budgets, prompt injection defenses и live model retries не проверены. Это
согласованный deferred scope, а не физически недоступная часть готового adapter.
Нет внешнего production deployment: TLS/IAM, реальные Clerk/JWKS/membership revocation,
backup/restore, load и privileged archival/retention остаются deployment/release gates.
DB outage во время failure recording может оставить RUNNING до recovery;
publication deadline не прерывает arbitrary adapter code. Remote CI qualification подтверждена ниже для конкретного source/test SHA.

## Что мне важно понять

Validation защищает границу данных, version fence — момент публикации, transaction —
атомарность записи. Ни один механизм не заменяет остальные. Держать DB transaction
открытой во время provider I/O увеличивает lock time и расход connection pool.
Правильный fake isolation не разрешает выдавать demo результат как production AI.

## Что мне стоит изучить

1. **Граница зависимости (Port and Adapter)**: interface отделяет application от provider.
2. **Короткая транзакция (Transaction boundary)**: I/O и atomic persistence — разные фазы.
3. **Проверка актуальности (Version fencing)**: версия источника запрещает stale overwrite.
4. **Ограничения БД (Database constraints)**: unique/check/trigger защищают invariants при
   ошибке application layer.
5. **Безопасный отказ (Fail-closed)**: отсутствие provider не превращается в sample success.

## Production considerations

До реального включения нужны отдельный bounded production adapter, durable analysis
Jobs/retry/cost budgets, human approval/citations workflow, gateway rate controls,
actor audit (#87), scoped provider credentials/TLS и tested operational retention/
backup/restore policy. Runtime остаётся least-privilege; migrations — отдельным user.
Default local profile нельзя считать production deployment configuration.

**Подтверждённые факты:** перечисленные local gates, V23 startup, HTTP statuses и inspection.
**Инженерные выводы:** port и короткие fenced transactions позволяют подключить adapter
без удержания DB connection и без stale signature overwrite при проверенных сценариях.
**Assumptions/дополнительная проверка:** external infrastructure, real model behavior,
operational budgets/retention, audit и deployment configuration. Production readiness
всего AI workflow не заявляется.

## Commits / publication

- `d089400`: validated port и production fail-closed configuration (37 focused tests).
- `1ef9d93`: persisted lifecycle/provenance и atomic fenced publication.
- `e2d44bf`: runtime privilege/transaction fixtures и atomic DB-deadline rollback qualification.
- Documentation commit фиксирует этот отчёт, ADR, manifest и scope в планах.

Push выполнен в `mvp`; #114 closed completed, #97 получил `status: next`.
Master #107 сохраняет Task2.3 partial: real generation/citations/approval отложены.

## Remote CI qualification

[CI 36902726234](https://github.com/Sanyoghk/Cadence/actions/runs/36902726234)
для **`5b3118e048a07f0e5a73d011423610268103be3e`** завершился success:
`verify` и `dependency-security` passed.

- Ubuntu full `sh ./mvnw --batch-mode --no-transfer-progress clean verify`:
  **645 Java tests, 0 failures/errors/skips**, compilation/Spotless/JaCoCo passed.
- Scripts Python **10/10**, renderer Python **29/29**.
- Required PostgreSQL/MinIO/ClamAV/renderer/full-provider report gates passed;
  PostgreSqlBrandAnalysisTest **13/13**, no skips.
- Enforcing scoped AppArmor, kernel namespace preflight и реальный Chromium sandbox
  passed; isolated screenshot **1280×720** verified.
- OSV: complete inventory **165 Maven dependencies**, no findings at scan time.
- Collect bounded kernel denials step skipped штатно: он запускается при preflight
  failure; preflight passed. Это не skip security tests.
- Actions logs предупреждают о Node20 deprecation/forced Node24 для существующих
  pinned actions; upgrade action pins остаётся отдельной maintenance задачей #8.

Последующий commit меняет только documentation и записывает эту qualification;
product source/tests после полного gate неизменны. Новый docs-only SHA не называется
повторно квалифицированным этим run. Deployment/model-provider gates остаются отдельно.
