# Issue #97 — Strategy generation application flow

Дата: 2026-10-04. Baseline: `e92f1ef`. Product source: `1a1759a`.

## Что было сделано

Реализован application flow: OWNER вызывает generation для своего ACTIVE Brand с
валидной signature, получает новый сохранённый DRAFT с pillars/cadence и immutable
provenance. Старые версии сохраняются. Real AI не подключён по согласованному scope:
production fail-closed; отдельная planned задача [#120](https://github.com/Sanyoghk/Cadence/issues/120)
теперь явно включает Brand/Strategy adapters. REST/approval workflow — #102.

## Как это работает

BuildStrategyUseCase имеет method authorization и NOT_SUPPORTED transaction boundary.
StrategyBuildTransactions.snapshot/complete — разные Spring beans/proxied methods с
REQUIRES_NEW. Spring proxy применяет transaction/security interceptors при вызове
между beans; self-invocation внутри одного bean этого не гарантировала бы.
Snapshot удерживает Workspace→Brand locks лишь во время source capture. Generator
получает immutable record, не Entity; provider I/O не занимает DB transaction.
Complete повторно проверяет ACTIVE, versions, exact normalized name/industry/signature
и publication deadline; Brand lock сериализует next-version allocation.
Result всегда новый DRAFT, provider status в contract отсутствует.

## Архитектурное решение

Port isolates infrastructure от application/domain. Single long transaction с external
I/O удерживала бы DB connections/locks весь provider timeout. Blind overwrite терял бы
history и принимал stale output. Chosen short transaction phases сохраняют history и
отвергают устаревший output, но требуют recheck и могут безопасно отказать после
изменения source. Default READ_COMMITTED квалифицирован concurrent test; более строгая
isolation может вызвать serialization conflict. Автоматического provider retry нет.

V24 защищает nullable legacy / complete generated provenance, unique generation ID,
source/version/deadline, approved-content immutability и allocation integrity. Deferred
constraint triggers выполняются при commit после всех JPA collection writes.
Runtime role не владеет schema и не может UPDATE provenance/identity или DELETE root.
Нет новых dependencies. [ADR-0010](adr/0010-strategy-generation-and-immutable-history.md).

Обнаружена и исправлена timezone ошибка: до исправления PostgreSQL assertion для
`LocalTime.NOON` получил `11:00:00`; expected `12:00:00` сохранён. LOCAL_TIME mapping
использует direct Java Time JDBC binding для wall times; UTC audit Instant mapping
остаётся прежним. Механизм подтверждён [Hibernate MappingSettings](https://docs.jboss.org/hibernate/orm/6.6/javadocs/org/hibernate/cfg/MappingSettings.html).
Production данных нет; исторические demo times не сдвигались на угаданный timezone.

## Security Review

Защищаем signature tenant, Strategy history и DB/provider capacity. Caller — OWNER.
Trust boundaries: authenticated scope, persisted source, untrusted provider output,
runtime SQL. Provider fixtures trusted только как тестовые механизмы, output contract
проверяется независимо.

- Threat: BOLA/forged workspace scope, reserved-role bypass. Protection: method guards
  на use case и helper transactions + scoped Brand lookup. Verification: foreign Brand,
  forged scope, EDITOR/VIEWER/unauthenticated и direct helper denial tests.
- Threat: stale/archive race. Protection: Workspace→Brand lock order, repeated ACTIVE/
  version/name/industry/signature equality. Verification: edits/archive during provider,
  raw signature edit before completion, concurrent two requests. Runtime writers должны
  соблюдать parent locking protocol; arbitrary malicious DBA/SQL не считается caller API.
- Threat: late output/overwritten history. Protection: application deadline + independent
  PostgreSQL clock guard, monotonically allocated version, immutable unique provenance.
  Verification: expired Java deadline, backdated DB deadline, rewrite/delete SQL denials.
- Threat: malformed output/resource abuse/provider-controlled approval. Protection:
  bounded immutable result, no status, DB constraints/deferred weight/time checks.
  Verification: invalid text/list/weights/duplicate names/times/timezone/nanoseconds,
  partial invalid DB edit and maximum version denial before provider.
- Threat: approved-content tampering through object aliases or SQL. Protection: defensive
  copies, domain state guards, DB root/child triggers, restricted runtime grants.
  Verification: domain alias regression, APPROVED/LOCKED edit/downgrade denial, actual
  non-owner DB role successful draft save and forbidden rewrite tests.
- Threat: sensitive provider diagnostics. Protection: fixed exception without cause;
  commands/results omit credentials/raw payload. Verification: secret-looking fixture
  exception cannot escape use case; null output creates no draft.
- Injection/SSRF/CSRF/CORS: no new HTTP/external transport/shell/template execution;
  SQL application queries parameterized, test SQL constants not external input. Provider
  URLs/transport/cost bounds remain #120; API/escaping/rate limits remain #102/#87.
- Supply chain/logging: no dependency added, no secrets committed, no provider payload
  logging. Full dependency/security CI result recorded separately after push.

## Изменённые файлы

[Полный manifest каждого изменённого файла](issue-97-files.md). Main components:
StrategyGenerationCommand/Result/Service, BuildStrategyUseCase,
StrategyBuildTransactions, BuiltStrategy, StrategyGenerationProvenance,
Strategy/PublishingCadence, V24, runtime grants, CI report gates и tests/docs.

## Runtime Flow

```text
Authenticated OWNER + workspace scope
 → BuildStrategyUseCase.execute
 → StrategyBuildTransactions.snapshot [new short transaction; Workspace→Brand locks]
 → immutable command
 → StrategyGenerationService.generate [no database transaction]
 → validated immutable result / safe failure
 → StrategyBuildTransactions.complete [new short transaction; recheck; next version]
 → StrategyRepository.saveAndFlush
 → PostgreSQL V24 guards + deferred constraints at commit
 → BuiltStrategy DTO (DRAFT)
```

## Тесты

Первый targeted run при выключенном Docker: 30 tests, 9 passed и 21 skipped; это
не integration pass. Docker Desktop был запущен, тесты повторены. Первый настоящий
PostgreSQL run воспроизвёл 1 publishing-time assertion failure. Mapping исправлен.
При добавлении reload assertion исправлен test-side lazy read: collection читается
в TransactionTemplate, production fetch strategy не ослаблялась.

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test '-Dtest=StrategyGenerationContractTest,StrategyGenerationConfigurationTest,StrategyTest'
.\mvnw.cmd --batch-mode --no-transfer-progress spotless:apply test '-Dtest=BuildStrategyUseCaseTest,StrategyTest,PostgreSqlStrategyBuildTest,PostgreSqlStrategyMigrationTest,PostgreSqlPersistenceTest'
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
python -X utf8 -m unittest discover -s scripts -p 'test_*.py'
python -X utf8 scripts/verify_postgres_report.py
python -X utf8 scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-renderer-preflight -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Port/configuration/domain slice18/18 passed; final targeted flow/migration31/31 passed.
Actual runtime provider suite9/9 passed. Scripts Python10/10; renderer Python29/29;
Linux filesystem35/35, no failures/skips. Final full/Compose qualification appended below.
Ignored evidence logs: var/issue97-port-final.log, issue97-flow-qualified.log,
issue97-full-verify.log, issue97-python-scripts.log, issue97-renderer-python.log,
issue97-linux-filesystem.log. Java21, Docker Desktop Linux engine29.8.1.

## Security Tests

Unauthorized scope/roles/helper calls, malformed/oversized output, null/failing provider,
source changes and archives, expired deadlines, duplicate allocation/empty times,
immutable source/version/history, approved collection mutation/downgrade и runtime
privilege denials перечислены выше с protection/verification. HTTP negative scenarios
для Strategy не заявляются: новых endpoints в #97 нет.

## Edge Cases

Concurrent valid requests → distinct drafts1/2; source changes → no partial save;
provider failure → previous versions retained; max int version → reject before provider;
legacy V23 rows → preserve DRAFT/APPROVED, pillars/times, provenance NULL; collection
updates → verify final transaction state; wall time → SQL exact value and ORM reload.
No claim of generation idempotency: another execute creates another draft version.

## Непроверенные аспекты

Real model adapter, live provider/privacy/cost/cancellation, production TLS/IAM/network,
live Clerk/OAuth, production backups/restore/load/E2E не проверены. Причина: provider
не выбран/credentials отсутствуют, внешняя инфраструктура не настроена. Offline fixtures
проверяют contracts, не качество AI или коммерческий deployment. Перед activation
нужны #120 и deployment qualification, real failure/timeout/load validation.

## Что мне важно понять

Immutable record не позволяет provider менять захваченный source через Entity alias.
Lock на source нужен и при capture, и при completion: отсутствие transaction во время
I/O требует повторной проверки. DRAFT отделяет machine suggestion от human approval.
Database checks защищают integrity при ошибке application; scoped authorization
по-прежнему нужна, поскольку DB runtime role не знает end-user identity.

## Что мне стоит изучить

1. Port/adapter: interface изолирует business flow от model vendor; реальный transport #120.
2. Состояние гонки (race condition): source может измениться во время I/O; recheck/locks
   предотвращают публикацию stale result.
3. Отложенные constraints (deferred constraints): проверка complete коллекций при commit.
4. Неизменяемый snapshot (immutable snapshot): копии исключают изменение approved values
   через ранее полученную ссылку.
5. Wall time vs Instant: LocalTime — часы в указанной cadence timezone, Instant — UTC момент.

## Production considerations

Explicit prod profile; generation remains disabled до bounded real adapter #120.
Publication deadline не отменяет зависший I/O: cancellation обязан обеспечить adapter.
Approved/versioned Brand Identity/citations/jobs/budgets остаются #107; current source —
валидная current signature. API CRUD/approval/revision lineage/audit и safe errors — #102.
No new endpoint abuse surface until API delivered; future generation API требует quotas,
rate limits и model cost controls. V24 deployment: backup/stopped old writes, Flyway
migration identity и repeated runtime grants; old-runtime rollback не квалифицирован.

## Факты, выводы и assumptions

Факты: указанные targeted/runtime/Python/Linux tests реально запускались; raw SQL
wall-time failure воспроизведён и fixed mapping проверен; migrations/domain/guards
изучены. Full/remote/deployment facts только в qualification sections ниже.
Вывод: architecture защищает текущий application scope и сохраняет history; это не
сертификация usable production AI workflow. Assumptions: runtime database isolation
READ_COMMITTED, trusted migration operator, parent locking protocol для runtime writes,
explicit prod profile и будущая отдельная live qualification. Commercial readiness
остаётся не подтверждена.


## Full local qualification

Source `1a1759a`: `clean verify` BUILD SUCCESS, **678 tests, failures0/errors0/skipped3**,
86 XML suites; **675 executed cases passed**. Compilation, JAR, Spotless и JaCoCo gate
passed. JaCoCo LINE4240 covered/293 missed (~93.54%); BRANCH2046 covered/751 missed.
Three skips — Windows symlink filesystem restrictions; all35 filesystem tests passed
separately on Linux, no failures/skips. Mandatory PostgreSQL and real provider report
gates passed with no missing/failed/skipped required suite. Full provider9/9 includes
actual restricted runtime-role Strategy generation. Python scripts10/10, renderer29/29.

Warnings: commons-logging discovery/Mockito dynamic attach, Hibernate dialect/JTA
messages; cached Spring contexts emitted Hikari warnings for connections to earlier
Testcontainers databases already stopped between suites. None produced test failure;
no warning suppression/security bypass introduced. First Docker-less skipped run is
recorded historically, not used as successful qualification. Full run4m07s.


## Saved Compose V23 → V24 qualification

```powershell
.\scripts\start_local_evidence.ps1 -ProjectName cadence-issue117-validation -UpdateSignatures
```

Existing saved volume preserved; Flyway now24, Hibernate startup validation passed.
App/postgres/clamd/gateway/renderer-ready healthy; MinIO and renderer running.
FreshClam daily database28143 loaded after controlled restart. Updater's expected
missing local clamd socket notification warning was followed by explicit reload/restart;
ClamAV ready afterward. Runtime grants reapplied, `has_column_privilege` confirms
provenance UPDATE=false, status UPDATE=true, Strategy DELETE=false.
Actual loopback GET `/api/v1/health`200; `/api/v1/brands` without token401 with Bearer
challenge. This is startup/auth rejection, not end-to-end Strategy REST (API is #102).
Ignored logs: var/issue97-compose-startup.log and var/issue97-compose-smoke.log.
No production infrastructure deployment or live AI provider was attempted.


## Remote dependency/security qualification

[CI37228608605](https://github.com/Sanyoghk/Cadence/actions/runs/37228608605),
SHA `660d4c7d37a9c24b880cd1f95df7d2ec15e5ba31`: dependency-security job success.
Resolved runtime/test SBOM, pinned scanner checksum, inventory gate and Python10/10
passed; **165 Maven dependencies**, **no OSV vulnerabilities reported at scan time**.
This is a time-specific dependency result, not SAST/container/deployment certification.
Renderer enforcing AppArmor and kernel/sandbox preflight passed; full remote Maven
result is appended only after completion. Local product source unchanged after qualification.


## Next issue handoff

#102 получил concrete implementation notes: scoped DTO creation с loaded lazy
collections, reuse OWNER/common Problem Details/OpenAPI, safe unavailable mapping,
expectedVersion и consistent Workspace→Brand→Strategy locking, approval validated
committed draft и edit-as-new-version/revision lineage без provenance rewrite.
Это plan следующего issue, не assertion о выполненном REST workflow.


## Full remote qualification / GitHub completion

[CI37228608605](https://github.com/Sanyoghk/Cadence/actions/runs/37228608605), SHA
`660d4c7d37a9c24b880cd1f95df7d2ec15e5ba31`: **verify + dependency-security success**.
Linux `sh ./mvnw --batch-mode --no-transfer-progress clean verify`: **678 tests,
0 failures/errors/skips**, BUILD SUCCESS11m25s; compilation/JAR/Spotless/JaCoCo passed.
Scripts Python10/10, renderer29/29; required PostgreSQL and real provider report gates
passed without skips; enforcing renderer AppArmor/kernel boundary/sandbox preflight
and isolated screenshot1280×720 passed. A denied renderer boundary negative test
prints failed-container diagnostics; RendererContainerIntegrationTest6/6 passed.
OSV verified165 Maven components without vulnerabilities reported at scan time.

#97 closed completed; #102 tagged status:next with concrete handoff notes; #107 updated
without marking broader approved Brand Identity/AI/deployment workflow complete.
Commits:755e395 port,1a1759a fenced persistence,660d4c7 local report. Subsequent docs-only
qualification commit records these remote facts; product source/tests remain identical
to the qualified SHA. Push goes to mvp, no merge or commercial deployment performed.
