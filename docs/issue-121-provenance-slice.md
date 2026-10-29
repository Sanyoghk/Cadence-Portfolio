# #121 — Identity DRAFT из проверенного analysis receipt

Дата: 2026-10-08, branch mvp, baseline af1c6fb. Это третий законченный local slice #121.
Qualification относится к сохранённому feature working tree после этого baseline; source
фиксируется отдельным local commit и указывается в #121/финальном ответе. Remote CI пока нет.

## Что было сделано

Creator может создать новый Identity DRAFT из текущего успешного Brand analysis. Сервер
сверяет source versions и checksum и сохраняет immutable связь с первоначальным receipt.
Read/list/edit responses показывают безопасное original generation provenance.
Это copy существующего результата, не новый AI call. Evidence citations и approved
Identity→Strategy остаются незавершёнными; #121/#107 Task2.3 IN PROGRESS.

## Как это работает

POST `/api/v1/brands/{brandId}/identities/from-analysis`, body `{"expectedVersion":1}`.
Token — Brand.lockVersion. Optional analysisId выбирает scoped receipt; без него сервер
выбирает один текущий SUCCEEDED receipt по versions, startedAt/id. Token из чужого/старого
состояния не позволяет выбирать иной source. Foreign/missing explicit receipt404,
no qualified/current source409. Content/provenance/status не принимаются из request.

Workspace→Brand locks, fresh REQUIRES_NEW/READ_COMMITTED transaction, active parent checks;
receipt.brandVersion+1=Brand.lockVersion и совпадение Workspace version. Bounded normalized
content copied из signature обязан иметь исходный outputChecksum. New root version=max+1,
DRAFT, cap1000; 201 + Location. Создание не idempotent. Human approval — отдельный command.

analysisProvenance: analysisId,provider,modelVersion,promptVersion,warnings,initialOutputChecksum,
generatedAt. Manual draft edit сохраняет ORIGINAL receipt, не приписывает новый content модели.
Approved branching получает revisedFromId и NULL provenance; старый source/audit сохраняются.
Checksum — SHA-256 length-prefixed UTF-8 fields/list counts; это integrity check, не подпись
модели. Receipt хранит versions/checksums, не полный replay snapshot prompts/input/cost/latency.

## Архитектурное решение

Сохранены existing layers и analysis API. Explicit copy не создаёт Identity при каждом legacy
analyze и не меняет его response. Provider-neutral port не расширен выдуманными citations:
нынешний model input — только declared brandName/industry. UI может создать copy без знания
внутреннего ledger ID. External I/O отсутствует в этом use case.

V30 добавляет nullable INSERT-only analysis_attempt_id/FK, single-origin constraint,
same-Brand/SUCCEEDED/current source guard, immutable origin и deferred initial-content
checksum validator после collection flush. PostgreSQL core sha256 не требует pgcrypto или
новой dependency. Составной index ограничивает receipt lookup; stale default tokens
отклоняются до history query. Runtime provenance UPDATE не получает grants.

Trade-offs: draft edits меняются на месте, поэтому receipt описывает initial output, не весь
manual edit history. Legacy palette normalization, меняющая checksum, приводит к отказу,
а не переименованию старого receipt. V1–V29 и legacy/manual content не backfill.
Полные evidence snapshots/citations и AI Jobs/budgets требуют отдельного следующего scope.

## Security Review

Защищаем tenant content и происхождение identity. Caller — authenticated OWNER с server
Workspace scope. Trust boundaries: HTTP DTO→application, signature/receipt→domain и application→DB.
При обходе controller остаются method security, scoped lookup, domain checks и PostgreSQL guards.

Threat: IDOR/BOLA, foreign receipt, forged scope/role или mass assignment.
Protection: scoped Brand lookup до source lookup, same-Brand receipt selection, OWNER controller/
use-case, permitsWorkspaceScope. Request DTO не содержит writable content/provenance/approval.
Verification: H2/PG HTTP401/403/404, direct reserved roles/widened scope denied; extra content ignored.

Threat: stale source/archive race и выдуманная attribution.
Protection: parent-first locks, actual source versions/status/checksum, fresh transaction,
original immutable receipt и separate approval. DB самостоятельно сверяет первоначальный output.
Verification: stale/running/failed/missing source, archived parents, PostgreSQL competing archive,
raw SQL foreign receipt и mismatched initial content rejected; Unicode checksum matches.

Threat: history/origin tampering и ложная новая generation при branch/edit.
Protection: nullable FK + immutable association, terminal immutable analysis ledger, INSERT-only
runtime privilege; edits явно сохраняют initial receipt, branches получают только lineage.
Verification: raw SQL attach/remove origin denied, manual edit/approval/branch preserves source;
restricted runtime creates valid copy but provenance UPDATE получает permission denied.

Threat: resource exhaustion и sensitive-data exposure.
Protection: current-token check before automatic lookup, LIMIT1/index, bounded content/page/cap1000;
safe DTOs/errors без prompts/private references/secrets. No provider/network calls, no new dependencies.
Verification: full relevant suites/report gates, existing pagination/cap/negative tests и diff review.
Production request rate limits/admission budgets остаются #87; cap не заменяет rate limiting.

Shared runtime credentials не являются криптографическим доказательством вызова AI provider:
их компрометация требует operational/audit защиты. Approval actual DB membership checks
сохранены; copy использует существующий OWNER/server-scope creation contract. Security chain,
CSRF/CORS и bearer-only authentication не менялись; live Clerk не квалифицирован.

## Runtime Flow

```text
Bearer → Spring Security → OWNER Controller → validated IdentityFromAnalysisRequest
    → scoped Brand lookup → Workspace lock → Brand lock → active parents
    → current successful receipt selection / explicit scoped receipt
    → source version/status checks → normalized content / canonical checksum
    → BrandIdentity.fromAnalysis → revision allocation / DRAFT save
    → PostgreSQL FK/origin/source guards + deferred content checksum → commit
    → immutable response/provenance DTO → 201 + Location
```

## Тесты

Фактически выполнены:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=BrandIdentityTest,BrandIdentityApiIntegrationTest,PostgreSqlBrandIdentityApiTest,PostgreSqlBrandIdentityMigrationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
git diff --check
```

Окончательный full: **BUILD SUCCESS, 96 suites,801 tests,0 failures/errors,3 Windows skips**;
03:42, finished2026-10-08T22:04:26+01:00. Linux35/35 без skips покрывает три symlink scenarios.
Identity domain5/5,H2HTTP16/16,PostgreSQL30/30,migrations3/3; full provider/runtime10/10.
Python10/10,renderer29/29; все обязательные PostgreSQL/MinIO/ClamAV/renderer report gates PASS.
Compilation/JAR/Spotless/JaCoCo PASS; LINE4867covered/296missed =94.27%.

Focused промежуточного source54/54 passed. Первый focused run нашёл ошибки новых fixtures:
MockMvc очищал SecurityContext перед direct bean call, а anonymous test унаследовал owner.
Fixtures исправлены explicit context setup/cleanup; assertions/authorization не ослаблены.
Промежуточные full runs остановлены для final API/security review; они не считаются passes.
Final counts выше относятся к окончательному source с server selection/index/token guard.

Startup: hidden Java process с `-jar target/cadence-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
--server.address=127.0.0.1 --server.port=18089`; readiness timeout и finally остановка только
созданного процесса. Health200 и новый anonymous from-analysis endpoint401 проверены.

Ignored logs: var/test-run-2026-10-08/identity-provenance-*.log; Surefire XML и JaCoCo — target.
Warnings: existing JVM CDS/bootstrap classpath и Hikari closed test connections; compile
deprecated/unchecked test warnings. Они не подавлялись; test failures отсутствуют.

## Security Tests и Edge Cases

Проверены no receipt/running/failed/stale/foreign/missing source, invalid/null/negative request
token, roles и forged scope, client content spoofing, same-Brand linkage, checksum mismatch,
immutable source attach/remove, runtime privilege denial, archive lock wait и post-archive denial.
Нормальная copy, server-selected copy, Unicode Māori/emoji checksum, edit→approval→branch
и read original receipt прошли. Populated V29→V30 сохраняет existing draft с NULL origin;
старые populated V27/V28 upgrade tests также проходят. V1–V29 inspection unchanged;
existing OpenAPI paths/non-Identity schemas unchanged. LIMIT/cap и прежний approval contract сохранены.

## Изменённые файлы

- [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md): current scope update.
- [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md): current scope update.
- [README.md](../README.md): workflow discovery.
- [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql): INSERT-only provenance comment.
- [docs/adr/0012-brand-identity-drafts.md](../docs/adr/0012-brand-identity-drafts.md): design and trade-offs.
- [docs/brand-identity-api.md](../docs/brand-identity-api.md): API contract.
- [docs/issue-121-provenance-slice.md](../docs/issue-121-provenance-slice.md): qualification report and manifest.
- [docs/verification.md](../docs/verification.md): qualification boundaries.
- [docs/issue-sync.md](../docs/issue-sync.md): current partial issue scope and synchronization.
- [src/main/java/com/cadence/application/repositories/BrandAnalysisAttemptRepository.java](../src/main/java/com/cadence/application/repositories/BrandAnalysisAttemptRepository.java): scoped/bounded source selection.
- [src/main/java/com/cadence/application/results/BrandIdentityDetails.java](../src/main/java/com/cadence/application/results/BrandIdentityDetails.java): original provenance snapshot.
- [src/main/java/com/cadence/application/results/IdentityAnalysisProvenance.java](../src/main/java/com/cadence/application/results/IdentityAnalysisProvenance.java): immutable safe receipt DTO.
- [src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java](../src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java): transactional receipt-copy command.
- [src/main/java/com/cadence/domain/BrandAnalysisAttempt.java](../src/main/java/com/cadence/domain/BrandAnalysisAttempt.java): completedAt getter.
- [src/main/java/com/cadence/domain/BrandIdentity.java](../src/main/java/com/cadence/domain/BrandIdentity.java): guarded factory and immutable origin association.
- [src/main/java/com/cadence/domain/BrandIdentityContent.java](../src/main/java/com/cadence/domain/BrandIdentityContent.java): canonical UTF-8 checksum.
- [src/main/java/com/cadence/presentation/BrandIdentityController.java](../src/main/java/com/cadence/presentation/BrandIdentityController.java): OWNER-only route.
- [src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java](../src/main/java/com/cadence/presentation/dto/BrandIdentityResponse.java): safe response provenance.
- [src/main/java/com/cadence/presentation/dto/IdentityFromAnalysisRequest.java](../src/main/java/com/cadence/presentation/dto/IdentityFromAnalysisRequest.java): dedicated input DTO.
- [src/main/resources/db/migration/V30__identity_analysis_provenance.sql](../src/main/resources/db/migration/V30__identity_analysis_provenance.sql): FK/origin guards/checksum/indexes.
- [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json): route and schema contracts.
- [src/test/java/com/cadence/domain/BrandIdentityTest.java](../src/test/java/com/cadence/domain/BrandIdentityTest.java): domain/checksum negatives.
- [src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java](../src/test/java/com/cadence/infrastructure/storage/FullEvidenceProviderIntegrationTest.java): actual runtime permission proof.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityApiTest.java): raw SQL/concurrency negatives.
- [src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlBrandIdentityMigrationTest.java): populated V29 upgrade.
- [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java): exact V1-V30 inventory.
- [src/test/java/com/cadence/presentation/OpenApiContractTest.java](../src/test/java/com/cadence/presentation/OpenApiContractTest.java): new DTO schema inventory.
- [src/test/java/com/cadence/support/BrandIdentityApiContract.java](../src/test/java/com/cadence/support/BrandIdentityApiContract.java): shared H2/PostgreSQL HTTP/security workflow.

## Непроверенные аспекты и Production considerations

Feature local only; push/exact-SHA CI после всего #121. No merge/deployment. Live Clerk/AI/OAuth,
cloud IAM/TLS, enforcing AppArmor host, load/backup/clock/retention qualification отдельно.
Новый dependency scan не запускался; dependencies не добавлены. Production runtime должен
использовать reviewed grants. Fixtures не доказывают real provider execution/privacy/cost.
До полноценной generation нужны evidence admission/snapshots/validated citations, approved
Identity→Strategy fencing, real adapters #120 и AI Jobs/budgets. CLEAN/FETCHED не повышены до READY.

## Что мне важно понять

Receipt нельзя «приклеить» к любому похожему content: source versions и bytes должны совпадать.
Checksum фиксирует первоначальный output; после human edit это всё ещё original receipt,
а не свидетельство новой model generation. Поэтому branch наследует историю через lineage,
но не копирует provider attribution. Database independently проверяет initial insertion,
а application locks не допускают source/archive change во время capture.

## Что мне стоит изучить

1. Происхождение данных (provenance): server-owned связь с immutable generation receipt.
2. Каноническое представление (canonical serialization): UTF-8 byte lengths/list counts дают
   одинаковый digest в Java/PostgreSQL, без ambiguous concatenation.
3. Отложенное ограничение (deferred constraint): сверяет root и коллекции в конце transaction.
4. Блокировка источника (source fencing): versions и ordered locks отвергают stale capture.
5. Разделение generation/edit (generation attribution): ручное изменение не должно менять
   историю того, что когда-то сделал provider.

Факты: окончательные tests/report gates/inspection/startup прошли, legacy contracts сохранены.
Вывод: текущий copy/provenance contract предотвращает проверенные source/history attacks.
Assumptions: server principal/runtime credentials и grants защищены; production infrastructure
и live-provider qualification ещё не доказаны. Whole issue не объявляется completed.
