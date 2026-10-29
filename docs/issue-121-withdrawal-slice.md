# #121 — отзыв TEXT admission и актуальный snapshot

Дата: 2026-10-12. Законченный local vertical slice; #121 остаётся открытым.

## Что было сделано

OWNER может отозвать ранее подтверждённый TEXT admission без уничтожения review history.
Отдельный endpoint возвращает точный reviewed snapshot только после проверки актуальности.
AI, citations, bulk source selection и Strategy integration этот этап не добавляет.

## Как это работает и Runtime Flow

Bearer principal → OWNER controller → ReviewEvidenceUseCase → scoped ownership lookup →
Account lock → Workspace lock → Brand lock → Evidence lock → actual active OWNER/membership →
admission lookup с evidenceId → eligibility checks либо withdrawal insert → PostgreSQL → DTO.

Snapshot включает immutable metadata и текст до20000 UTF-16 units. Сервер сравнивает source,
Brand/Workspace versions, source/content SHA-256, exact text, policy и lifecycle; withdrawal
запрещает чтение snapshot. Withdrawal не меняет admission/source/ingest versions и сохраняет
actor/time, определённые principal/Clock. Archived/stale source разрешено отозвать.

## Архитектурное решение

Отдельная EvidenceAdmissionWithdrawal сохраняет audit неизменяемым. Mutable flag ломал бы
предыдущий immutable contract, deletion уничтожала provenance. Цена решения — одна таблица,
дополнительный indexed EXISTS и обязательная future publication recheck. Explicit resource
binding предотвращает получение receipt другого evidence. Domain отвечает за audit time,
application — authorization/currentness, DTO — API contract, PostgreSQL — integrity.
REQUIRES_NEW/READ_COMMITTED исключает reuse старого persistence context; locks сериализуют
withdrawal с чтением snapshot. External I/O внутри этих transactions отсутствует.

## Security Review

Threat: IDOR/BOLA, подмена actor или расширение workspace scope.
Protection: method security на controller/use case, actual DB OWNER/activity/membership,
scoped evidence lookup и admission.id+evidence.id binding.
Verification: shared HTTP contract на H2/PostgreSQL и direct forged actor/scope tests.

Threat: повторное использование withdrawn/stale input и гонка с отзывом.
Protection: versions/checksums/exact text/policy/lifecycle/withdrawal rechecks в новой transaction;
единый parent-first lock order, UNIQUE(admission_id).
Verification: source/parent mutation, archive, replay и PostgreSQL blocking race test.
Snapshot, выданный до withdrawal, остаётся у caller: future publication fencing обязателен.

Threat: rewriting audit либо обход application permissions через SQL.
Protection: append-only V32 trigger, actor/membership/time checks, FK/UNIQUE, runtime revoke.
Verification: forged actor/foreign membership/past/future time, SQL UPDATE/DELETE/duplicate,
реальный runtime INSERT с UPDATE/DELETE42501.

Threat: cross-tenant SQL reparenting с совпадающими parent versions.
Protection: V32 запрещает UPDATE Evidence.brand_id и Brand.workspace_id независимо от наличия
review; это усиливает существующий domain/API invariant. Triggers не берут дополнительные
parent locks и не добавляют новый lock order.
Verification: PostgreSQL пытается перенести source/Brand к другому version-zero tenant;
оба изменения блокируются23514, original snapshot продолжает соответствовать исходному tenant.

Threat: injection, prompt injection, XSS, sensitive data exposure и resource exhaustion.
Protection: parameterized JPA/JDBC, отдельные DTO, plain bounded text/no private storage keys,
no-store, существующие controlled Problem Details; review не делает инструкции доверенными.
Verification: API schema assertions, исходный markup сохраняется как text; AI execution отсутствует.
Frontend должен экранировать вывод; live AI adapter должен изолировать source data от instructions.
Новые dependencies, external URLs, file I/O, cookie auth и SecurityFilterChain changes отсутствуют.

## Изменённые файлы

Application paths относительно src/main/java/com/cadence:

- domain/EvidenceAdmissionWithdrawal.java — immutable withdrawal actor/time/admission.
- application/repositories/EvidenceAdmissionWithdrawalRepository.java — bounded indexed existence.
- application/repositories/EvidenceAdmissionRepository.java — explicit admission/evidence binding.
- application/results/EvidenceWithdrawalDetails.java — audit response.
- application/results/EvidenceAdmissionSnapshot.java — bounded snapshot response.
- application/usecases/ReviewEvidenceUseCase.java — currentness/withdrawal commands и authorization.
- presentation/EvidenceAdmissionController.java — два OWNER-only маршрута с no-store.

Configuration/contracts:

- src/main/resources/db/migration/V32__evidence_admission_withdrawal.sql — новая таблица/guards,
  immutable Evidence→Brand и Brand→Workspace database boundaries;
  существующие V1–V31 не менялись.
- docker/postgres/runtime-permissions.sql — запрет runtime UPDATE/DELETE withdrawal history.
- src/main/resources/openapi/cadence.json — новые operations и schemas.

Tests относительно src/test/java/com/cadence:

- domain/EvidenceAdmissionWithdrawalTest.java — valid/invalid immutable audit construction.
- support/EvidenceAdmissionApiContract.java — HTTP/currentness/revocation/IDOR/direct boundary tests.
- persistence/PostgreSqlEvidenceAdmissionTest.java — SQL integrity, runtime grants и blocking race.
- persistence/PostgreSqlEvidenceAdmissionMigrationTest.java — populated V31→V32 preservation.
- persistence/PostgreSqlPersistenceTest.java — strict inventory расширен до V32.
- persistence/PostgreSqlUtcMigrationTest.java — две новые TIMESTAMPTZ audit columns.
- presentation/OpenApiContractTest.java — reflection contract новых DTO.

Documentation: docs/adr/0013-evidence-admission.md, docs/evidence-admission-api.md,
этот отчёт, docs/verification.md, docs/issue-sync.md, DEVELOPMENT_PLAN.md и DEVELOPMENT_PLAN_RU.md.

## Тесты

Финальная команда:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
```

BUILD SUCCESS, 2026-10-12T02:50:24+01:00, 04:19 min.101 suites,840 tests,0 failures/errors,
3 Windows filesystem skips;837 выполненных Maven tests прошли. Отдельный Linux35/35 без skips
закрывает Windows symlink scenarios;32 остальных tests дублируются, их не складываем с840.
Domain withdrawal1/1,H2 admission HTTP11/11,PostgreSQL admission20/20,migrations2/2.
Spotless/compilation/JAR/JaCoCo passed; LINE5048 covered,299 missed —94.41%.

Фактически выполненные дополнительные команды:

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\withdrawal-startup.ps1
git diff --check
```

Python10/10,renderer29/29,Linux35/35. Mandatory PostgreSQL/MinIO/ClamAV/renderer/full pipeline
report gates прошли без required skips/errors. Packaged local health200; anonymous preview,
admit,snapshot,withdrawal401. Ignored var launcher запускает JAR только на loopback и останавливает
только собственный process. Live Clerk/provider не используются.

Первый targeted run:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceAdmissionWithdrawalTest,EvidenceAdmissionApiIntegrationTest,PostgreSqlEvidenceAdmissionTest,OpenApiContractTest'
```

34 tests,15 executed passed,19 PostgreSQL skipped из-за неработавшего Docker; не считается PG
qualification. После запуска Docker первый full run имел839 tests,1 failure,0 errors,3 skips:
DELETE regression ошибочно выбирал чужого actor и не находил row. Запрос исправлен на реальное
удаление созданной withdrawal; assertion сохранён. После review добавлен parent-identity guard
и regression. Финальный full run840 выше полностью повторяет suite после этих изменений.
Существующие warnings: JDK CDS, deprecated S3 API/test helpers, unchecked test code и Hikari
closed connections после завершения disposable PostgreSQL containers; warnings не подавлялись.

## Security Tests и Edge Cases

Anonymous; EDITOR/VIEWER; foreign workspace/receipt; wrong evidence binding; revoked membership;
direct actor/scope spoof; changed source/parent versions; archived source; repeated withdrawal;
повторный admission того же sourceVersion после withdrawal; original history сохранена;
withdrawal до review time/в будущем; SQL rewriting/deletion; runtime least privilege;
concurrent withdrawal блокирует snapshot до commit, после commit snapshot409.
Populated V31 review не меняется и не отзывается автоматически миграцией.
Перенос Evidence/Brand в другой version-zero tenant запрещён PostgreSQL.

## Непроверенные аспекты и Production considerations

Нет actual AI consumer, batch selection, citation validation, publication fencing и real provider
policy/model. URL/PDF/image eligibility требует отдельно scan/artifact/storage lifecycle gates.
Snapshot, выданный раньше, невозможно удалить из памяти caller; его запрещено считать вечным
разрешением на publication. Re-admission withdrawn sourceVersion намеренно не разрешён.
Изменение Brand/Workspace version также делает review устаревшим; UNIQUE(evidence_id,source_version)
пока не позволяет повторный review того же sourceVersion в новом parent context. Версионированный
re-review lifecycle необходим перед live generation, иначе такой источник останется недопустимым.
Retention/data erasure требуется отдельно согласовать перед live provider. Shared DB role при
компрометации credentials не доказывает JWT actor. Hosted CI exact SHA, live Clerk/AI, свежие
OSV/SAST/container scans и реальный commercial deployment этим local run не квалифицированы.

## Что мне важно понять

Неизменяемый audit описывает прошлое; разрешение использовать данные — текущее состояние.
Поэтому отзыв добавляет новый факт, а не переписывает старый. Проверка перед AI call не
гарантирует актуальность после него: за время внешнего вызова owner может отозвать источник.
Будущий generation workflow должен повторить authorization и eligibility перед publication.

## Что мне стоит изучить

1. Append-only audit — прошлые факты не редактируются; withdrawal добавляет отдельный факт.
2. Гонка проверки и использования (TOCTOU) — разрешение может измениться после snapshot.
3. Порядок блокировок (lock ordering) — одинаковый порядок уменьшает риск deadlock.
4. Эшелонированная защита (defense in depth) — use case и PostgreSQL защищают одни invariants.
5. Least privilege — runtime INSERT/SELECT разрешены, изменение audit запрещено grants.

## Факты, выводы, assumptions и Git

Подтверждённые факты: финальные commands/results выше, immutable SQL boundaries, runtime grants,
реальная PostgreSQL concurrency и packaged startup. Инженерный вывод:
append-only withdrawal согласуется с immutable review и позволяет запретить его future reuse.
Assumption: текущий продукт не поддерживает отмену withdrawal; новый lifecycle потребует отдельного
контракта. #121 не завершён: batch evidence→generation/citations и approved Identity→Strategy
остаются следующими этапами. Этот результат сохраняется отдельным local commit
`feat: withdraw reviewed evidence admission (#121)`; push/remote CI после whole issue.
GitHub #121/#107 синхронизируются с commit SHA и результатами; обе задачи остаются открытыми.
