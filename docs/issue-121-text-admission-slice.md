# #121 — reviewed TEXT evidence admission

Дата: 2026-10-11. Исходный HEAD: `aa89e36f2efb531bfc414e97cd4da54da225441a`, ветка mvp.

## Что было сделано

Реализован законченный TEXT source review workflow: OWNER видит точный сохранённый текст,
подтверждает version/checksum tokens и получает immutable admission snapshot с reviewer/time.
Это отдельный шаг перед будущей generation с citations. #121 остаётся IN PROGRESS:
URL/PDF/image admission, withdrawal/consumption, validated citations и approved Identity→Strategy
ещё не реализованы. Real AI #120, AI Jobs/budgets и полный Playbook остаются отдельно.

## Как это работает

GET `/api/v1/evidence/{evidenceId}/ai-admission` создаёт согласованный preview: text, sourceVersion,
brandVersion, workspaceVersion, sourceChecksum, contentChecksum, policyVersion. POST принимает
только tokens; content, actor и time не определяются request. При изменённом token409 и требуется
новый review. Snapshot сохраняет точные данные, source и parent versions, actor/time и policy.
Повторное подтверждение409. Evidence.status не изменяется, AI port не вызывается.

Registration checksum — SHA256 UTF-8(`TEXT\n`+text), contentChecksum — SHA256 UTF-8(text).
Разные digests связывают snapshot с сохранённым источником и точным будущим model input.
Все DTO создаются внутри transaction; Entity/private storage references не возвращаются наружу.
Оба success responses и errors no-store. Только TEXT/PENDING/active parents допустимы.

## Архитектурное решение

EvidenceAdmission — самостоятельная append-only entity, не поле mutable Brand signature.
Application use case ReviewEvidenceUseCase отделён от Controller и будущих model adapters.
Review не удерживает transaction между GET и POST: каждый запрос получает fresh REQUIRES_NEW,
READ_COMMITTED snapshot. Account→Workspace→Brand→Evidence locks сериализуют review с
revocation/archive writers. Scoped lookup и actual DB membership работают одновременно.

Новая V31, FK/UNIQUE/checks/trigger и runtime grants защищают историю независимо от Java.
V1–V30 не изменены; backfill review/citations отсутствует. Dependencies не добавлены.
Alternatives/trade-offs: [ADR0013](adr/0013-evidence-admission.md). Дополнительная копия bounded
текста сохраняет audit, но требует retention/deletion policy перед live consumption.

## Security Review

Threat: IDOR/BOLA, foreign IDs, expanded scope и revoked OWNER claims.
Protection: scoped lookup до locks, child binding Brand ID, method security scope/actor checks,
actual active DB OWNER/membership rechecks.
Verification: HTTP и direct-bean negatives, role/activity/membership rejection, PG revocation races.

Threat: клиент подтверждает другую версию/текст или подставляет reviewer/time.
Protection: server-only content/actor/Clock, exact checksum и Evidence/Brand/Workspace version tokens.
Verification: stale/invalid token matrix, forged JSON extras, source digest/domain checks, PG raw SQL attacks.

Threat: rewrite/delete audit, fabricated legacy admission.
Protection: immutable snapshot, V31 invoker guard, FK/UNIQUE/checksum checks и revoked UPDATE/DELETE grants.
Verification: populated V30→V31 migration, raw SQL rejection и actual restricted runtime role.

Threat: review/revocation/replay concurrency.
Protection: reviewed parent-first lock order, fresh transaction, post-lock actual account checks,
unique(evidence_id,source_version).
Verification: два admission calls→один snapshot; concurrent account/membership revocation→отказ.

Threat: prompt injection/XSS/injection/excessive data exposure.
Protection: snapshot остаётся недоверенным plain text; в этом этапе нет model/tool/HTML execution,
SQL parameterized, metadata response не дублирует text; private references/secrets отсутствуют.
Verification: markup сохранён как data; Unicode/control/size negatives; inspection отсутствия I/O/logging.
Future UI обязан escaped rendering; model adapters обязаны собственные output/citation/tool restrictions.

Threat: resource exhaustion и supply chain.
Protection: bounded20000 UTF-16 text, bounded single-source queries, один snapshot/sourceVersion,
existing request/transaction limits; dependencies не добавлены.
Verification: max-size edge, oversized domain rejection, replay/concurrency tests, repository inspection.
Production rate limiting и operational controls остаются #87/Phase8.

## Runtime Flow

```text
Bearer → Spring Security OWNER → Controller → request DTO validation
    → scoped Evidence ownership projection
    → Account lock → Workspace lock → Brand lock → Evidence lock
    → actual account/membership + active parents + source eligibility
    → exact stored TEXT + source digest + version/content review token
    → append-only EvidenceAdmission → PostgreSQL constraints/guard → commit
    → bounded immutable response DTO, no-store
```

## Тесты

Окончательный full: **BUILD SUCCESS,100 suites,827 tests,0 failures/errors,3 Windows filesystem
skips;824 executed tests прошли**. Linux filesystem35/35 без skips покрывает три symlink cases;
не суммируем повторно остальные32 scenarios. Finished2026-10-11T00:26:40+01:00, duration04:22.
Domain4/4,H2HTTP7/7,PostgreSQL13/13,populated admission migration1/1; ConfigurationProfiles6/6
включает H2 isolation regression; прежний UseCaseMethodSecurity10/10 прошёл после исправления.
Mandatory PostgreSQL/MinIO/ClamAV/renderer/full-provider report gates PASS; Python scripts10/10,
renderer29/29. Compilation/JAR/Spotless/JaCoCo PASS: LINE5000covered/299missed,94.36%.

Фактически выполненные команды окончательной qualification:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\admission-startup.ps1
git diff --check
```

Предшествующий focused run (25/25):
`.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=EvidenceAdmissionTest,EvidenceAdmissionApiIntegrationTest,PostgreSqlEvidenceAdmissionTest,PostgreSqlEvidenceAdmissionMigrationTest,OpenApiContractTest' test`.

Startup launcher запускает hidden `java -jar target/cadence-0.0.1-SNAPSHOT.jar
--spring.profiles.active=local --server.address=127.0.0.1 --server.port=18093`, ждёт readiness
с deadline45s и в finally останавливает только собственный process. Packaged health200;
anonymous GET/POST `/api/v1/evidence/{id}/ai-admission`401. Local profile — H2 fixture,
не live Clerk/production deployment. Existing renderer image используется без изменений
Dockerfile/renderer sources; не заменяет live Linux kernel/AppArmor qualification.

Ignored logs: var/admission-focused.log, var/admission-full.log (первый failed run),
var/admission-full-final.log, var/admission-linux-final.log, var/admission-startup.*.log;
Surefire/JaCoCo/JAR — target. Existing deprecated/unchecked compile warnings, JVM CDS и
closed Testcontainers connection warnings не подавлялись. Assertions не ослаблены.

Inspection сравнивает все прежние OpenAPI paths/schemas с HEAD: unchanged; V1–V30 unchanged.
Focused промежуточного source:25/25 без failures/errors/skips. После него добавлен archived Workspace/
forged direct admission regression, который входит в окончательный full run.

Промежуточные ошибки исправлены: test вызвал несуществующий deactivate; sandbox блокировал
loopback/Docker/temp; derived JPA query конфликтовал с getEvidenceId; MockMvc context setup и
pooled SET ROLE загрязняли fixtures. Actual revocation теперь SQL-controlled, query explicit,
anonymous context очищается, direct calls получают explicit auth, SET ROLE использует dedicated
непулируемый connection. Assertions и security implementation ради теста не ослаблены.

Первый full run выявил два устаревших exact migration assertions V1–V30 и один UTC column-count
assertion (26→28 из-за двух новых audit columns V31). Они расширены до нового schema contract,
не заменены на >= или отключены. Этот run также воспроизвёл10 errors UseCaseMethodSecurityTest:
разные Spring contexts делили одну H2 database, eviction/create-drop удалял чужую schema.
Test profile теперь использует generate-unique-name=true без fixed URL. Regression test создаёт
одноимённые таблицы в независимых contexts и проверяет, что закрытие второго не разрушает первый.
Production PostgreSQL/local profiles не изменены.

## Security Tests

Anonymous/EDITOR/VIEWER/foreign/missing IDs; forged scope/actor; actual inactive/role/membership;
stale source/Brand/Workspace/checksum; invalid/missing tokens; forged client text/time/reviewer;
malformed stored text; unsupported URL/FILE; immutable raw SQL history, forged content/checksum/
source version/actor/time/policy; restricted runtime update/delete denial; replay/revocation races.

## Edge Cases

Unicode Māori/emoji, CR/LF/tab, markup как data,20000-unit maximum, oversized/blank/unpaired surrogate/
controls, parent context changes, archived Brand/Workspace, concurrent confirmation, legacy upgrade.
Не все эти cases вызываются через HTTP: malformed Unicode/control/FILE eligibility часть domain tests.

## Непроверенные аспекты

No live AI/Clerk, production deployment, new exact-SHA remote CI, fresh OSV/SAST/container scan,
live renderer AppArmor/kernel qualification, load/retention/delete/withdrawal qualification.
Существующий renderer fixture не доказывает hosted sandbox. Эти gates не заменяются local tests.
Human review audit не доказывает просмотр UI и не гарантирует content safety/права автора.
Compromised shared runtime credentials не являются криптографическим доказательством JWT actor.

## Что мне важно понять

Malware scan отвечает на иной вопрос, чем допуск источника к AI. Поэтому ingest status и human
review разделены. Checksum связывает review с точным текстом; parent versions не дают незаметно
подтвердить устаревший context. Immutable snapshot сохраняет исходный input, даже если источник
позже изменён; его наличие не разрешает future generation без новых lifecycle/permission checks.

## Что мне стоит изучить

- Snapshot: неизменяемая копия входных данных, пригодная для последующего audit/provenance.
- Optimistic concurrency: request versions обнаруживают изменения между preview и confirmation.
- Pessimistic locking: row locks сериализуют admission с конкурентной revocation/archive.
- Defense in depth: Java checks, DB constraints и runtime privileges защищают разные обходы.
- Trust boundary: reviewed text остаётся data; review не превращает его в доверенные instructions.

## Production considerations

До live AI нужны selected provider/model/privacy policy, admission withdrawal/retention semantics,
bounded server-selected inputs, validated citations, source rechecks/publication fencing, budgets,
rate limits и deployed security qualification. URL/PDF/image sources требуют own eligibility gates.
Ни READY, ни citations, ни generation provenance не выдуманы для legacy sources.

## Git / issues и границы выводов

Законченный проверенный slice сохраняется local commit. #121/#107 синхронизируются после commit.
Push/exact-SHA CI после завершения #121;
merge/deployment не выполняются. Current Strategy source остаётся legacy signature.

Подтверждённые факты: реализованный scope, inspection и фактические результаты tests ниже.
Инженерные выводы: layered checks защищают перечисленные проверенные векторы, не весь deployment.
Assumptions: будущие consumers соблюдают own source/policy/permission fencing; они ещё не реализованы.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [EvidenceAdmission.java](../src/main/java/com/cadence/domain/EvidenceAdmission.java) | Immutable snapshot, TEXT eligibility, Unicode/size/digest invariants. |
| [EvidenceAdmissionRepository.java](../src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java) | Persistence и bounded duplicate lookup с explicit JPA path. |
| [BrandEvidenceRepository.java](../src/main/java/com/cadence/application/repositories/BrandEvidenceRepository.java) | Scoped ownership projection и locked child/Brand binding. |
| [EvidenceReview.java](../src/main/java/com/cadence/application/results/EvidenceReview.java) | Immutable preview DTO и согласованный token bundle. |
| [EvidenceAdmissionDetails.java](../src/main/java/com/cadence/application/results/EvidenceAdmissionDetails.java) | Immutable metadata DTO без дублирования текста. |
| [ReviewEvidenceUseCase.java](../src/main/java/com/cadence/application/usecases/ReviewEvidenceUseCase.java) | Scoped review/confirmation, actual actor/membership, locks, tokens, replay rejection. |
| [EvidenceAdmissionController.java](../src/main/java/com/cadence/presentation/EvidenceAdmissionController.java) | OWNER GET/POST API, request validation и no-store. |
| [AdmitEvidenceRequest.java](../src/main/java/com/cadence/presentation/dto/AdmitEvidenceRequest.java) | Только bounded version/checksum writable fields. |
| [V31__reviewed_text_evidence_admission.sql](../src/main/resources/db/migration/V31__reviewed_text_evidence_admission.sql) | Новая append-only таблица, FK/UNIQUE/bounds/checksums/actor/source guards. |
| [runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Запрет runtime UPDATE/DELETE admissions. |
| [cadence.json](../src/main/resources/openapi/cadence.json) | Только два новых operations и три DTO schemas; прежние contracts сохранены. |
| [EvidenceAdmissionTest.java](../src/test/java/com/cadence/domain/EvidenceAdmissionTest.java) | Domain/Unicode/type/size/digest/time negatives. |
| [EvidenceAdmissionApiContract.java](../src/test/java/com/cadence/support/EvidenceAdmissionApiContract.java) | Общий H2/PG HTTP/direct-bean/security/lifecycle contract. |
| [EvidenceAdmissionApiIntegrationTest.java](../src/test/java/com/cadence/security/EvidenceAdmissionApiIntegrationTest.java) | Исполнение общего API contract на H2. |
| [PostgreSqlEvidenceAdmissionTest.java](../src/test/java/com/cadence/persistence/PostgreSqlEvidenceAdmissionTest.java) | Общий API contract, raw SQL/runtime/concurrency/revocation/blank-text checks на PostgreSQL. |
| [PostgreSqlEvidenceAdmissionMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlEvidenceAdmissionMigrationTest.java) | Populated V30 upgrade без invented admissions/READY. |
| [PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Exact expected migration inventory расширен до V31. |
| [PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Exact UTC audit-column inventory включает две новые V31 columns. |
| [OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java) | Reflection alignment новых request/response DTO fields. |
| [ConfigurationProfilesTest.java](../src/test/java/com/cadence/configuration/ConfigurationProfilesTest.java) | Contract и regression независимых H2 contexts/schema lifecycle. |
| [application-test.properties](../src/test/resources/application-test.properties) | Unique embedded H2 database per context вместо shared fixed URL. |
| [verify_postgres_report.py](../scripts/verify_postgres_report.py) | Новые PostgreSQL suites обязательны и не могут skipped/missing. |
| [test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Regression false-green report gate для новых suites. |
| [ADR0013](adr/0013-evidence-admission.md) | Admission architecture/threat model/alternatives/remaining gates. |
| [ADR0012](adr/0012-brand-identity-drafts.md) | Связь Identity roadmap с отдельным reviewed-input workflow. |
| [ADR index](adr/README.md) | Discovery нового ADR. |
| [API contract](evidence-admission-api.md) | Routes, tokens, errors, workflow и ограничения. |
| [Этот отчёт](issue-121-text-admission-slice.md) | Реализация, qualification, manifest и границы claims. |
| [verification.md](verification.md) | Latest local verification и remaining production gates. |
| [issue-sync.md](issue-sync.md) | Current #121 scope/qualification/remaining work. |
| [README.md](../README.md) | Discovery admission workflow. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Текущий continuation/remaining scope. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Русская версия continuation/remaining scope. |

Ignored logs/smoke launcher в var и generated reports/JAR в target не являются committed source.
