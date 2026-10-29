# Исправления architecture и application security review

База изменений: `90686e5c9c41cc776632b368fb7f2189eaa56804`. Работа выполнена по
последующему запросу на исправления; первоначальный read-only review не менял код.

## Что было сделано

Исправлены шесть подтверждённых проблем review: переход URL Evidence в FAILED,
обход квот через создание Workspace, upgrade populated V11–V13, гонка Brand с
архивацией Workspace, несовпадение TEXT/social contract и завершение устаревшей
попытки Job. Дополнительно задан явный порядок блокировок evidence workers.

## Как это работает

URL сохраняет extraction provenance даже при INFECTED/неустранимой ошибке. Evidence
переходит в FAILED, Job — в DEAD; следующий Job продолжает выполняться. Это не
превращает FAILED Evidence в одобренный источник. Проверки CLEAN scan и provenance
сохраняются.

Создание Workspace блокирует строку account, перечитывает число memberships и
возвращает 429 при исчерпанной квоте. Архивные Workspace учитываются. По умолчанию
API разрешает 3 Workspace на account; настройка допускает 1–10. PostgreSQL
дополнительно ограничивает рост memberships до 10 атомарным счётчиком, который
откатывается вместе с membership transaction. Прямая подмена счётчика запрещена.
Identity bootstrap отвергает claims с более чем 10 assignments. Существующие
accounts сверх лимита сохраняют данные и доступ, но дальнейший рост блокируется.

Brand create/update сначала блокирует Workspace и только затем проверяет ACTIVE.
Если архивация завершилась первой, операция отвергается. Работники получают locks
в порядке Job → Workspace → Brand → Evidence. URL candidate query использует
EXISTS: чтение eligibility больше не блокирует Evidence раньше Workspace.

Job completion требует совпадения worker, номера попытки и неистёкшего lease.
DurableJobQueue блокирует Job и берёт время из Clock приложения; production Clock
использует PostgreSQL. Один и тот же worker name после redelivery не даёт старому
callback права завершить новую попытку.

## Архитектурное решение

Существующие domain/application/presentation/infrastructure boundaries сохранены.
Domain задаёт допустимые переходы и размеры; application организует ownership,
quota и transaction; presentation преобразует quota rejection в безопасный 429;
PostgreSQL защищает размеры, provenance и account budget независимо от DTO.
Новых dependencies нет.

Расширен общий source_reference column до 20 000, но type-dependent CHECK оставляет
URL/FILE references на 4096. Это сохраняет единый Evidence model и соответствует
уже опубликованному TEXT/provider contract. Отдельная TEXT-таблица для исправления
этого дефекта не требуется.

Checksums V1–V24 сохранены. Изменение V14 задним числом нарушило бы Flyway validation
на уже обновлённых базах; вместо этого migration-only callback временно отключает
ровно один append-only trigger внутри транзакции V14. Exclusive table lock и
transaction rollback не дают writer увидеть незащищённое состояние. Для standalone
Flyway Java необходимо явно зарегистрировать callback; подробности в
[migration runbook](database-migrations.md).

Лимит account выбран вместо новой billing/ownership subsystem. Это ограничивает
ресурсный бюджет одного account, но не является общей защитой от массового создания
разных аккаунтов. Trusted provisioning может выдать до 10 memberships, даже если
API limit ниже. Изменение коммерческой модели квот требует отдельного решения.

## Security Review

| Threat | Protection | Verification |
|---|---|---|
| Poison Job с extracted URL, мешающий глобальной очереди | V25 разрешает FAILED с сохранённой metadata, scan receipt и Job атомарны | FileScanWorkerContract: rejectedUrlWithMetadataDoesNotPoisonTheNextScanJob на H2 и PostgreSQL |
| Обход workspace quotas через новые tenants | Account lock, ограниченный membership count, V26, claim limit, safe 429 | PostgreSqlReviewConcurrencyTest: competing creation/direct SQL, archived membership, counter rollback/tampering; SecurityIntegrationTest |
| Мутация Brand после конкурентной архивации | Workspace lock до проверки состояния, затем Brand lock | create/update в PostgreSqlReviewConcurrencyTest реально ждут незавершённую archive transaction |
| Взаимная блокировка родителя и evidence worker | Явный порядок locks; отсутствие Evidence lock в URL eligibility query | PostgreSQL NOWAIT проверяет, что ожидающий Workspace worker не захватил Brand/Evidence |
| Старый callback завершает переизданный Job | Worker + expectedAttempt + lease deadline под Job lock | DurableJobTest, DurableJobQueueTest, существующие stale-result worker contracts |
| Потеря append-only защиты при historical upgrade | Transactional V14 callback, восстановление trigger, неизменные SQL checksums | Populated V13 upgrade, injected interruption/rollback, запрет UPDATE receipts |
| Отказ после уже оплаченного social fetch из-за короткого column | 20 000 в Domain/JPA/SQL; URL/FILE остаются bounded | Полный social registration с 20 000-symbol snapshot и origin receipt на PostgreSQL |
| BOLA/role escalation, forged provenance, SSRF, hostile renderer inputs | Существующие ownership/role checks, DB triggers, URL policy и provider isolation сохранены | Существующие security/provider suites включены в полный запуск |

## Runtime Flow

```text
POST Workspace
  → Bearer authentication / current OWNER check
  → READ_COMMITTED transaction
  → account row lock → membership count → quota check
  → Workspace + membership → atomic DB budget guard → commit

Evidence worker
  → claim transaction: Job → Workspace → Brand → Evidence
  → commit snapshot + attempt/lease
  → bounded provider/storage call outside transaction
  → completion transaction: same lock order
  → current attempt + lease + source/status checks
  → immutable receipt / Evidence state / Job state → commit
```

## Security Tests и Edge Cases

Проверяются lease ровно на границе expiry, повторно использованное worker name,
late failure и late success, archived parent, две конкурентные admission operations,
унаследованный REPEATABLE_READ snapshot, rollback счётчика и raw SQL tampering.
TEXT: 20 000 разрешены, 20 001 отвергаются; URL reference 4097 отвергается.
Legacy account с 12 memberships сохраняется при V26, но не получает 13-ю.
Неуспешный V14 можно повторить без manual Flyway repair и отключённых guards.

## Непроверенные аспекты и Production considerations

Новая версия не активирует реальные AI providers и не добавляет отложенные
approval/revision HTTP workflows, regeneration/export/analytics. Это отдельный
product scope, а не исправление перечисленных дефектов. Существующие domain/SQL
защиты Strategy lineage продолжают проверяться своим suite.

Перед production нужны backup/restore rehearsal, остановка старых writers для
upgrade, migration identity отдельно от runtime, runtime grants, явное включение
providers после operational checks. Проверка настоящих customer credentials,
внешних Instagram/TikTok API, production сети, backups и нагрузочного профиля
остаётся отдельной задачей. Миграции не применялись к существующим пользовательским
базам; integration tests используют отдельные disposable базы/контейнеры.

## Что мне важно понять

Проверка ACTIVE до записи не защищает от конкурентной архивации: между чтением и
commit может измениться parent. Lock заставляет конфликтующие действия иметь
определённый порядок, после чего нужно проверить актуальное состояние.

Lease owner не идентифицирует попытку: worker name может повториться. Номер попытки
отсекает старый callback, а expiry запрещает запись после истечения полномочий.

Правильная Java validation не заменяет PostgreSQL tests: зависимости triggers
и migration history проявляются именно в реальной СУБД.

## Что мне стоит изучить

- **Fencing token** — номер поколения работы; здесь expectedAttempt отделяет новый Job attempt от старого ответа.
- **Pessimistic locking / lock order** — ожидание конфликтующей записи и общий порядок получения locks предотвращают race/deadlock.
- **Transaction isolation** — READ_COMMITTED позволяет перечитать quota после ожидания; старый snapshot нельзя использовать для admission.
- **Transactional DDL** — PostgreSQL откатывает изменение trigger вместе с неуспешной migration.
- **Defense in depth** — одна квота и один content contract защищены и application, и database.

## Разделение доказательств

Факты проверки фиксируются ниже по результатам фактических запусков. Инженерный
вывод: исправления закрывают описанные сценарии review при существующих trust
boundaries. Это не доказательство отсутствия любых будущих ошибок и не
автоматическая квалификация production deployment.

## Тесты — фактический результат 2026-10-05

Перед финальным запуском найден и запущен установленный Docker Desktop. Для shell
использованы JDK 21.0.11 и локальный Maven 3.9.16 cache; Docker CLI добавлен только
в PATH процесса, DOCKER_HOST указывал на desktop Linux engine. Production config
не изменялась. Промежуточные ошибки отсутствующего Docker, новой migration и старых
mock fixtures устранены до финального полного запуска.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

- **BUILD SUCCESS**; 695 tests, 0 failures, 0 errors, 3 Windows filesystem skips.
- Отдельный Linux filesystem suite: **35/35**, 0 failures/errors/skips; включая все три Windows-skipped link cases.
- Все 140 тестов с PostgreSql в имени suite и все 86 security-package tests прошли.
- Обязательные PostgreSQL и evidence/provider report gates прошли без missing/skipped suites.
- Python report-gate tests: **10/10**.
- JaCoCo: **4303 covered / 299 missed lines — 93,50%**; coverage gate пройден.
- JAR packaging и Spotless check прошли. `git diff --check` ошибок не обнаружил.
- V1–V24 не имеют изменений; добавлены V25/V26.

Запущен именно собранный JAR, а не только Spring test context:

```powershell
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089
Invoke-WebRequest http://127.0.0.1:18089/api/v1/health
Invoke-WebRequest http://127.0.0.1:18089/api/v1/workspaces -SkipHttpErrorCheck
```

Startup успешен; health вернул **200**, защищённый Workspace endpoint без Bearer
вернул **401 authentication_required**. Временные smoke process и отдельный native
PostgreSQL останавливаются после проверки. Существующие PostgreSQL services и базы
не использовались. Docker оставлен доступным для последующей разработки.

Warnings: deprecated/unchecked API в существующих test sources, ожидаемые
отказы startup в fail-closed configuration tests и Windows filesystem assumptions.
Они не скрывают failed tests. Текущий vulnerability scan зависимостей и remote CI
для этих незакоммиченных изменений не запускались; новые dependencies не добавлялись.

## Изменённые файлы

- `.env.example` — Документирован account limit.
- `docs/database-migrations.md` — Upgrade V25/V26 и требования к V14 callback.
- `docs/review-remediation-90686e5.md` — Решения, границы проверки и итоговый отчёт.
- `docs/verification.md` — Воспроизводимый native PostgreSQL test path.
- `scripts/test_verify_postgres_report.py` — Regression проверки обязательных новых suites.
- `scripts/verify_postgres_report.py` — Migration/concurrency suites обязательны для gate.
- `src/main/java/com/cadence/application/exceptions/WorkspaceQuotaExceededException.java` — Явная application ошибка квоты.
- `src/main/java/com/cadence/application/repositories/BrandEvidenceRepository.java` — Отдельные ownership projection и Evidence row lock.
- `src/main/java/com/cadence/application/repositories/CadenceUserRepository.java` — Account row lock и membership count.
- `src/main/java/com/cadence/application/repositories/DurableJobRepository.java` — URL eligibility через EXISTS без раннего Evidence lock.
- `src/main/java/com/cadence/application/repositories/WorkspaceRepository.java` — Scoped Workspace row lock.
- `src/main/java/com/cadence/application/services/DurableJobQueue.java` — Clock и attempt fencing при claim/completion.
- `src/main/java/com/cadence/application/services/EvidenceArtifactWorker.java` — Явный порядок locks и новый Job completion contract.
- `src/main/java/com/cadence/application/services/EvidenceMalwareScanWorker.java` — READ_COMMITTED, parent-first locks, attempt fencing.
- `src/main/java/com/cadence/application/services/EvidenceOwnershipLocks.java` — Общая последовательность Workspace → Brand → Evidence.
- `src/main/java/com/cadence/application/services/UrlFetchWorker.java` — Parent-first locks, fresh clock после ожидания, fenced completion.
- `src/main/java/com/cadence/application/usecases/BrandManagementUseCase.java` — Сериализация create/update/archive с parent lifecycle.
- `src/main/java/com/cadence/application/usecases/WorkspaceManagementUseCase.java` — Account quota, current-state admission, isolation check.
- `src/main/java/com/cadence/domain/BrandEvidence.java` — TEXT limit согласован с API/provider contract.
- `src/main/java/com/cadence/domain/DurableJob.java` — Проверка expectedAttempt и lease expiry.
- `src/main/java/com/cadence/infrastructure/persistence/LegacyScanReceiptMigrationCallback.java` — Transactional upgrade populated V11–V13 без изменения checksums.
- `src/main/java/com/cadence/presentation/ApiExceptionHandler.java` — Безопасный 429 для Workspace quota.
- `src/main/java/com/cadence/security/CadenceIdentityService.java` — Ограничен размер workspace bootstrap claim.
- `src/main/resources/application.properties` — Конфигурация account limit.
- `src/main/resources/db/migration/V25__evidence_failure_and_content_contract.sql` — FAILED extraction provenance и bounded TEXT column.
- `src/main/resources/db/migration/V26__account_workspace_budget.sql` — Атомарный membership budget, counter guard, безопасный legacy backfill.
- `src/main/resources/openapi/cadence.json` — OpenAPI contract для Workspace quota 429.
- `src/test/java/com/cadence/application/BrandManagementUseCaseTest.java` — Mocks используют новые scoped locks; прежние assertions сохранены.
- `src/test/java/com/cadence/application/EvidenceArtifactWorkerContract.java` — Общий lock helper и required attempt number.
- `src/test/java/com/cadence/application/FileScanWorkerContract.java` — Реалистичная URL metadata и regression на отсутствие poison queue.
- `src/test/java/com/cadence/application/PostgreSqlSocialEvidenceQuotaTest.java` — Публикация полного 20 000-symbol snapshot; disposable database extension.
- `src/test/java/com/cadence/application/PostgreSqlSocialProviderCapacityTest.java` — Общий PostgreSQL test extension.
- `src/test/java/com/cadence/application/UrlFetchWorkerTest.java` — Новый Clock-based queue claim contract.
- `src/test/java/com/cadence/application/UseCaseAuthorizationTest.java` — Mocks новых locks при прежних запретах archived resources.
- `src/test/java/com/cadence/application/WorkspaceManagementUseCaseTest.java` — Проверка quota rejection до любых writes.
- `src/test/java/com/cadence/domain/BrandEvidenceTest.java` — Граничные размеры TEXT/reference.
- `src/test/java/com/cadence/domain/DurableJobTest.java` — Expired lease и старый attempt при повторном worker name.
- `src/test/java/com/cadence/domain/EvidenceScanResultTest.java` — Явный expectedAttempt в fixture.
- `src/test/java/com/cadence/persistence/DurableJobQueueTest.java` — Authoritative Clock, rejection late callbacks и прежние retry/concurrency cases.
- `src/test/java/com/cadence/persistence/PostgreSqlBrandAnalysisTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlEvidenceArtifactWorkerTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlEvidenceRegistrationQuotaTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlFileScanWorkerTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlFileUploadTransactionTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java` — Проверка V1–V26 и новый PostgreSQL test extension.
- `src/test/java/com/cadence/persistence/PostgreSqlQuarantineLifecycleTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlReviewConcurrencyTest.java` — Реальные account/Brand/worker races, SQL bypass и isolation rejection.
- `src/test/java/com/cadence/persistence/PostgreSqlReviewMigrationTest.java` — Populated V13 upgrade/rollback и legacy membership preservation.
- `src/test/java/com/cadence/persistence/PostgreSqlStrategyBuildTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlStrategyMigrationTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/persistence/PostgreSqlTestDatabase.java` — Testcontainers default и explicit disposable native PostgreSQL fallback.
- `src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java` — Подключён общий PostgreSQL test extension; существующие assertions сохранены.
- `src/test/java/com/cadence/security/JobApiIntegrationTest.java` — Fixture Job completion указывает номер попытки.
- `src/test/java/com/cadence/security/SecurityIntegrationTest.java` — Запрет чрезмерного bootstrap claim до provisioning.

## Повторная qualification при передаче — 2026-10-05

Новый рабочий чат подтвердил mvp/90686e5 и завершённый статус чата review до edits.
Существующий diff сохранён. Повторно выполнено на текущем source:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

Docker Desktop Linux engine 29.8.1, JDK21.0.11, Maven3.9.16. BUILD SUCCESS за3m01s;
695 tests, failures/errors0, Windows skips3. Linux filesystem35/35 без skips.
Python10/10, PostgreSQL и all-provider gates passed. Full provider и renderer suites
включают startup/sandbox checks; ожидаемое fail-closed startup rejection не является
failed test. Исторические startup результаты выше не подменяют этот новый запуск.
Remote CI фиксируется отдельно после push; production deployment не выполнялся.

## Git/remote qualification при передаче

Commit `9f6f530ac1bf580aa04406bbf1f9322bfa025f24` pushed в mvp.
[CI37257337832](https://github.com/Sanyoghk/Cadence/actions/runs/37257337832)
завершился success: verify и dependency-security passed. Linux Maven695/695,
failures/errors/skips0; PostgreSQL/all-provider report gates passed; Python scripts10/10
и renderer29/29. Enforcing AppArmor/kernel preflight и real screenshot1280×720 passed.
OSV verified165 Maven dependencies без findings на момент проверки.
Это qualification review source SHA, не последующих локальных Strategy API changes.
CI warnings: pinned Actions targeting Node20 forced to Node24 и punycode deprecation;
jobs successful. Обновление Actions runtime остаётся quality-gate maintenance follow-up.
Merge/deployment не выполнялись.
