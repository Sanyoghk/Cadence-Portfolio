# #121 — Brand Identity approval и независимые draft revisions

Дата: 2026-10-05. Ветка mvp, baseline e514ad75984016e2a389d5e2763368d1dc5a8ac7.
Это второй законченный slice. #121 и #107 Task2.3 остаются IN PROGRESS.

## Что было сделано

Creator может подтвердить сохранённый Identity draft. Дальнейшее редактирование approved
content создаёт новый DRAFT; прежний content и approval audit сохраняются. Добавлены
server actor/time, immutable revisedFromId, V29, runtime grants, DTO/OpenAPI и layered tests.
Citations, generation provenance и approved Identity→Strategy source ещё не реализованы.

## Как это работает

POST `/api/v1/identities/{identityId}/approval` принимает VersionRequest.expectedVersion.
Controller берёт actor из CadencePrincipal. BrandIdentityUseCase.approve сначала выполняет
scoped projection, затем блокирует Account→Workspace→Brand→Identity. Actual active OWNER
и текущая membership проверяются в БД. Fresh REQUIRES_NEW/READ_COMMITTED transaction читает
committed draft, сравнивает lockVersion, вызывает BrandIdentity.approve(actorId, clock.instant())
и flush. Content из approval request не принимается. Response содержит approvedBy/approvedAt.

PATCH DRAFT заменяет content и возвращает200. PATCH APPROVED/LOCKED проверяет source token,
под Brand lock выделяет max(version)+1 и создаёт отдельный DRAFT, возвращая201/Location.
revisedFromId указывает source; approval audit не копируется. Source остаётся неизменным.
Повторные approved edits могут создавать разные branches; create не idempotent. Replay
approval409; stale expectedVersion409; malformed/null/negative tokens400.

## Архитектурное решение

Сохранён существующий Strategy lifecycle pattern без общей абстракции между разными aggregates:
общее inheritance/service framework сейчас усложнило бы разные будущие source/citation rules.
Использованы immutable snapshots, dedicated request DTO, explicit state transitions,
optimistic token плюс parent-first pessimistic locks, отдельная transaction для review command.

Rejected alternative: mutate approved root — уничтожает review history. Combine edit/approve
подтверждал бы content, который пользователь не читал, и зависел бы от Hibernate collection
flush order. Separate command подтверждает только committed state; ценой дополнительного HTTP
request и необходимости UI показать новую revision/Location. Append-only каждого draft edit
не требуется текущему contract; draft меняется на месте, approved history сохраняется.

V29 добавляет lineage/audit FK и status/audit CHECK, заменяет root guard без изменения V28.
DB запрещает forged cross-Brand/draft/missing lineage, mutable approved root content/audit,
DRAFT→LOCKED, APPROVED→DRAFT, новые APPROVED inserts и content replacement в том же root SQL
UPDATE, что approval. V28 collection guards запрещают approved collection mutations; deferred
validators проверяют committed aggregate. Runtime UPDATE расширен только status/audit columns;
lineage/version/ownership INSERT-only и root DELETE запрещён.

## Security Review

Защищаем tenant content, review history и связь approval с actor/time. Caller: OWNER с
membership; trust boundaries HTTP→DTO→application и application→PostgreSQL. Атакующий может
подставить чужой ID/actor, использовать stale claims/token, переписать approved data или
соревноваться с revocation/archive. Каждый сценарий имеет checks и negative tests ниже.
При обходе HTTP guards продолжают действовать use-case authorization, scoped lookup, domain,
DB constraints и runtime privileges. При компрометации shared runtime credential DB не
доказывает JWT identity или сам факт чтения UI; необходимы operational access controls/audit.

Threat: IDOR/BOLA и forged actor/scope.
Protection: OWNER на controller/use-case, scope-first lookup, permitsWorkspaceScope и
isCurrentUser; Controller не связывает actorId из body. Foreign/missing одинаковый404.
Verification: H2/PostgreSQL HTTP401/403/404; direct bean forged actor/widened scope403.

Threat: stale role/activity/membership и race с account revocation.
Protection: Account lock перед parents; актуальные DB fields/membership перечитываются после
lock. JWT principal не заменяет actual authorization на approval.
Verification: invalid/absent/inactive/wrong-role/unbound actor403; PostgreSQL competing activity
и membership revocation ждут Account lock, не держат Workspace/Identity lock и отвергают approval.

Threat: lost update, concurrent edit/approve/archive, colliding version allocation.
Protection: expectedVersion/JPA @Version, fresh transaction, Account→Workspace→Brand→Identity
для approval; Workspace→Brand→Identity для edits; max+1 под Brand lock, lifetime cap1000.
Verification: edit/approve одним token допускает ровно один; approved parallel edits дают
versions2/3; create/edit/approval ждут Workspace archive и затем отказывают; cap блокирует branch.

Threat: mass assignment и подтверждение uncommitted/подменённого content.
Protection: VersionRequest принимает только token; actor/time server-controlled, approval не
меняет content; fresh persistence context, отдельная transaction и immutable DTO.
Verification: forged content/actor/time ignored, response подтверждает прежний content;
outer transaction с dirty unflushed Entity не влияет на approval; SQL combined root content/approval rejected.

Threat: audit/history tampering при обходе application.
Protection: FK/check/trigger immutable content/audit/metadata/lineage; collection guards и
restricted runtime grants. Existing V28 drafts сохраняют NULL audit/lineage.
Verification: raw SQL content/audit/reset/delete/invalid lineage отвергается; actual non-owner
runtime может approve/create branch, но не изменяет source/audit/lineage; populated V28→V29.

Authentication/TLS: существующий Clerk/Spring bearer-only contract не менялся; live JWT не
квалифицирован. CSRF/CORS/security chain unchanged; session cookie authentication отсутствует.
Injection: parameterized JPA, no feature shell/HTTP URL calls; test SQL table allowlist static.
Exposure/logging: DTO UUID/time/content, без Entity/private storage/subject/secrets/content logging;
common safe Problem Details. Abuse: cap1000/page100 и finite existing transaction/DB deadlines;
rate limits/tenant quotas/external audit остаются #87. Новых dependencies/plugins нет.

## Изменённые файлы

[Полный manifest](issue-121-lifecycle-files.md). Domain/application/DTO/controller, V29/runtime
permissions, OpenAPI, shared HTTP/domain/PG/concurrency/migration/runtime tests, ADR/API/plans
и qualification docs. V1–V28 checksums сохранены. Старые Strategy и другие OpenAPI paths/
schemas не изменены; изменён только Identity contract и добавлен approval route.

## Runtime Flow

```text
Bearer Request → Spring Security/Clerk → OWNER Controller → VersionRequest validation
    ↓
BrandIdentityUseCase.approve: principal actor/scope guard + fresh transaction
    ↓
Scoped Identity projection → Account lock → Workspace lock → Brand lock
    ↓
Actual DB active OWNER/membership → Identity lock → expectedVersion
    ↓
Committed domain content validation → server actor/time → Hibernate flush
    ↓
PostgreSQL audit/lifecycle constraints + deferred content validation → commit
    ↓
Immutable Details/Response → 200 или safe Problem Details
```

PATCH approved использует Workspace→Brand→source Identity, создаёт новый root/content с
lineage, commit возвращает201+Location. Account lock там не нужен: approval не выполняется.

## Тесты

Полный clean verify: BUILD SUCCESS,786 tests,0 failures,0 errors,3 Windows-only filesystem
skips. Independent Linux filesystem35/35 без skips закрывает эти scenarios. Domain3/3,
shared H2 HTTP11/11, PostgreSQL Identity23/23, populated Identity migration2/2, actual
provider/runtime suite10/10 прошли. Все mandatory PostgreSQL/MinIO/ClamAV/renderer/full-pipeline
report gates passed без skipped/failed reports. Python scripts10/10, renderer29/29.
Spotless, compilation, packaged JAR, JaCoCo checks прошли; LINE4778covered/294missed (94.20%).

Существенные warnings: existing Mockito/Byte Buddy dynamic agent/CDS warnings и Hikari
closed connections после teardown отдельных Testcontainers databases. Build не имеет
test failures; warnings не подавлялись. Эти local results не являются deployment certification.

Первый focused run35/35 и дополненный focused42/42 прошли без failures/errors/skips.
Все результаты ниже относятся к новому source, а не к прежнему e514ad7 baseline.

Фактически выполненные commands:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=BrandIdentityTest,BrandIdentityApiIntegrationTest,PostgreSqlBrandIdentityApiTest,PostgreSqlBrandIdentityMigrationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-121-lifecycle-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-121-lifecycle-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
git diff --check
```

Packaged application запущен скрытым Start-Process с exact arguments:

```text
java -jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local --server.address=127.0.0.1 --server.port=18089
```

Invoke-WebRequest проверил public health200 и пять Identity operations (create/list/read/PATCH/
approval)401 без JWT. Остановлен только созданный test process. PostgreSQL context startup
и Hibernate validate проверены full integration suites; live Clerk happy path не проверен.

Reports: target/surefire-reports и target/site/jacoco; временные logs `%TEMP%/cadence-121-approval-*.log`,
`cadence-121-lifecycle-*.log`, не коммитятся. Существенные warnings перечислены с фактическими
результатами; новые warnings не подавлялись.

## Security Tests

Anonymous approval401; reserved roles403 HTTP/direct bean. Foreign/missing404. Invalid token400,
stale/replay409. Client actor/time/content ignored. Forged direct actor/widened scope403. Actual
wrong-role/inactive/no-membership/missing accounts403. SQL forged actor/backdated time/combined
content and approval/immutable audit/approved collections/reset/lineage attacks rejected.
Concurrent edit/approve ровно один accepted; revocation/archive lock ordering и independent
approved branches проверены PostgreSQL. Actual restricted runtime tested после latest grants.

## Edge Cases

V28 populated draft сохраняет version1, lockVersion7, existing RGB/caption/collections и NULL
audit/lineage. Legacy signature не backfill. New branch max+1, audit=NULL, lockVersion0, source
untouched. Domain запрещает reapprove/edit approved, invalid time/actor/version/draft branching.
LOCKED state поддерживает branch и immutable history; public lock endpoint не добавлен.
Approved source при1000 roots не создаёт1001-й branch. Archived parents/read history сохранены.

## Непроверенные аспекты

Source local only: push/exact-SHA CI после всего #121, remote результат baseline не доказательство.
Live Clerk/production adapters/citations/approved Strategy source/UI review отсутствуют в этом
scope. Host enforcing AppArmor/kernel preflight, deployed IAM/TLS/cloud/collation/clock skew,
production-size migrations/load/backup/recovery и external audit qualification отдельно.

DB audit подтверждает schema constraints/связь actor с tenant на approval; это не криптографическое
доказательство HTTP identity при компрометации runtime credentials. Raw SQL state guards не
заменяют parent/account-first protocol всех application writers. Production runtime должен
использовать grants, а migration identity владеть schema отдельно.

## Что мне важно понять

Approval — переход состояния уже сохранённого content. Если одновременно принимать editable
content, невозможно отделить reviewed revision от только что присланной. Поэтому frontend
сначала PATCH, получает token, показывает content, затем отправляет отдельный approval.

Связь revisedFromId хранит происхождение draft, но не делает его уже approved. Новый branch
получает NULL audit и должен пройти отдельный review. Source token не увеличивается при
создании branch, поскольку source не меняется; два branches с одного source допустимы.

Principal может содержать старые права: Account lock и actual DB authorization защищают approval
после revocation. Проверка только role из principal дала бы ошибочное разрешение. DB guards
дополняют application checks и сохраняют историю при ошибке другого writer.

## Что мне стоит изучить

1. Оптимистическая блокировка (optimistic locking): token означает конкретную редакцию draft.
2. Происхождение ревизии (revision lineage): revisedFromId связывает новый draft с immutable source.
3. Свежий контекст сохранения (fresh persistence context): REQUIRES_NEW не утверждает dirty outer Entity.
4. Порядок блокировок (lock ordering): Account→Workspace→Brand→Identity предотвращает проверенные races.
5. Журнал подтверждения (approval audit): actor/time назначаются сервером и становятся immutable.

## Production considerations

Перед deployment нужны reviewed migrations→runtime grants activation, backups/PITR/recovery,
clock synchronization, actual JWT/membership/cloud/TLS/IAM, load/rate/retention controls и audit sink.
Approval не означает готовность AI workflow: citations и immutable approved source integration
должны быть завершены и qualified. Real adapters #120 и durable AI Jobs/budgets отдельные gates.

Факты: выполненные tests/inspection и local commit, точные results выше.
Инженерный вывод: fresh transactions, source-preserving branches и locks предотвращают проверенные races.
Assumptions: все runtime writers соблюдают reviewed lock protocol и grants, production bearer-only.
Не подтверждены: новый remote CI, live/cloud/UI/load/recovery/collation/clock qualification и остальной #121.
