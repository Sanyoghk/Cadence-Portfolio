# #121 — bounded TEXT analysis и exact citations

Дата: 2026-10-12. Начальное состояние: `mvp`, HEAD `aa54bfd`, clean working tree.
Это законченный analysis slice; whole #121 остаётся IN PROGRESS.

## Что было сделано

Добавлено явное действие `analyze-evidence`: backend передаёт adapter только актуальные
reviewed TEXT snapshots и принимает только цитаты из выбранных источников. Источники,
actor, declared input и цитаты сохраняются в immutable attempt history. Старый analysis
по brandName/industry сохраняет свой contract; legacy receipts не получают выдуманного actor.

## Как это работает

Сервер выбирает 1–5 admissions в порядке evidence UUID, максимум 20000 UTF-8 bytes.
Нулевой/слишком большой набор даёт 409, а не молчаливое усечение. Admission должен совпадать
с source/Brand/Workspace versions, text/checksums, policy и текущим lifecycle; withdrawal
любого review того же sourceVersion запрещает использование. Infrastructure storage key/URL
fields не добавляются в input; reviewed text остаётся произвольным недоверенным текстом.

Provider output недоверен. `validateCitations` требует 1–10 distinct citations с selected
admission ID и exact nonblank quote, до 500 UTF-16 units / 1000 UTF-8 bytes. Invalid output
превращается в безопасный 503. HUMAN_REVIEW_REQUIRED обязателен; DECLARED_FIELDS_ONLY
в evidence mode запрещён. Local fake сохраняет DEMONSTRATION_ONLY и не делает реальных
выводов из источников. Production configuration продолжает fail-closed.

После I/O completion заново проверяет actual OWNER/account/membership, parent lifecycle,
versions, источники, withdrawals, input binding и deadline. Сначала сохраняются citations,
затем signature и SUCCEEDED receipt в одной transaction. Ошибка откатывает публикацию;
FAILED/SUPERSEDED history сохраняется отдельно. Terminal cleanup после revocation может
закончить только свою bound attempt, без права публиковать content.

## Архитектурное решение

Расширены существующие provider-neutral command/result ports с совместимыми legacy
constructors. Orchestration остаётся вне transaction, persistence phases — отдельный Spring
bean с REQUIRES_NEW/READ_COMMITTED. Domain entities не импортируют application DTO.

Actor-sensitive операции используют Account → Workspace → Brand → Evidence locks.
V34 добавляет immutable source/citation tables и nullable actor/declared snapshot columns.
Deferred constraint trigger проверяет полный source набор и canonical SHA-256 input digest
после child inserts; обычный немедленный trigger увидел бы неполный JPA graph.
Runtime grants запрещают UPDATE/DELETE новых receipts. V1–V33 не менялись.
Legacy `Identity from-analysis` явно отклоняет evidence-mode receipts в domain и новом
PostgreSQL trigger: отдельная integration должна сохранить citations и проверить withdrawal
на copy/approval boundary. Иначе старый общий copy action обошёл бы новый review lifecycle.

Alternative: автоматически добавлять evidence в старый action — проще, но молча меняет
contract. Client-selected IDs потребовали бы дополнительного API/product решения. Выбрано
явное bounded server-selected действие. Links переиспользуют immutable admission text,
не создавая ещё одну полную копию. Цена — две таблицы, deferred checks и новый endpoint.

## Security Review

Защищаем tenant text, цитаты, signature и audit history. Caller — authenticated OWNER.
Trust boundaries: HTTP principal → actual DB authorization; DB snapshots → adapter;
adapter output → validated application result; external I/O → fenced publication.

**Threat: IDOR/BOLA или stale principal после revocation.**
Protection: scoped Brand lookup, bound actor, actual DB active OWNER/membership и Account lock
на capture/completion. Verification: foreign scope, VIEWER, wrong completion actor,
membership removal и реальное ожидание Account lock до revocation commit.

**Threat: forged quotation/provenance и prompt injection.**
Protection: selected immutable inputs, checksum binding, exact quote validation и selected-source
FK/SQL substring guard; required human-review warning. Verification: invented/foreign/missing/
duplicate quotes и SQL forgery. Это ограничивает provenance forgery, но не доказывает
семантическую корректность модели и не является полной защитой live AI от prompt injection.

**Threat: stale/withdrawn sources опубликованы после I/O.**
Protection: fresh transaction, reviewed locks, versions/checksums/withdrawal/lifecycle/deadline
rechecks, PostgreSQL publication guards. Verification: source version change, withdrawal during
adapter call, archived parent, access revocation, SQL insertion after withdrawal; existing
declared analysis suite дополнительно проверяет общий deadline/recovery mechanism.

**Threat: обход evidence lifecycle через legacy Identity copy.**
Protection: evidence-mode receipt нельзя прикрепить к Identity через legacy copy до отдельной
qualification. Verification: shared H2/PostgreSQL use-case regression и raw SQL insert denial;
прежние declared receipts/manual drafts продолжают проверяться полным suite.

**Threat: resource exhaustion.**
Protection: bounded query до 6 rows для обнаружения превышения, 5 inputs/20000 bytes,
10 citations/bounded quotes, один RUNNING attempt/Brand и 60-second publication deadline.
Verification: sixth source, multibyte overflow и exact 20000-byte port boundary.
Deadline запрещает публикацию, но не останавливает произвольный adapter code.
Rate/cost/concurrency controls остаются production blockers отдельного scope.

**Threat: history rewrite, unsafe errors, extra exposure.**
Protection: append-only triggers, restricted runtime grants, DTO/no-store, generic errors;
parameterized JPQL/JDBC, no new external URL/file/shell handling, no new dependency.
Verification: raw SQL delete/actor rewrite, restricted-role UPDATE/DELETE denial,
OpenAPI response checks, anonymous packaged request. Secrets не добавлены.
SecurityFilterChain/CSRF/CORS не менялись: explicit Bearer header, без cookie sessions.

## Изменённые файлы

Manifest ниже перечисляет каждый source/config/test/doc file этого slice.

| File | Назначение |
|---|---|
| `src/main/java/com/cadence/application/ports/AnalysisTextSource.java` | Immutable validated TEXT input, checksum/Unicode/UTF-8 bounds. |
| `src/main/java/com/cadence/application/ports/AnalysisCitation.java` | Bounded citation contract. |
| `src/main/java/com/cadence/application/ports/BrandAnalysisCommand.java` | Selected inputs, canonical digest и exact citation validation. |
| `src/main/java/com/cadence/application/ports/BrandAnalysisResult.java` | Immutable citations и legacy-compatible constructor. |
| `src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java` | Scoped current TEXT query с bounded Pageable. |
| `src/main/java/com/cadence/application/repositories/BrandAnalysisSourceRepository.java` | Ordered attempt input links. |
| `src/main/java/com/cadence/application/repositories/BrandAnalysisCitationRepository.java` | Citation persistence. |
| `src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java` | Capture/completion/cleanup, actual actor authorization и publication fence. |
| `src/main/java/com/cadence/application/services/BrandIngestService.java` | Reject malformed model citations/warnings with safe 503. |
| `src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java` | Orchestration без I/O transaction. |
| `src/main/java/com/cadence/domain/BrandAnalysisAttempt.java` | Immutable actor/declared input snapshot. |
| `src/main/java/com/cadence/domain/BrandIdentity.java` | Fail-closed evidence receipt → legacy Identity copy до отдельной source integration. |
| `src/main/java/com/cadence/domain/BrandAnalysisSource.java` | Immutable source entity. |
| `src/main/java/com/cadence/domain/BrandAnalysisCitation.java` | Immutable citation entity. |
| `src/main/java/com/cadence/infrastructure/analysis/DeterministicBrandAnalysisAdapter.java` | Explicit demo quotes; legacy/evidence provenance warnings. |
| `src/main/java/com/cadence/presentation/EvidenceAnalysisController.java` | OWNER POST action и no-store. |
| `src/main/java/com/cadence/presentation/dto/EvidenceAnalysisResponse.java` | Signature/citation/provenance response DTO. |
| `src/main/resources/db/migration/V34__bounded_evidence_analysis.sql` | Tables, constraints, immutable/authorization/source/publication guards и deferred digest seal. |
| `src/main/resources/openapi/cadence.json` | New endpoint, responses и schemas. |
| `docker/postgres/runtime-permissions.sql` | Запрет runtime rewrite/delete новых receipts. |
| `src/test/java/com/cadence/application/EvidenceAnalysisContractTest.java` | Port Unicode/digest/byte/citation negative tests. |
| `src/test/java/com/cadence/infrastructure/analysis/EvidenceAnalysisFakeTest.java` | Demo warnings и Unicode quote boundary. |
| `src/test/java/com/cadence/support/EvidenceAnalysisApiContract.java` | Shared real use-case/REST/security scenarios. |
| `src/test/java/com/cadence/security/EvidenceAnalysisApiIntegrationTest.java` | H2 execution shared API contract. |
| `src/test/java/com/cadence/persistence/PostgreSqlEvidenceAnalysisTest.java` | Real PostgreSQL shared contract, SQL/runtime guards и Account concurrency. |
| `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java` | Exact migration inventory включает V34. |
| `src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java` | UTC audit inventory включает новые tables. |
| `src/test/java/com/cadence/presentation/OpenApiContractTest.java` | DTO field/schema alignment. |
| `scripts/verify_postgres_report.py` | New PG suite обязательна, без skips/missing report. |
| `scripts/test_verify_postgres_report.py` | Regression false-green gate. |
| `docs/adr/0015-bounded-text-analysis-citations.md` | Decision/alternatives/bounds. |
| `docs/adr/README.md` | ADR discovery. |
| `docs/evidence-analysis-api.md` | API contract и limitations. |
| `docs/issue-121-evidence-analysis-slice.md` | Этот detailed report. |
| `docs/verification.md` | Qualification summary. |
| `docs/issue-sync.md` | Issue/continuation alignment. |
| `DEVELOPMENT_PLAN.md` | Partial stage 2 progression и remaining Identity integration. |
| `DEVELOPMENT_PLAN_RU.md` | Согласованная русская версия плана. |

Ignored var logs/launchers и generated target reports/JAR не входят в committed source.

## Runtime Flow

```text
POST analyze-evidence → Bearer authentication → OWNER/principal guard
    → AnalyzeEvidenceUseCase (NOT_SUPPORTED)
    → startEvidence (REQUIRES_NEW): actual access + locks + bounded current admissions
    → RUNNING attempt + immutable sources + deferred input seal → commit
    → BrandIngestService → BrandAnalysisPort (без DB transaction)
    → validated result + exact citations
    → complete (REQUIRES_NEW): actual access/source/deadline recheck
    → citations + signature + SUCCEEDED → atomic commit
    → DTO + no-store
```

## Тесты

Окончательная команда (после всех code/SQL изменений):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
```

BUILD SUCCESS, 2026-10-12T04:37:58+01:00, 4:49. 105 suites / 894 tests:
0 failures, 0 errors, 3 Windows skips; 891 executed pass.
H2 evidence API15/15, PostgreSQL21/21, port5/5, fake1/1;
старый PostgreSQL analysis13/13 и admission migrations4/4 также прошли.
PostgreSQL/MinIO/ClamAV/renderer/full pipeline mandatory report gates PASS, без skips.
Spotless, compilation, JAR packaging и JaCoCo gates PASS;
LINE5312 covered /308 missed =94.52%.

Первые targeted runs были неуспешны: SQL CASE syntax/общий trigger, который обращался к
несуществующему `NEW.quote` у source rows, и Mockito/anonymous fixture errors. Исправлены
реализация и fixtures; assertions не ослаблялись. Успешный targeted run — 36/36:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceAnalysisApiIntegrationTest,PostgreSqlEvidenceAnalysisTest,BrandAnalysisContractTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceAnalysisApiIntegrationTest,PostgreSqlEvidenceAnalysisTest,EvidenceAnalysisContractTest,EvidenceAnalysisFakeTest,OpenApiContractTest'
```

Последняя targeted команда запускалась дважды: первый run неуспешен, следующий — 36/36.
Первый полный run прошёл 890 tests, затем security review нашёл half-surrogate quotation
edge case. Добавлены UTF-8 encoder checks в port/domain и отдельный regression test;
после этого выполнен повторный full run, итоговые counts приведены выше.
Повторный run прошёл 891 tests. Последующий boundary review обнаружил, что legacy Identity
copy принимает evidence receipt без новой withdrawal integration. Добавлены domain/SQL
deny guards и regression cases; после этого выполнен третий окончательный full run.

Дополнительные команды:

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\evidence-analysis-startup.ps1
git diff --check
```

Python10/10 (первый sandbox run не смог писать TEMP, повторён с разрешённым filesystem
access), renderer29/29, Linux35/35 без skips; Linux проверяет также 3 Windows symlink cases.
Наборы выполняются отдельно и не суммируются с Maven как независимые уникальные cases.
Packaged local JAR: health200, anonymous new POST401; процесс остановлен в finally.
CDS/deprecated S3/unchecked test code/Hikari disposed-container warnings не подавлялись.
Warnings ожидаемых negative configuration tests не являются application startup failure.

## Security Tests и Edge Cases

- Anonymous, VIEWER, чужой scope, другая completion identity, stale membership → отказ.
- Withdrawal до capture → adapter не вызывается; во время I/O → SUPERSEDED, без публикации.
- Actual membership/account revocation → publication запрещена; Account lock order проверен.
- Invented/foreign/duplicate/missing quote, missing human-review warning → отказ.
- Archived parent, changed source version → SUPERSEDED, original signature не заменяется.
- Шестой source / multibyte aggregate overflow → conflict без truncation.
- Exact UTF-8 boundary, immutable lists, malformed surrogate/control/checksum → port checks.
- Fake quote не разрезает surrogate pair. SQL receipts нельзя переписать или удалить.
- Legacy Identity copy отклоняет evidence receipt на application/domain и PostgreSQL boundaries.
- Legacy declared-only flow и populated migration suites выполняются в полном suite.

## Непроверенные аспекты

Live Clerk и real AI provider не квалифицированы. Новых production OSV/SAST/container scans
этот run не выполнял. Hosted TLS/IAM/AppArmor/sandbox, rate/cost budgets, реальные provider
timeouts/cancellation, privacy/retention policy требуют отдельной проверки.
Истечение deadline именно нового HTTP evidence action с медленным live provider отдельно
не воспроизводилось; общий fence покрыт существующим PostgreSqlBrandAnalysisTest.
Exact quote не доказывает truth/relevance. Non-TEXT inputs и evidence → Identity copy
не реализованы; новый receipt не следует выдавать за готовую Identity revision.

## Что мне важно понять

Проверка доступа перед model call недостаточна: пока выполняется I/O, пользователь может
потерять права или отозвать source. Поэтому публикация — отдельная проверяемая transaction.
Foreign IDs запрещает application validation, а FK/triggers защищают от обхода этого слоя.
Human approval не заменяется красивым AI output или наличием точной цитаты.

## Что мне стоит изучить

1. Граница доверия (trust boundary): model output проверяется так же строго, как HTTP input.
2. Ограждение публикации (publication fencing): версии/доступ/source/deadline проверяются снова.
3. Отложенное ограничение (deferred constraint): проверяет полный input graph при commit.
4. Происхождение данных (provenance): immutable links показывают конкретный reviewed snapshot.
5. Минимальные привилегии (least privilege): runtime может добавлять receipts, но не менять history.

## Production considerations и статус

#121/#107 остаются open. Следующий slice — evidence receipt/citations → Identity provenance,
затем non-TEXT eligibility и APPROVED Identity → Strategy. AI Jobs/budgets/full Playbook и
real adapter #120 отдельны. Push/exact-source-SHA CI выполняются после whole issue;
merge/commercial deployment требуют отдельного согласования.

Slice сохранён отдельным local commit `feat: analyze reviewed text evidence (#121)`;
его actual SHA указывается в итоговом ответе и GitHub synchronization. Push/remote CI этого
slice не выполнялись; текущий final qualification — local source, без claims hosted deployment.

Подтверждённые факты: фактические команды/results ниже и inspection изменённого code/SQL.
Инженерные выводы: layered validation/locks/immutable receipts снижают указанные attack vectors.
Assumptions: live provider не выбран; DB runtime credentials используются только trusted backend;
hosted security и semantic correctness источников/model пока не квалифицированы.
