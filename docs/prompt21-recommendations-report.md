# Задание21 — evidence-linked recommendations

Baseline `dabc3672a5c98e6dfd572adb6fa19d15fefdf192`, branch `mvp`, initial working tree clean.
Дата2026-10-17. Prerequisites13/19/20 сверены по code/ADR/reports; actual #100/#105/#107 прочитаны.
Статус: **21 — QUALIFIED LOCAL**. Implementation, security review и required local gates завершены.

## Что сделано / как работает

Метрики превращаются в проверяемую гипотезу, которую пользователь может применить к новой
редактируемой версии Strategy. Backend не меняет approved content автоматически.
Новая модель RecommendationInsight сохраняет снимок входных данных (immutable capture),
supporting observations/context versions/checksums/time и ограниченный proposed patch.
Confidence явно UNCALIBRATED; вероятности успеха и expected causal lift отсутствуют.
Origin HEURISTIC/rule version/provider фиксируются в history. Legacy Recommendation не backfill.

Контракт и пояснения: [ADR0038](adr/0038-evidence-linked-recommendations.md),
[API/recovery](recommendations-api.md). Metrics/Playbook09–20 не реализованы повторно.

## Архитектурное решение

Controller/strict DTO → OWNER/scope usecase → short parent-locked application transactions →
existing bounded performance reader/calculator → immutable normalized capture → minimal typed
RecommendationInput/engine вне locks → exact current capture recheck → atomic insight/links.
Apply использует существующий Strategy.createDraftVersion/replacePlaybook/recordHumanEdit;
immutable resolution связывает initial draft checksum и исходную recommendation.

Выбран pure local rule с typed action вместо free-form model commands. Это поддерживаемый
provider-independent scenario, не live AI adapter. Future external activation требует Jobs/
budget/provider/privacy qualification. Не создаются fictitious cost/usage/moderation records.
Provider failure/timeout не портят prior history; admission key binding при этом сохраняется.
Вместо численной «вероятности» выбран explicit uncalibrated confidence. Цена — ограниченный
набор действий и отсутствие автоматических обещаний growth; преимущество — проверяемая семантика.

V54 additive: key bindings, insights, support links и resolutions, независимые FK/check/SQL
guards/deferred seals/runtime grants. Прежние migration checksums не меняются. Нет dependencies.
List summary отделён от bounded detail, чтобы пагинация не тиражировала полные snapshots.

## Security Review

Threat: BOLA, forged actor/tenant/source/approval fields и stale membership.
Protection: strict DTO allowlist, OWNER/usecase, actual Account→Workspace→Brand→Strategy locks,
scoped lookup, current membership/active parents; PostgreSQL повторяет actor/source guards.
Verification: HTTP foreign/role/unknown fields/not found, SQL/runtime positives/negatives,
revocation/archival и concurrency tests. Обход application validation не разрешает SQL
foreign supporting links или mutation immutable history.

Threat: invented citations, stale/corrected observations и ложные causal claims.
Protection: server-selected exact scoped inputs; minimum sample/window/freshness/attribution;
typed proposal только из captured evidence. Free-form prose не принимается. HYPOTHESIS и
UNCALIBRATED не маскируются под measured live inference. Full provenance остаётся server-side;
engine получает только counters/weights/window/citation selectors.
Verification: forged IDs, missing/sparse/zero/stale/context correction, inherited TEXT withdrawal,
Identity changes, field/DTO contracts и independent SQL incomplete/wrong-link checks.

Threat: duplicate apply, race с archive/access/source changes, late output, resource exhaustion.
Protection: durable scoped key/payload binding, root/resolution UNIQUE, fresh preflight/completion,
existing physical verification вне locks, one resolution и new DRAFT lineage; caps/timeout/
bounded executor, no external paid calls. Даже потерявший гонку caller может только прочитать
уже существующий identical authorized receipt, не публиковать новый результат.
Verification: PostgreSQL concurrent generation/apply, revoked access during call, source lock,
provider timeout/failure, same-key conflicting payload и failed-key retention.

Threat: SQL/command/URL injection и утечки raw/provider/secrets.
Protection: parameterized queries, static allowlists; SQL dynamic identifiers только из
фиксированного internal table list для comparison. Нет model tools/fetch/shell, raw import
serialization, новых payload logs или dependencies. Bearer-only SecurityFilterChain unchanged.
Verification: DTO/engine input inspection, HTTP/OpenAPI tests, SQL/runtime denied writes и
dependency/report/full gates ниже. Production rate/load/IAM qualification отдельно.

## Изменённые файлы

Полный перечень всех 46 файлов и назначение каждого изменения:
[manifest](prompt21-changed-files.md). Основные компоненты: RecommendationService —
orchestration и publication/apply, RecommendationSources — current source/metrics capture,
RecommendationProposal — domain patch, RuleRecommendationEngine — independent arithmetic,
V54 — PostgreSQL integrity и immutable history. Existing security configuration и dependencies
не менялись; V1–V53 сохранены.

## Runtime Flow

```text
POST + authenticated OWNER + Idempotency-Key
 → current resource authorization → immutable admission binding
 → parent/source locks + exact metrics selection → release transactions
 → retained physical verification → minimal typed local rule input, bounded wait
 → current metrics/source/physical/access rechecks
 → immutable recommendation + support links + deferred SQL seal → no-store DTO

Apply → fresh authorization/key binding → same captured source recheck
 → new Strategy DRAFT + five-point patch + human audit
 → immutable resolution receipt → commit both atomically
```

## Тесты / фактические результаты

Compilation/Spotless PASS. Initial compile fixture ссылался на отсутствующий platform() у
PerformanceContribution; исправлен выбор platform из captured query. Первый flow run выявил
detached Entity assertion; он заменён scoped DTO read с теми же60/40 assertions и дополнительным
55/45 draft assertion. Targeted93: H245/45,PG47/48; real same-key apply race исправлена без
ослабления test. Следующий targeted93/93 PASS. Expanded124:123 PASS,1 historical V52→V53
migration boundary failure; explicit target53 сохраняет прежнюю проверку, V53→54 отдельная.
Expanded H251/51,PG57/57 (runtime и generated Identity/TEXT withdrawal), new migration1/1 PASS.
Окончательный targeted run: **133 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
H2 RecommendationApiIntegrationTest:53; PostgreSqlRecommendationTest:61;
RecommendationProposalTest:2; RuleRecommendationEngineTest:3; recommendation migration:1;
historical performance migration:1; persistence:8; UTC migration:1; OpenAPI:3.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=RecommendationApiIntegrationTest,PostgreSqlRecommendationTest,RecommendationProposalTest,RuleRecommendationEngineTest,PostgreSqlRecommendationMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,PostgreSqlPerformanceMigrationTest,OpenApiContractTest' test
```

Дополнительные фактически выполненные gates:

```powershell
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt21-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt21-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt21-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt21-current-bom.cdx.json --all-packages --format=json --output-file=var/prompt21-verification/osv-results.json
python -B scripts/verify_dependency_scan.py var/prompt21-current-bom.cdx.json var/prompt21-verification/osv-results.json
```

Python report tests: **10/10**. Linux filesystem: **37/37, 0 skipped**. Isolated renderer:
**29/29**. Fresh SBOM: **194 Maven dependencies; 0 reported OSV vulnerabilities**.
Scanner2.5.1 executable SHA256:
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
CycloneDX сообщил schema keywords `meta:enum`/`deprecated` warnings; BUILD SUCCESS,
SBOM parsed и полный dependency inventory проверен отдельным gate.

Source/build freeze: **722 files**. До full run проверено семантическое равенство всех
существовавших OpenAPI operations/schemas с HEAD; новые routes/schemas добавлены отдельно.
Окончательный full run: **BUILD SUCCESS**, **175 suites, 2104 tests,
0 failures, 0 errors**, 4 Windows filesystem skips; **2100 выполненных tests PASS**.
Завершён `2026-10-17T02:09:14+01:00`, duration `17:48 min`. Четыре Windows skips относятся только к
FileSystemEvidenceObjectStorageTest; отдельный Linux run37/37 выполнил их без skips.
Recommendation suites в full run: H2 API53/53, PostgreSQL61/61, domain2/2, rule3/3,
V53→V54 migration1/1. Existing AI crash/restart child-JVM8/8 также PASS.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
python -B var/prompt21-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\var\prompt21-startup.ps1
python -B var/prompt21-integrity.py
python -B var/prompt21-manifest.py
python -B var/prompt21-doc-check.py
git diff --check
```

Mandatory PostgreSQL/evidence report gates PASS: required PostgreSQL, MinIO, ClamAV,
renderer/full pipeline suites действительно выполнены, без skips/failures.
Spotless и JaCoCo thresholds PASS; actual line coverage **94.36%**, branch **71.4%**.
Packaged application: health200, **34 защищённых операций401** для anonymous request,
discovery401 с фиксированным Bearer challenge. Проверенный own process остановлен,
port18100 освобождён. Это local startup qualification, не live Clerk/deployment proof.

**722 source/build hashes** не изменились за full run/startup. Все **619
compiled classes/resources** совпали с BOOT-INF/classes packaged JAR; test-only
AiRecoveryProcess launcher в JAR отсутствует. JAR SHA256:
`7684f1251334ac2d1e79f4c7d0a5335860a61d446bb244277a96ccc4521d03d2`.
Surefire/JaCoCo/SBOM/OSV receipts сохранены в ignored `var/prompt21-verification`.
Reports/манифест проверены отдельно, так как docs не входят в source/build freeze.

Warnings: существующие deprecated JSON2/S3 APIs, unchecked test code, JVM/Mockito/Hibernate
informational messages и Hikari retries после остановки соответствующих Testcontainers.
Ожидаемые SQL/security negatives не являются test failures. CycloneDX schema warnings
описаны выше. Dependencies, suppressions и SecurityFilterChain не изменялись.

## Security Tests / Edge Cases

OWNER/anonymous/viewer/foreign IDs, unknown fields, malformed keys/pagination, stale source
versions/lock/new Strategy branch, latest context/counter correction, stale/missing/zero/sparse
metrics, forged citations/pretend-live attribution, minimal engine payload, timeout/failure,
same key/collision/concurrent apply, immutable SQL history/runtime privileges/deferred samples.

Два direct SQL bypass tests используют valid capture и FK-valid IDs. Отсутствие supporting
links и подмена attribution от другой observation завершаются SQLSTATE23514 именно в deferred
evidence guards; transaction rollback не оставляет partial recommendation. Runtime positive
generate/apply проверяется под отдельной runtime role, а не только migration owner.

Минимальная выборка проверяется для каждого pillar отдельно:4/6 Posts не заменяют5/5.
Reach ровно100 и difference ровно2pp допустимы; zero/missing/stale запрещены. Weighted fixture
независимо вычисляет25/150=16.6667% против20%; намеренно неверный candidate direction во входе
не заставляет engine повторить его. Engine действительно вычисляет результат, а server
повторно сверяет его с captured metrics. Поздний/поддельный output не получает права записи.

## Actual issue mapping

| Acceptance | Реализация |
| --- | --- |
| #100 correct Brand | scoped requests/capture/current SQL guards |
| Traceable source/time | immutable normalized capture/support/context/actor/time/checksums |
| Idempotent or versioned runs | admission mapping + one result/resolution + new Strategy revision |
| Provider failures preserve history | no partial publication, bounded call and fresh completion |
| Unit/persistence flow | domain/shared HTTP/PostgreSQL suites below |
| #105 recommendation API subset | scoped list/detail/apply/dismiss, provenance/DTO/negative tests |
| #105 report Job/download | NOT DELIVERED: separate task22 |

## Непроверенные аспекты / Production considerations

Real AI/analytics providers, calibrated confidence, causal lift, arbitrary actions, cross-window/
platform/Strategy comparisons и report22 не заявляются. Current rule не production AI.
Для future live adapter нужны privacy/processor/retention/cost decisions и durable Job/budget
integration до activation; synchronous pure-rule retry нельзя переносить на uncertain spend.
Production load, gateway quotas, app/DB clock synchronization, IAM/Clerk/TLS, backup/restore,
deployment и real provider truth требуют отдельной qualification. Local tests этого не доказывают.

## Что мне важно понять / Что изучить

1. **Снимок данных (immutable snapshot):** объясняет, какие конкретно observations привели к
   совету; later correction не переписывает прежнюю историю.
2. **Привязка повторного запроса (idempotency binding):** одинаковый key не даёт повторно
   применить patch или заменить payload после failure; operation scope включает actor/tenant.
3. **Ограждение публикации (publication fencing):** результат call полезен только после fresh
   source/access checks; устаревший output не получает права записи.
4. **Неопределённость (uncertainty):** достаточная выборка разрешает описать разницу, но не
   доказывает причинный эффект изменения weights и не даёт calibrated probability.
5. **Отложенные ограничения (deferred constraints):** PostgreSQL проверяет полный набор links
   и draft collections при commit, когда JPA закончила записывать связанные строки.

## Git / facts / выводы / assumptions

Проверенный slice сохранён локальным commit `feat(recommendations): qualify evidence-linked Strategy drafts`; фактический SHA указан в итоговом ответе и доступен через `git log -1`. Push/remote CI/issue updates/closure/merge/deployment не
выполнялись. #100 и recommendation subset #105 сопоставлены выше; whole #105 остаётся unfinished.
Task22 не начинался. Факты — только выполненные inspection/commands/results; инженерный вывод —
fixed typed hypothesis + exact evidence + new DRAFT предотвращают silent content mutation.
Assumptions: human inputs не platform truth, rule thresholds требуют live/product calibration,
а successful local gates не означают commercial deployment readiness.

## Практический пример и граница интерпретации

Если у двух pillars одной Strategy есть по5 сопоставимых Posts за одно exact окно,
backend сравнивает отношение **суммы engagements к сумме reach**, а не среднее процентов
отдельных Posts. Разница от2 percentage points разрешает только гипотезу: проверить перенос
5 points weight к pillar с более высоким наблюдаемым отношением. Это не обещание, что перенос
вызовет рост: тема, возраст публикаций, состав аудитории и другие факторы не контролируются.
Поэтому response содержит HYPOTHESIS, UNCALIBRATED и coverage/uncertainty caveats, а не
выдуманную вероятность успеха. Manual/CSV observations также не становятся verified platform
truth только потому, что прошли parser и database validation.

Apply в fixture меняет60/40 на55/45 **в новой Strategy revision**. Approved60/40 сохраняется,
новая55/45 требует human review и отдельного approval. Дальнейшие human edits допустимы;
receipt хранит checksum первоначального результата apply, а не checksum всех будущих edits.
Повтор запроса не должен внезапно создать ещё одну revision или отменить правки человека.

При выкатке V54 сначала применяется обычный reviewed Flyway/runtime-grants pipeline и
проверяется startup с Hibernate validate. Rollback приложения не требует удаления legacy
или новой audit history; разрушительное удаление таблиц/receipts не является recovery.
Реальные backup/restore, retention cleanup, multi-instance нагрузка и clock skew должны
пройти отдельную эксплуатационную проверку перед коммерческим запуском.
