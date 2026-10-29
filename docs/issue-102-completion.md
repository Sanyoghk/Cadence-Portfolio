# Issue #102 — Strategy REST lifecycle

Дата: 2026-10-05. Baseline перед issue: review source `9f6f530`.
Первый slice create/read/list: local commit `3879f23`; этот отчёт дополняет
[его qualification](issue-102-api-slice.md). Final remote status записывается после CI.

## Что было сделано

Creator может создать generated DRAFT, прочитать revision/history, заменить draft content
и подтвердить именно прочитанную revision. Изменение APPROVED/LOCKED создаёт новый DRAFT;
подтверждённая история сохраняется. Actor/time approval и ссылка на source назначаются
сервером и защищаются PostgreSQL. Broader Brand Identity/Playbook/AI workflow не объявлен finished.

## Как это работает

StrategyController принимает отдельный UpdateStrategyRequest или VersionRequest.
ManageStrategyUseCase проверяет OWNER и scope, получает короткие parent/root locks,
сравнивает expectedVersion с JPA lockVersion. StrategyContent проверяет complete content
независимо от HTTP. DRAFT меняется на месте. Approved source остаётся прежним, новый
DRAFT получает max(Brand.version)+1 и revisedFromId. Generation receipt остаётся у source:
ручное редактирование не является новым AI output.

Approval — отдельный command над **committed** DRAFT. Actor из CadencePrincipal сверяется
с active OWNER/membership в БД; server Clock назначает approvedAt. Клиентские actor/status
поля не входят в writable DTO. После approval old audit/content неизменяемы.

Все response collections копируются в immutable StrategyDetails внутри transaction,
затем Controller строит StrategyResponse. Entity/lazy collections не выходят в HTTP; OSIV=false.
Foreign/missing Strategy возвращают одинаковый404, stale/replayed lifecycle command409,
невалидный content400, role denial403, no Bearer401, disabled generation safe503.

## Архитектурное решение

Использованы existing modular-monolith boundaries: presentation DTO → application command →
domain invariant → scoped repositories → PostgreSQL constraints. Domain не зависит от security
context или HTTP. Dedicated commands используют REQUIRES_NEW/READ_COMMITTED для fresh
persistence context, без external I/O; default transaction timeout10s остаётся действующим.

Lock order edit: Workspace→Brand→Strategy; approval: Account→Workspace→Brand→Strategy.
Account первый согласован с Workspace admission. Brand lock общий с #97 completion,
поэтому generation и branch allocation не выбирают одинаковый следующий version.
Внешние/model calls остаются вне transaction; новая функция не добавляет external integrations.

Alternatives/trade-offs:

- Mutable approved content потерял бы review history; выбран независимый DRAFT.
- source.version+1 конфликтовал бы с более новыми revisions; выбран latest+1 под lock.
- Combined edit/approve зависит от JPA flush order и подтверждает непрочитанный content;
  выбраны два HTTP commands, ценой дополнительного запроса.
- Partial pillar endpoints сложнее атомарно валидировать; PATCH заменяет полный editable
  content и не является JSON Merge Patch.
- Каждое изменение draft не создаёт отдельный immutable revision. Сохраняется draft
  in place и audit approval; полный event ledger не реализован.
- Approved-source retries создают отдельные branches: request idempotency пока отсутствует.

[ADR-0011](adr/0011-strategy-rest-lifecycle.md) фиксирует contract и пределы audit.

## Security Review

Мини-модель угроз:

1. Защищаем tenant Strategy content, generation/history, approval actor/time и revision lineage.
2. Вызвать write может authenticated OWNER с Workspace membership. EDITOR/VIEWER запрещены.
3. Границы доверия (trust boundaries): HTTP/JWT → backend; DTO → domain; ORM → PostgreSQL.
4. Атакующий может подставить чужой UUID, forged role/actor/status, stale token, oversized
   content или попытаться изменить approved history/concurrent state.
5. Guards, scoped lookups, validated allowlist DTO/domain, locks, audit/lineage FK/constraints
   и immutable triggers предотвращают эти сценарии на применимых слоях.
6. Обход HTTP не обходит Spring method security; обход domain не обходит PostgreSQL guards.
   Полностью скомпрометированный runtime credential не является доверенной HTTP identity:
   DB audit не служит криптографической подписью, external audit sink остаётся #87.
7. Это эшелонированная защита (defense in depth), а не доверие frontend.

**Threat:** Broken Access Control / IDOR/BOLA через foreign Strategy UUID или other role.
**Protection:** Controller OWNER, use-case OWNER/scope/current actor guards, scoped Brand/Workspace
resolution, проверка DB active actor/membership при approval; foreign и missing404.
**Verification:** shared H2/PostgreSQL success +401/403/404, direct use-case role negatives;
forged/stale principal с missing membership или actual VIEWER не может approval.

**Threat:** Mass assignment status, actor, provenance или lineage.
**Protection:** writable DTO содержит только expectedVersion/cadence/contentPillars; actor из
principal, time из Clock; extra JSON fields игнорируются без назначения внутренних полей.
**Verification:** forged status/approvedBy PATCH остаётся DRAFT/audit=NULL; forged client actor
approval сохраняет реального actor; OpenAPI schemas и DTO fields проверены reflection gate.

**Threat:** Stale approval, lost update и concurrent duplicate business version.
**Protection:** expected lockVersion, короткие ordered parent/root locks, allocation под Brand,
fresh transaction; separate approval над committed values.
**Verification:** edit vs approve принимает ровно один token; concurrent approved edits создают
versions2/3; generation vs branch также2/3; archive waiter подтверждает Workspace lock до
Strategy lock и запрещает write после committed archive.

**Threat:** Software/Data Integrity Failures через переписывание approved history/audit/lineage
или incomplete collection flush.
**Protection:** V24 immutable content/provenance guards + V27 FK/paired audit/immutable lineage,
deferred content checks; минимальный runtime column UPDATE, DELETE roots запрещён.
**Verification:** real PostgreSQL raw SQL tampering rejected/rollback; actual restricted runtime
создаёт, редактирует, подтверждает и branches, но не переписывает provenance/source/lineage.
Populated V23→V27 сохраняет old content и NULL audit без выдумывания истории.

**Threat:** malformed/oversized input и Resource Exhaustion.
**Protection:** DTO/domain bounds1..10 pillars, sum100,1..21 posts,1..7 unique times,
bounded texts/timezone; existing request128KiB bound, pagination≤100 и timeout10s;
нет external I/O в write transaction.
**Verification:** HTTP/domain invalid weights/zone/duplicate times/precision/posts/empty input;
existing full-suite body/pagination gates. Global per-tenant write/branch rate/retention quota
пока отсутствует и необходим перед public production activation (#87/#120).

**Threat:** Injection / excessive data exposure / sensitive logging.
**Protection:** parameterized repository queries/SQL; никакого shell, URL fetch или template
evaluation в этих endpoints; snapshot DTO, safe shared Problem Details/no-store, не rawSQL/errors.
**Verification:** inspection + shared error/OpenAPI tests. Новые secrets/dependencies/logs не добавлены.
CSRF/CORS authentication design не менялся: API использует explicit Bearer, а не cookie session;
frontend должен безопасно отображать text (без unsafe HTML). JWT/Clerk controls остаются existing.

## Изменённые файлы

Полный per-file manifest обоих slices: [issue-102-files.md](issue-102-files.md).
Основные components: ManageStrategyUseCase.update/approve, StrategyContent,
Strategy.approve/createDraftVersion/replaceContent, V27 guards, StrategyController и reviewed OpenAPI.

## Runtime Flow

```text
PATCH JSON / POST approval + Bearer
  ↓ Spring Security / local CadencePrincipal / OWNER
StrategyController + request DTO validation
  ↓ use-case method authorization / scoped root projection
ManageStrategyUseCase REQUIRES_NEW READ_COMMITTED
  ↓ Account (approval only) → Workspace → Brand → Strategy locks
expectedVersion + active parent / actor membership checks
  ↓ StrategyContent / Strategy lifecycle
StrategyRepository flush
  ↓ V24 + V27 guards / deferred collections / commit
immutable StrategyDetails → StrategyResponse / relative Location for new DRAFT
```

## Тесты

Среда: Java21, Maven wrapper, actual Docker Desktop Linux engine, PostgreSQL17-alpine;
MinIO/private ClamAV/isolated renderer — existing actual production adapters с synthetic fixtures.
Команды выполнялись с JAVA_HOME Temurin21, Docker CLI PATH и
DOCKER_HOST=npipe:////./pipe/dockerDesktopLinuxEngine. Real production database не использовалась.

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=StrategyTest,StrategyContentTest,RecommendationTest,StrategyApiIntegrationTest,PostgreSqlStrategyApiTest,PostgreSqlStrategyBuildTest,PostgreSqlStrategyMigrationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
```

Final local source: `spotless:apply clean verify` **BUILD SUCCESS**,746 tests,
0 failures/errors,3 Windows-only filesystem skips. Все35/35 filesystem cases отдельно
прошли на Linux без skips; skipped required integration tests отсутствуют.
PostgreSqlStrategyApiTest27/27, shared H2 lifecycle19/19, actual disabled-provider1/1,
full-provider/runtime-role9/9. Compilation, packaged JAR, Spotless и JaCoCo gate passed:
4543 covered /293 missed lines (~93.94%). Python scripts10/10, renderer29/29;
PostgreSQL и complete-provider report gates passed.

Renderer commands (реально выполнены):

```powershell
docker build --tag cadence-102-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-102-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
```

Packaged JAR запущен через hidden Start-Process:

```powershell
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089
Invoke-WebRequest http://127.0.0.1:18089/api/v1/health -SkipHttpErrorCheck
```

Startup success, health200; GET Strategy/read-list, POST generation/approval и PATCH
без Bearer401. Для POST/PATCH отправлен `application/json` `{}`; все5 endpoints
проверены через фактический HTTP. Созданный process остановлен в finally.
Первый smoke ошибочно использовал `/actuator/health`; после проверки фактического
SecurityConfiguration повторён через public `/api/v1/health`, без изменения product source.
Local startup использует isolated H2, а PostgreSQL startup/validate — integration suites.

Warnings: existing deprecated/unchecked test APIs; expected negative startup/SQL errors,
Hikari validation warnings при остановке disposable DB; assertions не подавлялись.
Remote CI нового pushed source записывается отдельно после фактического выполнения.
Промежуточный focused run:73/73, no failures/errors/skips, до добавления archive/allocation cases.
Первая full попытка выявила detached lazy collection в старом runtime-role fixture; fixture
переведён на transactional application approval, без удаления/ослабления SQL assertions.
После этого изменения выполнен успешный final full run выше; результаты старого SHA его не заменяют.

## Security Tests

Проверены authorized flow, missing Bearer, EDITOR/VIEWER, foreign/missing Strategy,
direct method role guard, persisted actor membership/role, client actor/status forgery,
invalid DTO/domain content, stale edit/approval, approval replay, SQL audit/lineage/content
tampering, runtime privilege denial и архивирование во время ожидания parent lock.

## Edge Cases

- Edited old approved revision при более новом sibling получает latest+1.
- APPROVED и LOCKED source не меняются; новые drafts не наследуют audit/generation receipt.
- Local publishing times сохраняются как wall time без UTC shift; microsecond precision
  предотвращает silent PostgreSQL rounding.
- Duplicate case-insensitive names, time duplicates, unknown IANA zone и weights≠100 запрещены.
- Integer.MAX_VALUE не переполняется; domain и generation guards проверяют version limit.
- Legacy audit остаётся NULL; новые approvals требуют валидный committed content.
- Repeated approved edit — новая branch, а repeated approval —409.
- Foreign/missing revision одинаково404; reads archived history разрешены, writes запрещены.

## Непроверенные аспекты

Не проверены live Clerk/user signup, real model/provider output/privacy/cost/moderation,
production IAM/TLS/network/deployed renderer sandbox, full E2E frontend, large dataset
rollout/load, backup/PITR restore и rollback drill. Они требуют отдельно configured staging
и owner-approved providers/accounts; fixtures не подтверждают live support.
Отдельный saved Compose upgrade не запускался в этом slice: fresh/populated migrations,
Hibernate validate и actual runtime grants покрываются PostgreSQL integration suites.

## Что мне важно понять

Business version отвечает «какая стратегия», lockVersion — «изменилось ли то, что я прочитал».
Проверять business version для optimistic update неправильно: draft меняется внутри той же version.
Approval не должен принимать content одновременно: иначе сервер подтвердил бы не тот snapshot,
который creator прочитал. Отдельный command с token делает эту связь проверяемой.
Locks и constraints решают разные задачи: locks сериализуют правильные concurrent operations,
constraints сохраняют integrity при обходе application кода. DTO контролирует writable data,
а не просто формат JSON. Source generation receipt не описывает ручное редактирование.

## Что мне стоит изучить

1. Оптимистическая конкуренция (optimistic concurrency): token ловит stale client update;
   здесь expectedVersion сравнивается с JPA @Version lockVersion после lock.
2. Пессимистическая блокировка (pessimistic locking): DB временно сериализует writers;
   здесь parent order предотвращает archive/child inversion и version collisions.
3. Отложенное ограничение (deferred constraint): проверяет окончательные collections при
   commit, а не промежуточное состояние Hibernate flush.
4. Контекст persistence (persistence context): Hibernate хранит managed Entity/lazy values
   в transaction; fresh approval transaction читает committed content, DTO копируется до close.
5. Происхождение revision (revision lineage): immutable revisedFromId объясняет источник
   manual draft, не приписывая ему старое generation receipt как новое AI generation.

## Production considerations

Перед activation применить V27 и обновлённый runtime-permissions.sql, квалифицировать
rollout на staging, restore/retention policy и clock/tzdata alignment JVM/PostgreSQL.
Нужны per-tenant write/branch quotas/rate limits и monitoring, включая authenticated abuse.
Actual AI остаётся fail-closed до #120, durable Jobs/budgets и provider qualification.
Approved/versioned Brand Identity/source link, citations и полные Playbook fields остаются
следующей feature очередью #107; #102 завершает только свои REST/lifecycle acceptance criteria.
Audit FK сохраняет user identity reference: hard account deletion требует retention design.
Не заявляются production-ready product, merge или deployment.

## Факты, выводы и assumptions

- **Факты:** source/contracts/permissions inspected; конкретные тестовые результаты и CI
  привязаны к выполненным командам и SHA в qualification разделе.
- **Инженерный вывод:** scoped commands, token/ordered locks и независимые DB guards
  защищают протестированные revision/approval invariants при existing backend architecture.
- **Assumptions/дополнительная проверка:** deployment соблюдает runtime grants, clock/tzdata
  и verified authentication configuration; trusted server credentials не скомпрометированы.
  Live-provider/deployment/load/retention и external audit sink остаются separate gates.

## Полная remote qualification и GitHub completion

[CI37261511659](https://github.com/Sanyoghk/Cadence/actions/runs/37261511659), exact source
`e400acb8eadbaa5b186b61fa8549e9ed0e70247b`: **verify + dependency-security success**.
Linux `sh ./mvnw --batch-mode --no-transfer-progress clean verify`: **746 tests,
0 failures/errors/skips**, BUILD SUCCESS; compilation/JAR/Spotless/JaCoCo passed.
Scripts Python10/10, renderer29/29; mandatory PostgreSQL/MinIO/ClamAV/renderer/full-provider
report gates passed. Enforcing AppArmor/kernel boundary/real sandbox preflight и isolated
screenshot1280×720 passed. Renderer boundary negative test выводит expected failed-container
информацию; RendererContainerIntegrationTest6/6 passed. OSV verified165 Maven components
без reported vulnerabilities на момент scan; это не SAST/container/deployment certification.

Commits pushed в mvp:3879f231e673341a307b6e5815c1b487512d4077 (create/read/list) и
e400acb8eadbaa5b186b61fa8549e9ed0e70247b (lifecycle/V27).
#102 closed completed, #107 Task3.2 checked, следующий [#121](https://github.com/Sanyoghk/Cadence/issues/121)
помечен status:next; implementation ещё не начат. Task2.3 остаётся partial, #120 planned;
более широкий AI Jobs/budgets/Playbook/frontend/production scope остаётся open.
No merge/deployment. Последующий docs-only commit записывает эти результаты и не меняет
product source/tests/configuration; данный CI относится к **e400acb**, а не автоматически
к новому docs SHA. Product diff после qualification отдельно проверен пустым.

