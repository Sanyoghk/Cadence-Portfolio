# Задание15 — Content generation / regeneration Jobs

Qualification: **QUALIFIED LOCAL**, final full BUILD SUCCESS2026-10-16T07:01:21+01:00. Ветка `mvp`, исходный clean HEAD
`2c8a2b63563519d14db9d9f1c675c3ecb81ff9bb`. Prerequisites10–14 проверены по code/ADR/reports;
их implementation не повторялся. Actual GitHub #98/#115/#107 прочитаны до реализации.

## Что было сделано

Content generation подключена к существующим durable AI Jobs. Exact approved Strategy и выбранные
slots дают editable Posts в DRAFT с original model/prompt/source/attempt provenance. Regeneration
создаёт новую DRAFT revision того же Post; старые approval и provider receipts сохраняются.
Production adapter disabled, deterministic local/test adapter явно demo. Настоящих assets,
moderation verdict, measured usage, live cost или privacy decisions не добавлено.

Технический contract: [ADR0032](adr/0032-content-generation-jobs.md), [API](content-generation-api.md).
Задания16+ не реализовывались. #98/#115 остаются open для более широких acceptance criteria.

## Как это работает

Сервер фиксирует exact full APPROVED Strategy ID/revision/lockVersion/approval/checksum, proven
original Identity lineage, latest exact approved Identity и актуальную evidence eligibility.
Следующая Strategy revision не подменяет captured source. Новая Identity draft делает AI capture
неактуальным по ADR0026/32. Manual/legacy Strategy без доказуемой Identity lineage отклоняется для
AI; существующий manual Post workflow14 остаётся доступным. Provenance не backfill.

`ContentGenerationCommand` содержит approved signature/Playbook, bounded selected local slot IDs
и captured regeneration baseline. Private tenant/source/storage IDs не попадают в model command;
есть только server-issued correlation UUID и local IDs внутри Playbook. Raw evidence не отправляется
повторно. Команда не содержит fetch/tools/credentials. Model output обязан точно совпасть с набором
slots, platform и PostContent bounds. Input/private snapshot/output каждый≤64KiB UTF-8.

CONTENT — новый operation kind с private storage schema3. Старые analysis1/Strategy2 JSON/checksums
не переписываются. Worker использует те же claims, attempt witnesses, deadlines, budgets и executor.
External I/O вне DB transactions; до egress и publication повторяются access/source/physical checks.
Затем один atomic commit создаёт batch receipt, Posts/revisions и completion witnesses.
Если любая проверка не проходит, rollback отменяет весь набор inserts и status transitions.
Port — Java interface ContentGenerationService: coordinator не зависит от HTTP/SDK будущего API.

Regeneration field/all всегда добавляет revision: APPROVED остаётся immutable, старый DRAFT получает
SUPERSEDED. Сервер merge только selected field из validated full output с frozen baseline и снова
валидирует весь Post. Original output и applied field сохраняются отдельно, unchanged human fields
не выдаются за новый model output. Generated editedBy/At NULL до human edit; requester/publication
time, provider/model/prompt, operation/attempt и checksums остаются в истории.

Human approval — отдельный existing Post command с expectedVersion, без egress. Historical approval
не является постоянным разрешением для будущей AI/delivery операции: consumers16/18 должны определить
свои current eligibility checks. История остаётся читаемой после withdrawal, без нового AI egress.

## Архитектурное решение

| Выбор | Причина и trade-off |
| --- | --- |
| Reuse durable generation coordinator, новый CONTENT kind | Единые recovery/budget/lease правила; source-specific capture/publication вынесены отдельно. Ingest queue не объявляется AI Jobs |
| Immutable capture + exact approved lineage | Исключает silent source replacement и legacy provenance fabrication; manual Strategy без lineage не eligible для AI |
| Новая revision при любой regeneration | Сохраняет original human/provider history и approval; требует SUPERSEDED и deferred SQL completeness guards |
| Full validated provider item + server field merge | Один строгий provider format; немного больше output, зато provider не управляет изменением других полей |
| Отдельный batch receipt + revision FK/slot links | Batch immutable, original output в sealed operation; не дублируются самостоятельные mutable item receipts |
| Adapter disabled / isolated deterministic demo | Backend можно квалифицировать до выбора API; marketing quality, real costs/moderation/asset success не выдумываются |

Слои: strict HTTP DTO/controller → secured use case → coordinator/source policy/publication → domain
values/entities → repositories → PostgreSQL constraints/triggers. Новых dependencies нет.
V1–V47 unchanged. V48 расширяет kinds/status/audit и добавляет insert-only receipt/provenance links.
Runtime grants отдельно ограничивают history/provenance. Canonical Playbook checksum вычисляет Java;
SQL независимо проверяет IDs/versions/approval/lineage, output SHA и content/slot bindings, не дублирует
Jackson canonical encoder. Это соответствует разделению checks, уже принятому в14.

## Security Review

Защищаем creator content, tenant/source history, approval и durable accounting. Callers — active
OWNER с actual membership. Trust boundaries: HTTP, persisted user content, model response и worker
handoff. Frontend/role claims не являются единственной защитой; обход Java не отменяет SQL guards.

Threat: IDOR/BOLA, forged OWNER/actor, foreign result/source, mass assignment.
Protection: scoped queries, authenticated actor binding, actual DB active/role/membership, strict
DTO allowlist; provider output не принимает resource ownership/approval IDs.
Verification: anonymous/EDITOR/VIEWER/forged actor/actual role revocation, foreign result/source и
unknown fields tests; runtime role flow и SQL bypass negatives.

Threat: model-selected foreign slots, malformed output, prompt/command injection.
Protection: server-selected immutable command, exact output slot set/channel/PostContent validation,
64KiB cap; data-only port без tools/fetch/arbitrary backend commands. Source text не исполняется.
Verification: strict provider JSON, missing/wrong schema/unknown fields, slot mismatch, malformed and
oversized batch; existing malicious evidence instructions проходят только как data, withdrawal
не позволяет повторный egress. Existing SSRF/public-IP/retained artifact policies сохранены.

Threat: source/access/Identity/target revision changes while provider runs; lost/stale lease.
Protection: parent-first Account→Workspace→Brand→Identity/evidence→Strategy→Post ordering, fresh
frozen capture equality, physical verification вне locks, operation/job/attempt fencing перед
publication, SQL lease/deadline/current source checks и atomic batch completeness.
Дополнительно устранена actor ambiguity при двух OWNER regeneration Jobs: supersession guard
берёт actor только из batch, вставленного текущей transaction (xmin), а не из произвольного
RUNNING Job. Иначе возможен обратный Account lock и deadlock при конкурирующем OWNER.
Verification: regression удерживает второй Account под row lock и требует успешной publication
текущего actor; provider I/O вне transaction; withdrawal before egress/during call, access/Identity/
Post edit races, two claimers, duplicate delivery, real JVM crash/restart and publication rollback.

Threat: replay/duplicate billing, uncertain provider outcome, resource exhaustion.
Protection: actor/workspace/kind/key + normalized complete request digest; existing durable shared
reservation/settlement and limits; unknown dispatched spend stays HELD_UNKNOWN. Bounded executor2,
1–84 slots, ≤3 attempts/60s deadline, lease≤20s, history1000/Post и10000Posts/Brand.
Verification: concurrent replay one operation/reservation, changed payload conflict, provider failure
without retry, full budget regression suite и aggregate output overflow. Synthetic ≠ measured usage.

Threat: history/audit rewrite or excessive data exposure/logging.
Protection: immutable provider receipt/output, PostgreSQL FK/unique/check/deferred triggers и runtime
INSERT-only provenance; generated content separate from human edits/approval; scoped no-store DTO;
existing safe error codes/correlation counters без prompts/secrets/provider diagnostics.
Verification: complete runtime publish→regenerate→edit→approve, forbidden receipt/provenance writes,
legacy migration без fake audit и prior Post API compatibility. Test JVM fixture не packaged.

## Изменённые файлы

| Файл | Назначение |
| --- | --- |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Actual статус15, зависимости и remaining16–18 без закрытия широких issues. |
| [DEVELOPMENT_PLAN_RU.md](../DEVELOPMENT_PLAN_RU.md) | Та же синхронизация русской версии плана. |
| [docker/postgres/runtime-permissions.sql](../docker/postgres/runtime-permissions.sql) | Запрещает UPDATE/DELETE Content receipts и сохраняет INSERT-only provenance. |
| [docs/adr/0032-content-generation-jobs.md](adr/0032-content-generation-jobs.md) | ADR source lifetime, contracts, decisions, alternatives и threat model15. |
| [docs/ai-jobs-recovery-runbook.md](ai-jobs-recovery-runbook.md) | Content receipt diagnostics и schema3/worker upgrade/recovery rules. |
| [docs/astra-medium-mvp-prompts.md](astra-medium-mvp-prompts.md) | Execution note15, actual prerequisites и remaining tasks. |
| [docs/content-generation-api.md](content-generation-api.md) | Submission/result/regeneration examples, bounds, statuses, rollout и remaining scope. |
| [docs/mvp-prompt-progress.md](mvp-prompt-progress.md) | Qualified local progress и честные broad issue gaps. |
| [docs/prompt15-content-generation-report.md](prompt15-content-generation-report.md) | Этот подробный отчёт, фактические проверки и file manifest. |
| [scripts/test_verify_postgres_report.py](../scripts/test_verify_postgres_report.py) | Проверяет новый обязательный report в report-gate tests. |
| [scripts/verify_postgres_report.py](../scripts/verify_postgres_report.py) | Новый mandatory Content PostgreSQL suite, missing/skipped report запрещён. |
| [src/main/java/com/cadence/application/ports/ContentGenerationCommand.java](../src/main/java/com/cadence/application/ports/ContentGenerationCommand.java) | Immutable bounded model-facing signature/Playbook/slot/baseline input. |
| [src/main/java/com/cadence/application/ports/ContentGenerationResult.java](../src/main/java/com/cadence/application/ports/ContentGenerationResult.java) | Strict versioned provider output, exact slot/channel validation и provenance. |
| [src/main/java/com/cadence/application/ports/ContentGenerationService.java](../src/main/java/com/cadence/application/ports/ContentGenerationService.java) | Provider-neutral Content generation port. |
| [src/main/java/com/cadence/application/repositories/ContentGenerationBatchRepository.java](../src/main/java/com/cadence/application/repositories/ContentGenerationBatchRepository.java) | Persistence insert-only batch receipts. |
| [src/main/java/com/cadence/application/repositories/PostRevisionRepository.java](../src/main/java/com/cadence/application/repositories/PostRevisionRepository.java) | Bounded receipt → exact generated revision/slot links. |
| [src/main/java/com/cadence/application/results/PostDetails.java](../src/main/java/com/cadence/application/results/PostDetails.java) | Необязательные generation links; manual JSON shape сохранён. |
| [src/main/java/com/cadence/application/services/AiGenerationService.java](../src/main/java/com/cadence/application/services/AiGenerationService.java) | CONTENT submission/replay/result, port execution и fenced atomic publication reuse. |
| [src/main/java/com/cadence/application/services/AiGenerationSnapshot.java](../src/main/java/com/cadence/application/services/AiGenerationSnapshot.java) | Private schema3; backward decode old analysis1/Strategy2. |
| [src/main/java/com/cadence/application/services/AiGenerationSources.java](../src/main/java/com/cadence/application/services/AiGenerationSources.java) | Reuse approved Identity/evidence capture для Content; fresh scalar DB membership. |
| [src/main/java/com/cadence/application/services/ContentCapture.java](../src/main/java/com/cadence/application/services/ContentCapture.java) | Private immutable exact Strategy/Identity context и request provenance. |
| [src/main/java/com/cadence/application/services/ContentPublication.java](../src/main/java/com/cadence/application/services/ContentPublication.java) | Atomic batch → new Posts/DRAFT revisions; field merge и DRAFT supersession. |
| [src/main/java/com/cadence/application/services/ContentSourcePolicy.java](../src/main/java/com/cadence/application/services/ContentSourcePolicy.java) | Exact approved Strategy/Identity lineage, latest target/version/slot checks. |
| [src/main/java/com/cadence/application/services/GenerationBudgets.java](../src/main/java/com/cadence/application/services/GenerationBudgets.java) | CONTENT adapter availability; тот же shared durable ledger. |
| [src/main/java/com/cadence/application/usecases/GenerateContentCalendarUseCase.java](../src/main/java/com/cadence/application/usecases/GenerateContentCalendarUseCase.java) | Secured application boundary с actor/scope checks. |
| [src/main/java/com/cadence/domain/AiGenerationOperation.java](../src/main/java/com/cadence/domain/AiGenerationOperation.java) | Новый CONTENT kind; old statuses/history contract сохранены. |
| [src/main/java/com/cadence/domain/ContentGenerationBatch.java](../src/main/java/com/cadence/domain/ContentGenerationBatch.java) | Immutable operation/attempt/output-checksum receipt entity. |
| [src/main/java/com/cadence/domain/ContentGenerationRequest.java](../src/main/java/com/cadence/domain/ContentGenerationRequest.java) | Normalized selected slots и server-defined regeneration field merge. |
| [src/main/java/com/cadence/domain/ContentPost.java](../src/main/java/com/cadence/domain/ContentPost.java) | Immutable original outline slot binding для generated identity. |
| [src/main/java/com/cadence/domain/GenerationReservation.java](../src/main/java/com/cadence/domain/GenerationReservation.java) | CONTENT допускается в существующем budget contract. |
| [src/main/java/com/cadence/domain/PostContent.java](../src/main/java/com/cadence/domain/PostContent.java) | Strict unknown provider fields через existing value interface. |
| [src/main/java/com/cadence/domain/PostRevision.java](../src/main/java/com/cadence/domain/PostRevision.java) | Generated DRAFT factory, separate human edit audit, SUPERSEDED и append-only regeneration. |
| [src/main/java/com/cadence/infrastructure/content/ContentGenerationConfiguration.java](../src/main/java/com/cadence/infrastructure/content/ContentGenerationConfiguration.java) | Production disabled, deterministic demo fake только local/test. |
| [src/main/java/com/cadence/presentation/ContentGenerationController.java](../src/main/java/com/cadence/presentation/ContentGenerationController.java) | Strict DTO/202+Location, regeneration и scoped no-store result API. |
| [src/main/resources/application-local.properties](../src/main/resources/application-local.properties) | Явно изолированный Content fake для local. |
| [src/main/resources/application.properties](../src/main/resources/application.properties) | Default disabled Content adapter setting. |
| [src/main/resources/db/migration/V48__content_generation_jobs.sql](../src/main/resources/db/migration/V48__content_generation_jobs.sql) | Receipt/kind/status/provenance migration; independent source/fencing/history/output/batch guards. |
| [src/main/resources/openapi/cadence.json](../src/main/resources/openapi/cadence.json) | OpenAPI новых маршрутов/DTO/CONTENT/SUPERSEDED и additive Post provenance. |
| [src/test/java/com/cadence/domain/ContentGenerationContractTest.java](../src/test/java/com/cadence/domain/ContentGenerationContractTest.java) | Field merge matrix, request bounds и strict provider JSON negatives. |
| [src/test/java/com/cadence/infrastructure/content/ContentGenerationConfigurationTest.java](../src/test/java/com/cadence/infrastructure/content/ContentGenerationConfigurationTest.java) | Fail-closed production/profile checks и deterministic demo. |
| [src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java](../src/test/java/com/cadence/persistence/PostgreSqlAiRecoveryProcessTest.java) | Crash/restart matrix теперь проверяет и CONTENT, включая batch rollback/count. |
| [src/test/java/com/cadence/persistence/PostgreSqlContentGenerationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlContentGenerationTest.java) | Real API/worker/runtime/concurrency/source/withdrawal/overflow/history qualification. |
| [src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java) | Actual48 migrations в schema inventory. |
| [src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlPostMigrationTest.java) | Legacy migration дополнительно доказывает отсутствие invented generation receipts. |
| [src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java](../src/test/java/com/cadence/persistence/PostgreSqlUtcMigrationTest.java) | Две новые receipt timestamps в UTC schema inventory. |
| [src/test/java/com/cadence/support/AiRecoveryProcess.java](../src/test/java/com/cadence/support/AiRecoveryProcess.java) | Test-only Content child-JVM provider crash fixture; не packaged. |
| [src/test/java/com/cadence/support/PostApiContract.java](../src/test/java/com/cadence/support/PostApiContract.java) | Fixture использует application DB clock вместо host clock; assertions/SQL guards сохранены. |
| [src/test/resources/application-test.properties](../src/test/resources/application-test.properties) | Isolated test Content fake. |

## Runtime Flow

```text
Bearer request + strict DTO + Idempotency-Key
  → ContentGenerationController → GenerateContentCalendarUseCase
  → AiGenerationService.submitContent / regenerate
  → actual actor/parents + AiGenerationSources.captureContent + ContentSourcePolicy
  → schema3 operation + AI_GENERATION Job + durable budget reservation → commit202
  → worker claim / exact attempt+lease / physical verification
  → durable dispatch intent + held accounting
  → ContentGenerationService.generate outside transaction
  → validate exact bounded output / trusted synthetic usage record
  → fresh physical/source/access/target/lease/deadline checks
  → ContentPublication → batch receipt + DRAFT revisions + completion witnesses
  → PostgreSQL deferred seals → one atomic commit
  → scoped Job/result DTO → existing human edit / separate approval
```

## Тесты

| Проверка | Фактический результат |
| --- | --- |
| Final full build |148 suites /1554 tests:1551 passed,0 failures/errors,3 Windows filesystem skips |
| PostgreSQL Content / real JVM recovery |25/25 Content,8/8 crash/restart,0 skips |
| Prior Post API / migration |29/29 PostgreSQL Post API; populated migration и UTC/schema inventory passed |
| Spotless / JaCoCo |PASS;94.53% LINE /71.91% BRANCH, all configured coverage checks met |
| Mandatory report gates |PostgreSQL, MinIO, ClamAV, renderer, full evidence pipeline: PASS без skipped required suites |
| Linux filesystem / renderer Python |35/35 без skips на final compiled classes; renderer29/29 |
| Python report-gate tests |10/10 |
| Packaged startup |Health200;9 protected anonymous requests401;discovery401/Bearer;own process stopped/port released |
| Fresh SBOM / OSV |194 Maven dependencies,0 findings; fresh current SBOM exact inventory matched scan |
| Reproducibility |592 exact source/build file hashes unchanged during final full + SBOM; test launcher absent from JAR |

Точные выполненные qualifying commands (overlapping targeted runs не суммируются как unique tests):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlContentGenerationTest,ContentGenerationContractTest,ContentGenerationConfigurationTest,PostgreSqlPostApiTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=JsonCompatibilityTest,PostgreSqlContentGenerationTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlContentGenerationTest#supersessionUsesCurrentBatchActorWhileOtherOwnersAccountIsLocked,JsonCompatibilityTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
python -X utf8 var/prompt15-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt15-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt15-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt15-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\prompt15-startup.ps1
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt14-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt15-verification/osv-results.json
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt15-verification/osv-results.json
git diff --check
```

Targeted61/61 → compatibility26/26 → cross-owner3/3 passed; final full1554 включает все эти
scenarios и добавленные source negatives. Cache8 ограничивает только число retained Spring test
contexts; тесты/fixtures/assertions не выключались. Final full занял19:00min. Deprecation warnings
existing Jackson2 compatibility/S3/Testcontainers APIs и Hikari warnings для уже остановленных test
containers не failures. CycloneDX offline attempt отклонён самим plugin (requires online); online
run successful с metadata-keyword warnings `meta:enum`/`deprecated`. Сначала сканировался unchanged
prior dependency inventory, затем свежий current SBOM подтвердил exact194 packages и clean report.

Локальная evidence: `var/prompt15-verification/summary.json`, copied Surefire/JaCoCo reports,
`source-manifest.json`, current `bom.cdx.json`/`osv-results.json`; raw logs — `var/prompt15-*.log`.
Это local qualification; новый remote CI source SHA не отправлялся.

Targeted development runs выявили ошибки test fixtures: Mockito restubbing вызывал старый Answer
с null matcher, MockMvc очищал SecurityContext, detached lazy Playbook читался вне transaction,
host timestamps иногда опережали database clock. Исправлены fixtures/orchestration tests;
production security checks/assertions не отключались.
Первый полный run1553 имел1 failure в JsonCompatibilityTest: nullable Content поле добавлялось
в reserialized old schema1 snapshot. Исправлена production serialization через NON_NULL только
для нового поля; existing exact compatibility assertion сохранён. Этот full run не считается pass.
Следующий full run остановлен после inspection concurrency gap в supersession actor selection;
после current-transaction batch binding добавлен cross-owner Account-lock regression test.
Остановленный run также не считается qualification. В Post fixture audit time теперь берётся
из injected database-authoritative Clock, как в application layer. Первый targeted run и ошибочные
recovery runs не считаются qualification. Raw logs сохранены локально в `var/prompt15-*.log`.

## Security Tests

Positive requested four-week slots/mixed Instagram-TikTok; exact replay и concurrent replay; two
workers/one result; field/all regeneration из DRAFT/APPROVED; human edit/approval; inherited manually
edited Strategy lineage; prior captured Strategy сохраняется при новой approval; complete runtime
role flow. Negative scenarios: anonymous, reserved/forged roles/actor, foreign source/result, unknown
writable/provider fields, missing/wrong output schema, invalid slot set/format, oversized aggregate,
stale revision/slot binding, manual/DRAFT/unprovable source, withdrawal/access/Identity/Post edit,
lost lease, provider unavailable/unknown outcome, immutable SQL history/provenance и crash/restart.

## Edge Cases

Структурно approved source у границы64KiB может превысить общий input/snapshot envelope вместе
с Identity/baseline: capture безопасно отклоняется без сохранённого Job/reservation, без truncation.
Новый DRAFT из APPROVED не переносит approval. Замена DRAFT не стирает human attribution старой
revision. FORMAT-only конфликт с preserved script безопасно отклоняется. Ошибка любого item
откатывает весь batch. После commit redelivery/restart сохраняет единственный receipt/result.
Publication rollback не отменяет уже известный synthetic usage. Неизвестный provider outcome
не превращается в safe retry/zero spend. Исторические partial Strategy/manual Posts не получают
выдуманный Identity/model audit; старые snapshots/checksums и TEXT/PDF/URL/social consumers сохранены.

## Непроверенные аспекты

Live AI/media provider, measured cost/latency/moderation, transmission/privacy/region/deletion policy,
production Clerk/IAM/TLS/load/backup restore и hosted Linux AppArmor runner не квалифицируются этим
local slice. Эти scopes остаются отдельными gates. Local Testcontainers/runtime fixtures не являются
доказательством production deployment readiness. Нового push/remote CI нет.

## Что мне важно понять

Post id задаёт identity материала, revisionId — конкретный content snapshot, lockVersion — защиту
от concurrent изменения, operation/attempt/lease — право worker опубликовать ответ. Эти значения
не взаимозаменяемы. Модель создаёт недоверенные creative fields; владение, source links и approval
назначает сервер. Проверка до call не защищает от отзыва прав во время call, поэтому publication
проверяется заново и SQL фиксирует всё атомарно. Receipt сохраняет исходный AI ответ отдельно от
последующего human edit и field merge.

## Что мне стоит изучить

1. Неизменяемый снимок (immutable snapshot) — захват input; worker продолжает именно original request.
2. Ограничение права worker по lease (lease fencing) — номер attempt и lease owner ограничивают право старого worker на publication.
3. Повтор без второго побочного эффекта (idempotency) — same scoped request/key возвращает existing operation без второго расхода.
4. Отложенный триггер целостности (deferred constraint trigger) — проверяет в конце transaction целостность всего batch/history.
5. Происхождение данных (provenance) — отдельные source/model/human audit объясняют происхождение content без fabrication.

## Production considerations

Остановить admission/workers на upgrade, применить V48 и grants migration identity, затем запускать
новые workers. Backup/restore и IAM/TLS/network sandbox квалифицировать отдельно.
Проверить time synchronization application/DB hosts: leases и audit используют DB-authoritative
Clock, а BaseEntity timestamps остаются на existing host clock; clock skew может дать safe отказ. Real adapter
должен подтвердить строгий output, neutral measured usage, uncertain reconciliation, privacy,
moderation и egress/deadline boundaries до activation. Distributed HTTP rate limits/operational
retention/hardening28 и scheduling16/assets17/export18 остаются в соответствующих scopes.
Не включать fake в prod; production guard отвергает даже сочетание local/test с prod.

## Факты, выводы и assumptions

Факты — actual inspection и только выполненные команды/результаты qualification выше.
Инженерный вывод — reuse общего coordinator/ledger и append-only revisions устраняет отдельный
класс duplicate/stale publication и history rewrite. Assumptions — future consumers выполнят
собственные eligibility checks; backend всё ещё не является production AI MVP без real adapters.

## Git / issues

Finished qualified slice сохраняется local commit `feat(content): qualify durable generation and regeneration`; exact SHA определяется `git log -1 --
docs/prompt15-content-generation-report.md`. Push15/CI/issue closure/merge/deploy не выполнялись.
#98/#115 шире task15: actual scheduling/DST, media/assets и safe export остаются16–18.
#121/#120 открыты по утверждённому backend-first/provider-last приоритету. Следующее16 не начато.
