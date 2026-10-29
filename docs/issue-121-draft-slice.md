# #121 — Manual Brand Identity DRAFT: первый vertical slice

Дата: 2026-10-05. Ветка: mvp. Baseline: b2a6871ead3f2f18d5998db09fe358881282b4a6.
#121 остаётся открытым; этот отчёт не объявляет весь issue или production-ready AI завершённым.

## Что было сделано

Creator может создать независимый Brand Identity draft, прочитать его, получить ограниченную
страницу history и отредактировать content с защитой от конкурентной перезаписи. Добавлены
отдельная domain model, V28, OWNER/scoped REST, immutable DTO/OpenAPI и layered tests.
Approval/citations/approved Strategy source остаются следующими slices. Legacy signature
и существующие Strategy/analysis workflows сохраняются. Новых dependencies нет.

## Как это работает

BrandIdentityContent проверяет palette, списки и caption и копирует mutable inputs.
BrandIdentity хранит revision root и owned collections. Create берёт Workspace→Brand locks,
проверяет active parents, выделяет max(version)+1 и сохраняет новый DRAFT. Update делает
scope-first lookup, берёт Workspace→Brand→Identity locks, сравнивает expectedVersion с
lockVersion и меняет только content. Map в BrandIdentityDetails выполняется внутри transaction;
Controller возвращает BrandIdentityResponse без JPA Entity/private references.

Каждый mutation — fresh REQUIRES_NEW/READ_COMMITTED transaction. Spring proxy применяет
method authorization и transaction до вызова body. Flush отправляет SQL, commit выполняет
отложенные constraints. Ошибка commit отменяет всю transaction; 201/200 не подтверждают
частично записанный content. No-op content replacement может не увеличить lockVersion.

## Архитектурное решение

Выбрана отдельная revision model вместо mutation legacy BrandSignature. Иначе independent
revisions невозможно сохранить. Draft редактируется на месте; append-only каждого keystroke
не нужен текущему contract. В будущем approved edit будет создавать новую revision с lineage.
Полный replacement content проще валидировать атомарно; PATCH не JSON Merge Patch.

Database layer независимо защищает RGB/text, bounds, immutable metadata, allocation,
collection ownership/order/uniqueness и parent lifecycle. Constraints на collections deferred:
Hibernate может сначала удалить старые строки и только потом вставить новые. Проверка после
commit видит окончательное состояние. Runtime grants разрешают content UPDATE и owned
collection DELETE, но запрещают root DELETE/status/version/ownership UPDATE.

Limit1000 revisions/Brand — явный lifetime product bound. Pagination до100 не допускает
неограниченного response. Ограничения не заменяют production rate/tenant quotas. Read history
у archived parents разрешён, writes запрещены. DTO/model ничего не скачивают и не исполняют.
[ADR-0012](adr/0012-brand-identity-drafts.md) отделяет implemented contracts от следующих этапов.

## Security Review

Threat: IDOR/BOLA — authenticated OWNER подставляет чужой Brand/Identity ID.
Protection: scope из CadencePrincipal, permitsWorkspaceScope на application methods,
scoped repository lookups до locks; одинаковый404 foreign/missing resource.
Verification: shared H2/PostgreSQL HTTP contract,403 EDITOR/VIEWER и direct bean methods.

Threat: mass assignment — client присылает APPROVED/version/brandId/approvedBy.
Protection: dedicated request DTO; domain/server назначают metadata, V28 допускает толькоDRAFT,
metadata trigger и restricted column grants. Unknown JSON fields existing policy игнорирует.
Verification: HTTP forged fields создают только корректный DRAFT; raw SQL metadata mutations
отвергаются; actual non-owner runtime permissions test.

Threat: lost update/version collision и concurrent archive.
Protection: expectedVersion, JPA @Version, parent-first locks и version allocation под Brand lock.
Verification: два concurrent create дают1/2; два edits одним token допускают ровно один;
create/update ждут Workspace archive без преждевременного Identity lock и затем отвергаются.

Threat: invalid/partial content при обходе HTTP/domain.
Protection: Bean Validation→immutable domain→DB checks/FK/guards/deferred constraints.
Canonical whitespace учитывает Java Unicode whitespace; ISO controls запрещены.
Verification: malformed palette/null/empty/duplicate/oversized/control inputs; raw SQL blank
и Unicode blank caption, C1 control, missing collections/gapped order/reparent/incomplete roots
отвергаются с rollback. Maximum1000 проверен и application, и PostgreSQL.

Threat: injection/data exposure/secrets.
Protection: parameterized repositories/JDBC test inputs, no shell/remote URL calls из feature,
pure text DTO, common safe Problem Details, отсутствие content/token logging/secrets/new library.
Verification: inspection changed layers и OpenAPI assertions; existing security/error regressions
в full suite. Frontend обязан безопасно выводить текст; HTML rendering не часть этого slice.

Authentication: existing Clerk/Spring JWT configuration не менялась. Authorization: OWNER-only
на HTTP и bean boundaries; backend сам проверяет scope, frontend не security boundary.
CSRF/CORS: existing bearer-only configuration не менялась. Audit: approval отсутствует и
технически запрещён; draft-edit event audit пока не реализован. Resource exhaustion:
bounds/pagination/lifetime cap, finite existing transaction/DB deadlines; global rate limits #87.
DB grants не являются отдельным tenant RLS при компрометации общего runtime credential.

## Изменённые файлы

[Полный пофайловый manifest](issue-121-draft-files.md). Domain/application/presentation,
V28/runtime permissions, OpenAPI, negative/concurrency/migration/runtime tests, mandatory
report gates, ADR/API/verification/plans обновлены. V1–V27 не изменены; существующие OpenAPI
paths/schemas семантически не изменены (сравнение parsed JSON с git HEAD).

## Runtime Flow

```text
Bearer Request
    ↓
Spring Security / Clerk authentication
    ↓
BrandIdentityController: OWNER, DTO Bean Validation
    ↓
BrandIdentityUseCase: OWNER + principal scope, fresh transaction
    ↓
Scoped lookup → Workspace lock → Brand lock → Identity lock (PATCH)
    ↓
Domain content validation + expectedVersion + active parent checks
    ↓
BrandIdentityRepository / Hibernate flush
    ↓
PostgreSQL constraints/triggers + restricted runtime grants + deferred commit validation
    ↓
Immutable Details → Response DTO → 201/200 или safe Problem Details
```

## Тесты

Финальный полный build: BUILD SUCCESS,770 tests,0 failures,0 errors,3 skips — только
Windows filesystem scenarios. Independent Linux filesystem suite35/35 без skips закрывает
эти scenarios. PostgreSQL Identity suite13/13, populated Identity migration1/1, shared H2
Identity contract7/7, domain2/2, full actual provider/runtime suite10/10 прошли.
Все mandatory PostgreSQL и actual PostgreSQL/MinIO/ClamAV/renderer/full-pipeline report gates
прошли без failures/skips. Spotless check и packaged JAR build прошли. JaCoCo LINE4713covered,
295missed (94.11%), threshold check passed. Python scripts10/10, renderer29/29.

Фактически выполненные команды (PowerShell, JDK21, Docker Linux engine):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-121-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-121-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
git diff --check
```

Focused24/24 run command:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=BrandIdentityTest,BrandIdentityApiIntegrationTest,PostgreSqlBrandIdentityApiTest,PostgreSqlBrandIdentityMigrationTest,OpenApiContractTest'
```

Packaged application startup проверен через hidden `Start-Process` с аргументами:

```text
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089
```

`Invoke-WebRequest` проверил health200 и POST/GETlist/GETread/PATCH Identity401 без JWT.
Остановлен только созданный test process. Actual PostgreSQL application contexts startup
и Hibernate validate также проверены integration suites. Live Clerk happy path не проверен;
authorized success проверен MockMvc с existing authenticated principal/method security.

Финальные logs находятся в `%TEMP%`:cadence-121-verify-final.log,
cadence-121-linux-final.log,cadence-121-renderer-tests.log. Surefire/JaCoCo reports в target,
они не коммитятся. Logs предыдущих failures сохранены отдельно; результаты не подменялись.

Первый focused run:21 tests,6 failures,9 Docker skips. Исправлено конфликтующее H2 имя
колонки value→item_value; Docker Desktop запущен для реальных tests. Второй run:21 tests,
2 failures,0 skips — неверное fixture assumption, будто unloaded legacy signature обязательно
null; заменено populated legacy preservation assertion. Следующий focused run:24/24,0
failures/errors/skips (до двух добавленных SQL/cap tests). Первый full run:770 tests,
1failure,0errors,3Windows skips — UTC schema inventory не учитывал2 timestamp columns V28.
Исправлено точное count26 с отдельной assertion двух columns новой таблицы; старое UTC
conversion assertion сохранено. Итоговый результат указан выше, прежние runs не считаются pass.

Warnings: Byte Buddy dynamic agent/CDS при Mockito; Hikari closed connections после завершения
isolated Testcontainers databases. Они не являются startup/production qualification.
Новых build dependencies/plugins или suppressions не добавлено.

## Security Tests

- Anonymous4operations401, EDITOR/VIEWER4operations403 и прямые bean methods403.
- Foreign/missing Identity read/update и foreign Brand create/list404; malformedUUID400.
- Forged ownership/status/version/actor не изменяют server metadata.
- Stale token409; missing/null/negative token400; invalid/unbounded content400.
- Direct SQL запрещает metadata/status/delete и inconsistent collections; rollback сохраняет данные.
- Runtime non-owner role может заменить collections, но не переписать immutable metadata/history.
- Concurrency tests проверяют ровно один accepted token, distinct versions и archive lock order.

## Edge Cases

RGB input нормализуется uppercase; списки immutable, порядок сохранён, duplicate case/controls
не проходят. Limit1000 допускает1000 roots,1001отвергается; version0/out-of-range domain запрещён.
Пустой draft content, gaps и отсутствующие owned rows запрещены при commit. Archived history
читается, edits/create409. Legacy populated signature переживает V27→V28 без Identity backfill;
два новых independent drafts не меняют legacy content и друг друга.

## Непроверенные аспекты

Новый source не pushed: policy push только после всего #121; remote exact-SHA CI ещё нет.
Последний docs-only baseline b2a6871 имеет CI37262578710success, но этот result не квалифицирует
новый product source. Enforcing host AppArmor/kernel release preflight не запускался в этом
slice; production sandbox qualification отдельно. Live Clerk tokens/cloud infrastructure,
actual AI adapter/citations/approval/Strategy integration отсутствуют в этом scope.
Production database collation/Unicode case folding, large dataset migration/load/retention,
backup/PITR/recovery и external audit/rate quotas требуют staging/deployment verification.

## Что мне важно понять

Серверный version не то же самое, что optimistic lockVersion. Первый упорядочивает revisions,
второй сообщает, какая редакция draft была прочитана; сравнение token предотвращает lost update.
Tenant filtering должно происходить в backend: знание UUID не предоставляет право доступа.

Одна HTTP проверка не защищает от другого caller или будущего bug. Поэтому ограничения
повторены в domain, DB и runtime permissions. Deferred DB validation важна потому, что
Hibernate записывает aggregate несколькими SQL commands: промежуточное состояние может
быть неполным, но committed state обязан быть целостным. Approval нельзя добавлять простым
переключением status: нужны committed review content, actor/time, immutability и race tests.

## Что мне стоит изучить

1. Граница доверия (trust boundary): HTTP body недоверен; dedicated DTO/domain проверяют content.
2. Конкурентная перезапись (lost update): expectedVersion/JPA @Version защищают прочитанную редакцию.
3. Пессимистическая блокировка (pessimistic locking): Workspace→Brand→Identity согласует writes/archive/allocation.
4. Отложенное ограничение (deferred constraint): PostgreSQL проверяет завершённые owned collections при commit.
5. Минимальные привилегии (least privilege): runtime UPDATE columns не включает revision metadata/status.

## Production considerations

Нужны actual deployed JWT/membership qualification, TLS/secrets/runtime IAM, maintained providers,
rate limits/tenant quotas, content/privacy/retention policy, backup/restore/rollout/load tests и
external audit. Применить migrations, затем reviewed runtime-permissions.sql отдельным migration
identity; runtime не владеет schema. Future approval/citation/source stages должны использовать
новые migrations, immutable source/audit и server-selected references, не ослаблять текущие guards.

Факты: executed test results/inspection в разделе «Тесты»; source хранится local commit.
Инженерные выводы: parent-first locks и layered validation предотвращают проверенные races/invalid writes.
Assumptions: production использует reviewed migration/runtime grants и existing bearer-only authentication.
Не подтверждены: remote CI нового SHA, live/cloud/load/recovery/collation qualification и остальной #121.
