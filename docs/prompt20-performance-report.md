# Задание20 — actual performance formulas / bounded analytics

Baseline `27ca07c097fb00ec099d85e51c46c16b38b717ec`, branch `mvp`, initial working tree clean.
Дата начала2026-10-16. Prerequisite19 verified actual code/ADR0036/local commit/report;
14–19 не реализовывались повторно. Actual GitHub #104/#107 прочитаны16 октября.
Статус: **QUALIFIED LOCAL**, qualification завершена2026-10-17. Ниже фактически выполненные checks.

## Что сделано / как работает

Добавлены четыре показателя: engagement/reach, saves/reach, profile→site clicks/reach и
signups/clicks. Каждый процент — отношение сумм числителя и знаменателя по записям, где
известны оба значения. Это позволяет проверить результат вручную и не подменять пропуски нулями.
Backend группирует данные по platform, историческому pillar/format и точному временному
окну DAY/PERIOD. Отсутствующие, устаревшие, исправленные и несопоставимые данные отражены явно.
DTO содержит происхождение данных (provenance) и сведения о полноте выборки (coverage),
чтобы будущий UI мог объяснить каждое число.

Отдельная неизменяемая история классификации (MetricAttribution) связывает observation с
конкретной исторической APPROVED Post revision и pillar. Это решает обнаруженный пробел
задания19: постоянный Post ID сам по себе не сообщает, к какой версии контента относятся
метрики. Старым записям контекст не приписывается: UNKNOWN остаётся UNKNOWN. Ошибочную
классификацию можно исправить новой attribution version, сохранив прежние counters, source
time и историю. Исправление самих counters требует отдельного подтверждения контекста.

## Архитектурное решение

[ADR0037](adr/0037-performance-analytics.md), [API](performance-api.md).
Слои разделены: controller принимает DTO, защищённый usecase проверяет OWNER/scope,
application service организует транзакцию, domain рассчитывает формулы, reader port получает
ограниченную проекцию через parameterized JDBC/PostgreSQL. Отдельные JPA records хранят
attribution history. Новая migration V53 добавляет таблицу, SQL guards и индекс;
runtime permissions запрещают переписывать историю. Новых dependencies нет.

Выбрана явная классификация пользователем. Автоматический выбор текущей revision приписал
бы старым метрикам новый контент после редактирования Post. Цена выбранного решения —
дополнительное действие пользователя; преимущество — объяснимая история без выдуманного
provider output. Legacy PostMetric, checksums и receipts задания19 не переписаны.

DAY поддерживает дневной временной ряд, PERIOD — общее окно, например 7 или 28 дней.
Автоматическое суммирование daily reach могло бы повторно посчитать одни и те же accounts,
поэтому PERIOD принимает только явно измеренные period totals. Частичные, скользящие и
накопительные окна не преобразуются догадками. Для разрешения сравнения нужны минимум
5 Posts с положительным знаменателем и суммарно 100 reach либо 20 clicks. Это технический
порог для UI, а не доказательство статистической значимости. Порог устаревания 7 дней —
явная эвристика, которую потребуется откалибровать при подключении live provider.

SQL выбирает только нормализованные поля, без raw bytes и обхода графа JPA relationships.
Лимит — 1000 кандидатов; 1001-я запись вызывает отказ, а не частичный результат. После полного
ограниченного расчёта группы выдаются страницами. Одновременно разрешены 2 расчёта на instance;
statement timeout — 3 секунды, transaction timeout — 5 секунд. Блокировки Account→Workspace→Brand
согласуют чтение с импортами, corrections, archive и отзывом доступа; attribution дополнительно
блокирует Post. Цена — сериализация операций одного actor/Workspace/Brand, поэтому нужны
production load checks. Внешнего I/O внутри транзакции нет.

## Security Review

Threat: BOLA — подстановка чужого Brand/observation/revision/pillar; устаревшие role/membership
могли бы раскрыть бизнес-метрики другого tenant или испортить классификацию.
Protection: OWNER в controller/usecase, проверка действующих account/membership, блокировки
активных родителей, scoped IDs перед загрузкой Entity, связь revision с тем же Post и pillar
с исходной Strategy. SQL повторяет actor/context/history guards: обход application validation
не разрешает сохранить чужую связь. DTO не принимает actor/tenant/checksum/provider ownership.
Verification: HTTP/PostgreSQL owner/anonymous/viewer/foreign tenant/revision/pillar/stale version,
отзыв membership, archive, concurrent replay и разрешённые/запрещённые runtime role операции.

Threat: дублирование метрик, использование исправленного старого snapshot и подмена
исторического контента текущей revision дают правдоподобные, но неверные результаты.
Protection: latest correction выбирается до window filter, затем latest source snapshot для
Post/window; import/context history не умножает строки. Context versions и source links
неизменяемы; checksums позволяют объяснить конкретный набор входных данных.
Verification: независимо рассчитанные fixtures, повторные source times, correction с изменённым
окном,100 context versions→одна contribution, сохранение counters/time/history при correction.

Threat: division by zero, overflow, подмена missing нулём, сравнение несовместимых окон и
необоснованные статистические выводы.
Protection: ограниченные Long COUNT inputs, checked sums, BigDecimal, complete pairs,
явные issues/coverage/minimums и отдельные DAY/PERIOD/timezone/tzdb/platform/Strategy cohorts.
Verification: независимая арифметика, null/0/0/inconsistent/max10^12/1000 rows, sparse,
точная stale boundary, DST gap/fold/skipped date, разные namespace и timezone.

Threat: resource exhaustion через дорогие запросы, SQL injection и раскрытие raw/private данных.
Protection: allowlist dimensions/zone и bounds, parameterized fixed SQL, input cap до расчёта,
ограниченные pagination/concurrency/timeouts, нормализованная проекция, no-store DTO и safe503.
Verification:1001 реальных PostgreSQL observations→отказ, EXPLAIN ANALYZE/BUFFERS, capacity/bounds,
regression фактического JDBC timeout, OpenAPI contracts и inspection отсутствия raw bytea,
provider calls, secrets и пользовательского содержимого в новых logs.
Auth/SecurityFilterChain unchanged; explicit Bearer-only no-cookie contract adds no CSRF surface.
No new dependency/supply-chain expansion, no arbitrary URL/file/shell/model access.

## Изменённые файлы

Полный перечень с назначениями: [manifest20](prompt20-changed-files.md).

## Runtime Flow

```text
Bearer → PerformanceController → PerformanceUseCase OWNER/scope
 → capacity → actual Account/Workspace/Brand locks + active checks
 → scalar normalized SQL (latest correction/context,1001limit,3stimeout)
 → exact windows/latest source dedup → bounded groups → checked per-pair formulas
 → explicit coverage/provenance/no-store DTO → commit/release slots

Attribution PUT → scoped observation target → parent/Post locks → actual approved revision/pillar
 → expected context version/current corrected observation → immutable new context + SQL guard
 → audit DTO; history GET/current analytics resolve exact attribution version
```

## Тесты / результаты

Фактические предварительные commands:

```text
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceMetricsTest,PerformanceApiIntegrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceMetricsTest,PerformanceApiIntegrationTest,PostgreSqlPerformanceTest,PostgreSqlPerformanceMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,PostgreSqlMetricImportMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceMetricsTest,PerformanceApiIntegrationTest,PostgreSqlPerformanceTest,PostgreSqlPerformanceMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceMetricsTest,PerformanceApiIntegrationTest,PostgreSqlPerformanceTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceMetricsTest,PerformanceCalculatorTest,PerformanceApiIntegrationTest,PostgreSqlPerformanceTest,PostgreSqlPerformanceMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,OpenApiContractTest' test
```

Compilation PASS. Первое formatting/compile attempt обнаружило invalid compound `var` в test
fixture, исправлен синтаксис. Initial39test run:1failure auth fixture,38pass; expected401 не
ослаблялся, явный anonymous request устранил оставшийся MockMvc auth context и обязательные
query params заданы для role tests. Следующие runs83/83,79/79,81/81. Последний targeted run:
96 tests, 0 failures/errors/skips, BUILD SUCCESS (2026-10-16T23:49:25+01:00).
Full qualification выполняется отдельно; preliminary targeted results её не заменяют.

Первый full run завершился17 октября в00:09:09+01:00:169 suites,1981 tests,
0 failures/errors,4 Windows filesystem skips,16:50 min. Его исходники были зафиксированы
693 hashes. После review сделаны два улучшения, поэтому этот run не объявляется последним:

* SQL negative fixture теперь задаёт все обязательные поля и проверяет именно SQLSTATE23514
  и сообщение source/actor guard для foreign revision. Раньше INSERT мог падать на NOT NULL.
* Regression `PerformanceReadRepositoryTest` на реальном `JdbcTemplate` и JDBC test doubles
  воспроизвёл переопределение statement timeout: expected3, actual5 (3 cases,1failure).
  Binding callback теперь устанавливает `min(3, remaining transaction timeout)` после настроек
  Spring. Проверяются отсутствие transaction deadline, короткий1s и более длинный5s deadlines.

Первый compile attempt исправления использовал отсутствующий overload JdbcTemplate;
заменён существующим `query(String, PreparedStatementSetter, RowMapper)`, assertions сохранены.
Для окончательных исходников выполнен повторный full run; результаты приведены ниже.

После исправления: `PerformanceReadRepositoryTest`3/3 и `PostgreSqlPerformanceTest`39/39,
итого42/42,0 failures/errors/skips (00:12:05+01:00). Assertions не ослаблялись.

Окончательные qualification commands:

```text
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PerformanceReadRepositoryTest,PostgreSqlPerformanceTest' test
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt20-current-bom.cdx.json --all-packages --format=json --output-file=var/prompt20-verification/osv-results.json
python -B scripts/verify_dependency_scan.py var/prompt20-current-bom.cdx.json var/prompt20-verification/osv-results.json
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt20-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
docker build --tag cadence-prompt20-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt20-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\scripts\run_linux_storage_tests.ps1
```

Python report tests10/10, renderer29/29, Linux filesystem37/37 (0 skips) — PASS.
Свежий SBOM построен из окончательного source graph до повторного clean verify и сохранён
в `var/prompt20-current-bom.cdx.json`, чтобы clean не удалил вход OSV. Scan завершился
успешно:194 Maven dependencies,0 OSV vulnerabilities; verifier проверил полноту scan.
SHA256 существующего osv-scanner2.5.1:
`25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.
SBOM и scan не содержат prompts, пользовательские imports или credentials.

Финальный full run: **BUILD SUCCESS**, 170 suites,1984 tests,
**0 failures/errors**,4 Windows filesystem skips,1980 выполненных tests PASS.
Завершён `2026-10-17T00:28:54+01:00`, duration `16:14 min`. Четыре skips относятся только к symlink
сценариям `FileSystemEvidenceObjectStorageTest`; Linux run37/37 проверил их без skips.
Performance suites в этом full run: HTTP32/32, calculator3/3, formulas9/9,
JDBC timeout3/3, PostgreSQL39/39, migration1/1. AI crash/restart child-JVM8/8 также PASS.

После full run выполнены:

```text
python -B var/prompt20-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\var\prompt20-startup.ps1
python -B var/prompt20-integrity.py
python -B var/prompt20-manifest.py
python -B var/prompt20-doc-check.py
git diff --check
```

Mandatory PostgreSQL/evidence report gates PASS; Spotless и JaCoCo thresholds PASS.
JaCoCo: line94.5%, branch71.82%.
Packaged JAR: health200,29 защищённых операций401 без authentication,
discovery401 с фиксированным Bearer challenge; own process остановлен, port18100 освобождён.
Это local startup qualification, а не проверка production Clerk/IAM/deployment.

694 source/build hashes не изменялись на протяжении окончательного
full run и startup; прежние OpenAPI operations/schemas семантически сохранены.
Все 589 compiled classes/resources совпали с BOOT-INF/classes
packaged JAR; test-only AiRecoveryProcess launcher в JAR отсутствует.
JAR SHA256: `c7cce1285c147365d05656a27904f619088d69893a27c304a2068682aa03e930`.

Actual PostgreSQL EXPLAIN (ANALYZE,BUFFERS,FORMAT JSON):1001 rows,
execution time `4.818ms`; observed indexes:
`brands_identity_workspace_unique, metric_imports_pkey, metric_observations_post_id_observed_at_revision_number_key`. Проверено наличие нового
`metric_observation_window_idx`; planner не принуждался использовать его на маленькой fixture.
Это проверка формы query/indexes и ограниченного результата, не production p95 benchmark.

Warnings: прежние deprecated JSON2/S3 APIs, Mockito JVM agent, Hibernate informational
messages, Hikari после остановки Testcontainers и ожидаемые SQL/security negative messages.
CycloneDX сообщил schema warnings `meta:enum`/`deprecated`; SBOM generation и отдельный
dependency scan verifier завершились успешно. Новых dependencies/security suppressions нет.

## Security Tests / Edge Cases

Проверяются реальные HTTP/PG foreign tenant/Post revision/pillar, role/actor fields, version
conflicts, concurrent attribution replay, immutable SQL history/runtime column grants;
empty/missing/known zero/inconsistent/sparse/above100/maxcounts, pairwise coverage, independent
weighted arithmetic (25/150×100=16.6667, не16average), format/pillar source history, snapshots/
corrections, annotation history100versions/no row multiplication, DAY/PERIOD mismatch,
timezone bounds/DST23/25h/skipped day, candidate1001/page limits и parser-free safe failures.

## Actual #104 acceptance

| Требование actual issue | Реализация / verification |
| --- | --- |
| Authorized Workspace/Brand |current OWNER/membership, scoped scalar query, foreign/PG negatives|
| Validated time ranges/pagination |PerformanceQuery/DAY/PERIOD bounds, API/domain tests|
| Formulas/sample limitations |ADR0037, pure checked PerformanceMetrics, independent fixtures|
| No provider payload leak |only normalized scalar DTO/source IDs/checksums, no raw bytes/provider I/O|
| UTC timestamps |Instant window/source/audit, named view timezone separate, DST tests|
| Explicit missing/stale |nullable rates/pair coverage/issues, generatedAt and source times|
| Auth/cross-Workspace/empty tests |shared H2/PG HTTP suite, runtime/current membership checks|

## Непроверенные аспекты / Production considerations

Live synchronization #99, live platform credentials/mappings/methodology и AI adapters не
реализовывались. Human values не доказывают actual platform performance/publication. No causal
funnel/statistical significance. Exposure age неизвестен. Intraday/lifetime cumulative или
arbitrary overlapping window rollups unsupported; input coverage явно сообщает исключения.

Production-size100000rows/load/lock distribution/p95 не доказаны1001row fixture. Нужны отдельные
gateway/user rate policy, DB capacity/statement cancellation/load qualification, IAM/Clerk,
backups/restore/retention/deployment. Read calculations не durable historical report22: snapshot
checksum/asOf/source versions дают explainability текущего ответа, не бессрочное хранилище.
Pagination повторно вычисляет current snapshot; перед изменениями client сверяет response
source/context versions, historical presentation использует сохранённые provenance/asOf.
App/DB clocks должны быть синхронизированы: audit guards используют database time, а stale
policy — server Clock. Существенный clock skew должен приводить к диагностируемому отказу,
а не к подделке source/audit timestamps; production clock/latency qualification отдельно.

## Что мне важно понять / Что изучить

1. **Отношение сумм (weighted ratio):**25interactions/150reach даёт16.6667%, а среднее perPost
percentages дало бы16%. Метрика и complete-pair denominator должны быть определены явно.
2. **Сопоставимость (comparability):** одинаковое название counter не делает weekly и daily,
разные платформы или23/25h windows одинаковыми; comparison keys отражают ограничения.
3. **Атрибуция (data attribution):** stable Post не сообщает content revision прошлого
measurement. Новый audited human context сохраняет честную provenance без rewriting source.
4. **Ограниченный scalar read (bounded projection):** database отдаёт только нужные columns,
а1001st candidate вызывает отказ; пагинация результата не скрывает input truncation.
5. **Неизменяемая история (append-only lineage):** counter correction и context correction —
разные действия. Context version исправляет классификацию, сохраняя counts/source time.

## Git / факты, выводы, assumptions

Local commit после actual qualification; push/remote edits/CI/closure/merge/deploy не выполнены.
#104 локальные AC сопоставлены выше, внешнее closure не объявляется. #99/#121/#120 остаются OPEN;
21+ не начинались. Подтверждённые facts — выполненные commands/results выше; remaining production gates не объявляются пройденными.
Инженерный вывод — explicit context и exact bounded cohorts предотвращают silent rewriting/
misleading aggregates. Assumptions — human inputs не verified provider truth; min samples и
freshness heuristic требуют отдельной business/live calibration, не подменяют measured claims.


Local commit subject: `feat(analytics): qualify bounded performance insights`.
Точный source SHA сохранён Git; проверить: `git log -1 --format=fuller`.
Фактические commit SHA и итоговый working-tree status приведены в сообщении завершения.
