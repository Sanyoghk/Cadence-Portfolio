# #102 — Strategy create/read/list API slice

Дата: 2026-10-05. Baseline после review handoff: 9f6f530.

## Что было сделано

Creator может через authenticated REST создать generated DRAFT, получить отдельную
revision и paginated history своего Brand. Это первый законченный slice #102.
Редактирование draft, approval и новая draft version при изменении approved content
пока не реализованы в REST; issue остаётся открытым. Полнота всего Playbook/approved
Brand Identity source и production model provider также не объявляются завершёнными.

## Как это работает

StrategyController получает server-authorized Workspace scope из CadencePrincipal.
POST вызывает существующий BuildStrategyUseCase с short snapshot/completion
transactions и external generation вне transaction. Safe unavailable exception
переводится в503. Generated revision всегда сохраняется как DRAFT.
ReadStrategyUseCase использует scoped lookup и копирует все lazy collections внутри
read-only transaction в immutable StrategyDetails. Presentation возвращает отдельный
StrategyResponse. Controller не держит transaction вокруг provider I/O.

## Архитектурное решение

Сохранены presentation→application→domain/repository boundaries и OSIV=false.
Immutable nested records не содержат Entities или persistent collections.
Новые DTO/read use case проще, чем global eager collection loading: eager joins
для двух collections размножают rows, особенно с pagination. Trade-off: до двух
collection queries на revision в bounded page; performance optimisation позже
по measurements. Read snapshot гарантирует отсутствие lazy-loading после transaction,
но READ_COMMITTED list не обещает стабильную историю между разными HTTP pages.

Новых dependencies/migrations нет. Existing #97 owns allocation/source/deadline guards.
Strategy foreign/missing ID concealed404; Brand routes сохраняют established403.
API не принимает status/provenance и не умеет автоматически approve.
Runtime permissions и checksumsV1–V26 не изменяются.

## Security Review

Защищаем tenant content/history и DB/provider capacity. Caller — authenticated OWNER.
Trust boundaries: HTTP identifiers/query/body, server principal scope, persisted values,
model output. Authorization не зависит от frontend или знания UUID.

- Threat: BOLA/IDOR и forged scope. Protection: OWNER method guards на controller и
  use case, principal-scoped repository lookup, permitsWorkspaceScope; foreign read404.
  Verification: cross-Workspace requests, forged direct scope, missing UUID на H2/PostgreSQL.
- Threat: reserved-role bypass. Protection: denyAll class default и OWNER-only methods.
  Verification: EDITOR/VIEWER для всех трёх routes и direct read/list calls; no-auth401.
- Threat: model/client-controlled approval/provenance. Protection: POST не использует
  body, #97 immutable validated output и server-owned DRAFT/provenance.
  Verification: forged APPROVED/provider input не изменяет generated result.
- Threat: stale/archived source. Protection: #97 Workspace→Brand locks и повторные
  source/version/deadline checks. Verification: archive до вызова и внутри generation
  дают409; результат не сохраняется; #97 concurrency/deadline tests входят в full suite.
- Threat: sensitive diagnostic exposure. Protection: fixed503 Problem Details,
  DTO исключает raw signature, credentials, internal source/deadlines.
  Verification: secret-looking provider failure не возвращает secret/message, null
  output не публикуется; actual disabled adapter не переключается в fake.
- Threat: resource exhaustion и arbitrary query paths. Protection: page/size bounds,
  sort allowlist и общий128KiB non-multipart body guard, application pagination guard.
  Verification: size101/0,page-1/10001, invalid sort/direction, unpaged direct call,
  oversized body413 до provider. Per-tenant generation quotas/AI Jobs остаются
  production blocker; count/history retention не ограничиваются этим slice.
- Threat: SQL/command injection, SSRF. Protection: parameterized Spring Data queries;
  новые routes не принимают remote URL и не запускают shell/HTTP transport.
  Verification: inspection; existing transport negative suites retained.
- Threat: XSS. Protection: API возвращает JSON/text, не исполняет output; будущий
  frontend обязан сохранять text rendering и не вводить unsafe HTML rendering.
  Verification: DTO/serialization inspection; frontend ещё отсутствует.
- Threat: CSRF/CORS/authentication regression. Protection: существующий stateless
  Bearer-only security без session/cookie auth; SecurityFilterChain не менялся.
  Verification: existing JWT/security suites в full run; live Clerk не квалифицирован.
- Threat: supply-chain/logging. Protection: новых dependencies и logging provider
  payloads нет. Verification: diff inspection; remote dependency scan отдельно
  квалифицирует только pushed review SHA9f6f530, не этот локальный slice.

Defense in depth: HTTP guards → application scope guard → scoped repository →
existing #97 domain/provider validation → V24 database guards. Если frontend
отправляет чужой UUID или status, backend независимо отвергает доступ/игнорирует
недоступные client-controlled fields. Обычный DB owner/admin остаётся privileged
operator; runtime должен использовать documented non-owner grants.

## Runtime Flow

```text
Bearer authentication → local OWNER/memberships → StrategyController
 POST → BuildStrategyUseCase
      → snapshot transaction [Workspace→Brand locks]
      → validated immutable provider command → generator [no DB transaction]
      → completion transaction [locks, source/deadline/version checks]
      → Strategy DRAFT + provenance/collections → PostgreSQL V24 commit
      → ReadStrategyUseCase → immutable StrategyDetails → StrategyResponse + Location201
 GET → ReadStrategyUseCase [read-only transaction]
     → scoped root/paged query → load/copy collections → commit
     → StrategyResponse/StrategyPage
```

## Security Tests и Edge Cases

StrategyApiContract выполняется целиком на H2 и PostgreSQL: success, retained approved
legacy с generation=null, archived history, empty list, default newest-first paging,
immutable detached values,401/403/404, malformed UUID, forged scope/body, pagination
bounds, source/archive409, provider/null503 и body413. StrategyApiFailClosedTest
использует actual disabled adapter, не Mockito substitute. OpenAPI reflection gate
сверяет каждый actual route/schema; все success/error responses тестируются.

Первый focused run:25 tests,0 failures,1 fixture error. Fixture пытался вставить
коллекции сразу в APPROVED parent и был отвергнут PostgreSQL. Исправлен порядок
создания данных: persisted DRAFT → отдельная committed approval transaction.
DB guard не изменён; expected approved/immutable behavior сохранён.
Следующий focused run32/32,0 failures/errors/skips.

## Непроверенные аспекты

Оставшийся #102: PATCH/approval/version lineage/audit и соответствующая migration.
Real model/live Clerk, frontend, load/privacy/backup/restore/deployment не проверены.
Новый API slice пока local commit; согласно user push policy remoteCI выполняется
после завершения всего issue. Не переносить review SHA CI results на этот source.

## Что мне важно понять

Lazy collections требуют открытой persistence session, чтобы выполнить SQL.
Если Entity покинет use case до чтения collection, JSON serialization может упасть
или неожиданно выполнить SQL при OSIV. Здесь use case возвращает уже готовые values.

UUID идентифицирует запись, но не доказывает право её читать. Scope берётся с сервера
и входит в query; foreign ID не позволяет получить другой Workspace.
version — business revision; lockVersion — optimistic concurrency token. Следующий
slice должен проверить последний даже после получения parent locks.

## Что мне стоит изучить

- Read model — immutable projection для чтения; здесь StrategyDetails отделяет Entity lifecycle от API.
- Lazy loading — загрузка коллекций при обращении; выполняется внутри application transaction.
- Object-level authorization — право на конкретный ресурс; scoped lookup предотвращает BOLA.
- Optimistic concurrency — detection устаревшей записи через lockVersion; контракт подготовлен для edit/approval.
- Fail-closed — disabled provider возвращает503 вместо неявного fake или неподтверждённого output.

## Production considerations

Не активировать real synchronous AI без durable Jobs, quotas, idempotency и cost/
concurrency bounds; #120 и broader AI workflow отдельно. Ingress rate/time limits,
live Clerk, TLS/IAM/runtime grants, observability и backup/restore остаются release
gates. Local fake — demonstration-only. UI обязан показывать fake warnings и human
review; эти endpoints не завершают production creator journey.

## Разделение доказательств

Подтверждённые tests/inspection фиксируются ниже. Инженерный вывод: read snapshot
сохраняет layer boundaries и tenant isolation для реализованных routes.
Assumptions: trusted CadencePrincipal создаётся существующим authentication adapter;
production DB использует documented runtime grants. Production network/identity/
provider setup требуется квалифицировать отдельно.

## Изменённые файлы

- `docs/review-remediation-90686e5.md` — Qualification pushed review SHA9f6f530 и фактический remoteCI result.
- `docs/verification.md` — Remote review gate отдельно от локального #102 slice.
- `DEVELOPMENT_PLAN.md` — Текущий статус #102 и актуальная очередь после roadmap2026-10-05.
- `DEVELOPMENT_PLAN_RU.md` — Русский план синхронизирован с фактической границей slice.
- `docs/api-contract.md` — Ссылка на extension reviewed API contract.
- `docs/issue-102-api-slice.md` — Отчёт, security review, фактические проверки и ограничения.
- `docs/strategy-api.md` — Endpoints, DTO/pagination/error contracts и точный handoff следующего slice.
- `scripts/test_verify_postgres_report.py` — Новый Strategy API suite обязателен; missing/skipped report отвергается.
- `scripts/verify_postgres_report.py` — PostgreSqlStrategyApiTest включён в required CI gate.
- `src/main/java/com/cadence/application/repositories/StrategyRepository.java` — Scoped query findByIdAndBrandWorkspaceIdIn; root entity graph без collection eager loading.
- `src/main/java/com/cadence/application/results/StrategyDetails.java` — Immutable detached read snapshot и безопасная public generation metadata.
- `src/main/java/com/cadence/application/usecases/ReadStrategyUseCase.java` — OWNER/scoped read/list, application pagination bound, mapping внутри read transaction.
- `src/main/java/com/cadence/presentation/ApiExceptionHandler.java` — Fixed safe503 strategy_generation_unavailable.
- `src/main/java/com/cadence/presentation/StrategyController.java` — Generated create201/Location, revision GET и paginated Brand list.
- `src/main/java/com/cadence/presentation/dto/StrategyResponse.java` — Внешний DTO, не JPA Entity.
- `src/main/resources/openapi/cadence.json` — Все три operations и Strategy response/nested/page schemas.
- `src/test/java/com/cadence/persistence/PostgreSqlStrategyApiTest.java` — Тот же HTTP/security contract на реальном PostgreSQL/FlywayV1–V26.
- `src/test/java/com/cadence/presentation/OpenApiContractTest.java` — DTO reflection alignment для StrategyResponse; existing route gate автоматически включает новые endpoints.
- `src/test/java/com/cadence/security/SecurityIntegrationTest.java` — Общий no-Bearer test подставляет UUID для любого path variable;401 assertions сохранены.
- `src/test/java/com/cadence/security/StrategyApiFailClosedTest.java` — Actual disabled adapter даёт503 и не сохраняет DRAFT.
- `src/test/java/com/cadence/security/StrategyApiIntegrationTest.java` — Общий API/security contract на H2.
- `src/test/java/com/cadence/support/StrategyApiContract.java` — 14 reusable success/negative tests, detached immutability и provider I/O transaction assertion.

Первый полный API run:724 tests,0 failures,1 error,3Windows skips.
SecurityIntegrationTest.allDocumentedProtectedOperationsRejectMissingBearerWithTheSameContract
не смог подставить новый strategyId. Исправлена только URI fixture substitution
для любого path variable; no-auth401 assertions и automatic route coverage сохранены.
Full suite повторяется после исправления; этот failed run не считается pass.

## Тесты — финальная qualification slice

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=StrategyApiIntegrationTest,StrategyApiFailClosedTest,PostgreSqlStrategyApiTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
git diff --check
```

- Focused32/32, failures/errors/skips0.
- Финальный full BUILD SUCCESS за3m09s;724 tests, failures/errors0, Windows skips3.
- Mandatory PostgreSQL gate, включая новый PostgreSqlStrategyApiTest, passed без skips.
- Mandatory PostgreSQL/MinIO/ClamAV/renderer/full-provider reports passed без skips.
- Python report-gate suite10/10; new required suite missing/skipped tests preserved.
- JaCoCo4394 covered /294 missed lines —93,73%; configured gate passed.
- Compilation, JAR packaging, Spotless check passed.
- Все26 historical migrations побайтово сравнены после нормализации CRLF: изменений нет.
- Три Windows filesystem skips ранее в этом же turn исполнены через
  `.\scripts\run_linux_storage_tests.ps1`:35/35 без skips. Filesystem source не менялся
  между этим запуском и финальной API qualification.

Фактически запущен собранный JAR с local disposable H2:

```powershell
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089
Invoke-WebRequest http://127.0.0.1:18089/api/v1/health
Invoke-WebRequest http://127.0.0.1:18089/api/v1/brands/00000000-0000-0000-0000-000000000001/strategies -SkipHttpErrorCheck
Invoke-WebRequest http://127.0.0.1:18089/api/v1/strategies/00000000-0000-0000-0000-000000000002 -SkipHttpErrorCheck
Invoke-WebRequest http://127.0.0.1:18089/api/v1/brands/00000000-0000-0000-0000-000000000001/strategies -Method Post -SkipHttpErrorCheck
```

Startup successful; health200, все три Strategy operations без Bearer401.
Process создан через Start-Process -WindowStyle Hidden; остановлен в finally.
Authenticated HTTP success проверен через реальный MockMvc/application/PostgreSQL
workflow, не через live Clerk; ограничения такого smoke явно сохранены.
Warnings: existing deprecated/unchecked test APIs; ожидаемые fail-closed startup
rejections; Hikari validation warnings после остановки disposable test databases.
Они не скрывают failed assertions.

## Git и следующая задача

Review9f6f530 pushed и remoteCI37257337832 successful (695 Linux tests без skips,
OSV165 components без findings, renderer29/29). Эта qualification относится к review
source; новый API slice получает отдельный local commit и пока не push согласно
user policy «push после полного завершения issue».
#102 остаётся open/in progress. Следующий scope: draft edit → approval с committed
content validation → approved edit создаёт новую draft с lineage/audit; затем full
PostgreSQL/runtime/concurrency/API gates, push и CI exact feature source SHA.

## Historical slice завершён полным #102

Записи выше описывают первый local slice и его тогдашний handoff. Теперь полный REST
lifecycle реализован в e400acb, pushed вместе с3879f23; CI37261511659 passed для e400acb:
Linux746/746 без failures/errors/skips, both jobs success. #102 closed completed,
#121 next. Актуальный [full report](issue-102-completion.md) содержит lifecycle/security/qualification.
