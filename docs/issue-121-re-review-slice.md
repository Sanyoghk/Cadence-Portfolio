# #121 — versioned TEXT re-review по обновлённому плану

Дата: 2026-10-12. Local finished slice; whole #121 остаётся IN PROGRESS.

## Что было сделано

Перепроверены изменения плана в d2da970/d9c4535, оба DEVELOPMENT_PLAN, product review,
plan-refresh report и актуальные GitHub #107/#121/#120. Начальное состояние: mvp,
HEAD d9c4535820095d89ceb3f6233cea826a6e5c4843, working tree чистое. Чужие commits сохранены.
Обновлённый план отдельно выделяет AI Jobs, budgets/idempotency и полный Playbook; их реализация
не выдаётся за часть законченного #121. Следующий этап по плану — versioned re-review.

Новый явный review разрешён после parent context change для неотозванной TEXT sourceVersion.
Старые receipts неизменны. Replay exact tuple409. Отзыв любого receipt запрещает соответствующую
sourceVersion во всех contexts: rename/другой OWNER не возвращает разрешение. Regrant после
withdrawal не утверждён планом и этим этапом не реализован. До кода сохранён ADR0014.

## Как это работает

GET preview показывает exact source/Brand/Workspace versions и contentChecksum. Human review
подтверждается POST с этими tokens. Новый context создаёт новый UUID receipt, тот же tuple409.
GET snapshot сравнивает current versions/text/checksums/policy/lifecycle и проверяет withdrawal
всей sourceVersion. Новый review не переносит старое согласие автоматически. Source-edit API,
AI/citations и READY transition отсутствуют. Новая sourceVersion требует отдельного explicit
review current data; withdrawal прежней версии не переносится на неё автоматически.

## Архитектурное решение

V33 меняет UNIQUE на evidence_id/source_version/brand_version/workspace_version. Сохраняются
V31 validation/actor/time/checksum/lock guards; добавлены withdrawal sourceVersion и hard limit1000
reviews/evidence под Evidence lock. JPA/application queries отражают тот же contract.
Существующие runtime INSERT/SELECT и запрет UPDATE/DELETE не меняются. Flyway выполняет смену
constraint/guard транзакционно; старые migrations, rows, timestamps и provenance не переписываются.

Alternatives: auto-carry review между contexts не подтверждался человеком; mutable receipt
разрушал бы audit; regrant withdrawal требует отдельного product решения. Цена immutable tuple —
повторное хранение bounded text; предел1000 предотвращает бесконечное накопление копий на source.
Штатные optimistic versions монотонно увеличиваются; произвольный administrative rollback БД
не является этим API и требует отдельного recovery/data policy. Shared DB credentials не
подтверждают подлинность JWT actor при компрометации runtime identity.

## Security Review

Threat: stale/replay review либо fake consent в новом context.
Protection: exact current tokens/checksum, unique tuple, explicit human POST, immutable receipts.
Verification: H2/PG shared HTTP tests, source/parent mutation, stale old/current new snapshot,
duplicate context и populated V32 upgrade.

Threat: обход withdrawal через rename/другого actor/новый receipt, race с review.
Protection: query/SQL guard по evidence+sourceVersion, Account→Workspace→Brand→Evidence locks,
REQUIRES_NEW/READ_COMMITTED. Старый receipt может отозвать всю свою sourceVersion.
Verification: raw SQL admission после withdrawal, late withdrawal после нового review,
обе очередности PostgreSQL blocking races и existing runtime-role tests.

Threat: resource exhaustion через повторные parent updates/reviews.
Protection: MAX_REVIEWS_PER_EVIDENCE=1000, application count и PostgreSQL count под source lock,
tuple unique index и20k UTF-16 text bound; queries не загружают всю history.
Verification: actual1000 receipts на PG;1001-й context отклонён application и SQL.

Threat: IDOR, revoked membership, mass assignment, injection, sensitive logging/exposure.
Protection: прежние OWNER/actual account/membership checks, scoped lookups и immutable tenant
parents, DTO tokens/server actor/Clock, parameterized queries, no-store/safe errors.
Verification: полный прежний security suite и OpenAPI assertions. SQL fixtures тоже используют
prepared parameters. Новые dependencies, external I/O, cookie auth или SecurityFilterChain
не добавлены. Текст остаётся недоверенными данными; AI adapter/escaped frontend отдельно.

## Изменённые файлы

Application paths относительно src/main/java/com/cadence:

- domain/EvidenceAdmission.java — context unique tuple и hard history bound.
- application/repositories/EvidenceAdmissionRepository.java — existsForContext/countForEvidence.
- application/repositories/EvidenceAdmissionWithdrawalRepository.java — existsForSourceVersion.
- application/usecases/ReviewEvidenceUseCase.java — new context admission, withdrawal scope, cap.

Configuration/contracts:

- src/main/resources/db/migration/V33__versioned_evidence_review.sql — новый unique/insert guard;
  V1–V32 и runtime grants не менялись.
- src/main/resources/openapi/cadence.json — уточнение existing route lifecycle; новых routes нет.

Tests относительно src/test/java/com/cadence:

- support/EvidenceAdmissionApiContract.java — shared re-review/source/withdrawal scenarios и
  database-authoritative Clock fixtures, соответствующие production registration.
- persistence/PostgreSqlEvidenceAdmissionTest.java — raw SQL/races/1000-review bound.
- persistence/PostgreSqlEvidenceAdmissionMigrationTest.java — populated V32 active/withdrawn upgrades.
- persistence/PostgreSqlPersistenceTest.java — строгая inventory расширена до V33.

Documentation: DEVELOPMENT_PLAN.md, DEVELOPMENT_PLAN_RU.md, docs/evidence-admission-api.md,
docs/adr/0013-evidence-admission.md, новый docs/adr/0014-versioned-text-re-review.md,
этот отчёт, docs/verification.md и docs/issue-sync.md. Обновлены текущие plan sections,
исторические slice/review reports остаются snapshots прежнего состояния.

## Runtime Flow

Bearer/OWNER Controller → ReviewEvidenceUseCase → scoped Evidence lookup → Account lock →
Workspace lock → Brand lock → Evidence lock → actual account/membership → current preview
tokens → sourceVersion withdrawal check → context replay check →1000 cap → immutable receipt
INSERT → V33 invoker guard/UNIQUE → DTO201. Snapshot проходит те же authorization locks и
currentness/withdrawal checks. External model call отсутствует.

## Тесты

Финальная команда:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
```

BUILD SUCCESS,2026-10-12T03:43:19+01:00,03:40 min.101 suites,852 tests,0 failures/errors,
3 Windows filesystem skips;849 executed Maven tests прошли. Linux35/35 без skips закрывает
Windows symlink scenarios;32 Linux cases дублируются, их не складываем с852.
H2 admission14/14,PostgreSQL admission27/27,migrations4/4. Spotless/compilation/JAR/JaCoCo
passed: LINE5052 covered,299 missed,94.41%.

Дополнительные фактически выполненные команды:

```powershell
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\withdrawal-startup.ps1
git diff --check
```

Python10/10,renderer29/29,Linux35/35; mandatory PostgreSQL/MinIO/ClamAV/renderer/full evidence
pipeline report gates PASS без required skips/errors. Packaged local health200,anonymous
preview/admit/snapshot/withdrawals401. Ignored startup launcher ограничен loopback и завершает
только собственный JAR process. Существующие JDK CDS/deprecated S3/unchecked test/Hikari closed
disposable connection warnings не подавлялись; ошибок финального suite нет.

Targeted команды:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceAdmissionApiIntegrationTest,PostgreSqlEvidenceAdmissionTest,PostgreSqlPersistenceTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=EvidenceAdmissionApiIntegrationTest,PostgreSqlEvidenceAdmissionTest'
```

Первый targeted run:45 tests,0 failures,12 errors,0 skips. Повторный H2/PG run41/41 прошёл
без skips/errors; затем добавлены populated migration cases и выполнен полный suite выше.
Первый targeted run выявил fixture
clock mismatch: host Instant.now для capturedAt не совпадал с Clock БД для reviewedAt.
Production RegisterBrandEvidenceUseCase уже использует Clock БД; shared fixture приведена к
этому механизму, domain/SQL time assertions не ослаблялись. Production time code не менялся.
Docs checks через `python -B -`: совпадают continuation и re-review sections обоих планов,
Task IDs уникальны;67 относительных ссылок существуют. `git diff --check` прошёл.

## Security Tests и Edge Cases

Exact tuple replay; stale preview; parent change с sourceVersion прежней; current new/old stale
receipts; late withdrawal stale receipt; rename и другой OWNER после withdrawal; новая source
revision требует explicit new text/checksum review; original receipt timestamps/text сохранены;
raw SQL bypass; both concurrent orderings;1000/1001 history boundary; populated migration active
и withdrawn receipts. Existing anonymous/roles/membership/actor/scope/foreign/archive/SQL audit/
runtime grants и tenant-parent immutability tests повторяются без отключения.

## Непроверенные аспекты / Production considerations

Нет real AI/live Clerk, batch source selection/citations/publication fencing, non-TEXT admission
и approved Identity→Strategy. Snapshot может устареть после выдачи: future consumer обязан recheck
source/withdrawal/permissions перед publication после external I/O. Regrant withdrawn sourceVersion
требует отдельного решения пользователя, public source editing не реализован. Rate/tenant quotas,
retention/privacy policy и general audit recovery нужны перед production; hard1000 ceiling не
заменяет эти policies. Remote exact-SHA CI, свежие OSV/SAST/container scans и hosted AppArmor/live
IAM/TLS qualification этим local run не подтверждаются. #120 остаётся отдельным provider scope.

## Что мне важно понять

Review подтверждает и текст, и контекст, в котором его рассматривали. Новая версия Brand не
должна автоматически наследовать подтверждение. Отзыв — запрет использования версии источника;
его нельзя обходить созданием receipt в другом context. Поэтому replay и re-review различаются
exact version tuple, а withdrawal проверяется шире — по sourceVersion.

## Что мне стоит изучить

1. Составной уникальный ключ (composite unique key) — отличает новый context от replay.
2. Append-only provenance — новые факты не переписывают прошлые review/withdrawal receipts.
3. Гонка проверки/использования (TOCTOU) — external AI I/O требует publication recheck.
4. Database-authoritative clock — связанные timestamps используют один источник времени.
5. Defense in depth — application и PG guard сохраняют одинаковые lifecycle/limit invariants.

## Факты, выводы, assumptions и Git

Факты: commands/results выше, реальная PG concurrency/runtime/SQL/history-bound qualification,
populated upgrade и startup. Инженерный вывод: разрешение нового неотозванного
context устраняет тупик parent changes, сохраняя deny-by-default после withdrawal. Assumptions:
regrant этой sourceVersion не разрешён; source editing/recovery policies определяются отдельно.
Готовый результат сохраняется отдельным local commit `feat: review evidence in new version contexts (#121)`;
#121/#107 синхронизируются с SHA/tests,
issue остаётся open. Push/exact-SHA CI после whole issue, merge/deployment отдельно.
