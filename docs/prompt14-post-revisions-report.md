# Задание14 — Post identity/revisions

Status: **QUALIFIED LOCAL14**, 2026-10-16. Baseline: clean `mvp`,
`a283382c8191bfa4020d0fb3c141493cabb9f821`.
Prerequisite13 не повторялся. GitHub #107/#115/#103 прочитаны; scope14 — часть #115/#103,
а не всё calendar/generation/media/export. Local commit после qualification; no push/closure.

## Что было сделано

Созданы стабильная Post identity и отдельные revisions с Instagram/TikTok editorial content.
Creator выбирает exact complete APPROVED Strategy, создаёт DRAFT, редактирует с optimistic token,
отдельно approves; edit APPROVED создаёт новый DRAFT, сохраняя исходную approved history.
Legacy SocialPost сохраняется как явно incomplete read-only history без fabricated audit.

## Как это работает

`ContentPost` хранит Brand/platform и immutable source receipt. `PostRevision` содержит content,
revision number, state и actual actor/time для creation/edit/approval. `PostContent` проверяет
поля, hashtags и compatibility matrix. Application result `PostDetails` копирует lazy values внутри
transaction; controller не отдаёт JPA entities. DTO не принимает audit/provider/ownership fields.
Source checksum вычислен по full approved Strategy content, включая human edits; original model
receipt Strategy не переписывается. Source ID/version/approval также сохраняются отдельно.

`PostLifecycleUseCase` проверяет actual active OWNER и membership, active parents и exact source.
Mutations используют Account → Workspace → Brand → Strategy → Post identity locks и проверяют latest
revision + expectedVersion. Approved branch повторно не создаётся из старой revision. Reads scoped,
bounded и доступны для archived-parent history при actual permission. Read flags — не capability.

## Архитектурное решение

[ADR0031](adr/0031-post-revision-lifecycle.md) записан до V47. Отдельные normalized identity/revision
tables выбраны вместо mutation одной SocialPost: иначе стабильный ID и approved history конфликтуют.
JSON blob позволил бы меньше таблиц, но лишил бы FK/check/SQL history protections. Trade-off — join,
больше persistence mappings и отдельная migration legacy data. Dependencies не добавлены.

Manual workflow не обращается к model/evidence bytes; historical Strategy lineage не разрешает
новый model egress. Required AI guards/providers/jobs остаются15. Scheduling/DST16, media17 и CSV18
имеют отдельные acceptance boundaries. Source full APPROVED обязателен, но latest Strategy не
подставляется автоматически: явно выбранная revision сохраняется до конца Post history.

## Security Review

| Threat | Protection | Verification |
| --- | --- | --- |
| IDOR/BOLA/foreign source | scoped IDs, current DB account/membership, same Brand source | shared H2/PG API negatives, foreign/missing404, source-of-other-Brand rejection |
| stale OWNER claims / reserved role | bean+controller OWNER, locked active account and membership | EDITOR/VIEWER denied, disabled account/actual EDITOR/revoked membership |
| mass assignment / forged approval/provider | dedicated strict root/nested/approval DTO, server actor/time | unknown approvedBy/provider/source/actor/status/ownership rejected400 |
| history rewrite / lost update / replay | expectedVersion/latest checks, identity lock, one-DRAFT unique, immutable lineage | stale edit/approval/repeated branch, concurrent edit/approve/branch, SQL direct writes/deletes |
| archive/source races | parent-first locks, repeated exact source lifecycle/version/checksum check | mutation waits for archive then rejects, source LOCKED and corrupt checksum denied |
| SQL/command/SSRF injection | parameterized repositories, strings treated as data, no network/commands/model | malicious command/URL caption round-trip; no egress port used |
| resource exhaustion | body/filter limits, text/count/page bounds,10000 Posts/Brand,1000 revisions/Post | format/bounds/page/domain cap and SQL hashtag/count/order negatives |
| leaked content/secrets | DTO only necessary scoped data, safe Problem Details, no new content logs/credentials | error/unknown-field tests, inspection of handler/logging/configuration |
| runtime DB bypass | immutable identity/history triggers, source/audit/lineage guards, column grants | positive runtime draft edit/approval; forbidden source/history/delete writes |

Authentication uses existing Bearer-only Spring Security; cookie/session auth не добавляется,
поэтому existing CSRF rationale остаётся применимым. SecurityFilterChain/CORS не изменялись.
XSS: backend хранит plain text; будущий UI должен выводить escaped text. CSV formula injection
предотвращается consumer18, а не произвольным удалением символов из пользовательского content.
Ни новые secrets, ни provider credentials, ни dependencies не появились. DB role не заменяет
per-request authorization: runtime connection не идентифицирует human actor автоматически.

## Изменённые файлы

Полный перечень каждого source/test/docs/config file и назначение:
[manifest14](prompt14-changed-files.md). Old Flyway V1–46 checksums сохранены.

## Runtime Flow

```text
Bearer request → Spring Security / OWNER → PostController / strict DTO
→ PostLifecycleUseCase / scoped resolve / locked actual account+membership
→ Workspace → Brand → exact APPROVED Strategy → Post identity
→ latest revision / expectedVersion → PostContent / PostRevision
→ repositories → V47 FK/check/trigger guards → atomic commit
→ PostDetails (immutable snapshot) → HTTP201/200
```

Approval выполняется отдельным request и повышает lockVersion. Branch получает новый revisionId,
следующий revisionNumber и lockVersion0; identity id и source receipt сохраняются. Нет I/O под locks.

## Тесты

Executed targeted:56/56, failures/errors/skips0 (Post API/domain/PostgreSQL migration/runtime/races
и baseline persistence/UTC). Последующий targeted56/56 включал TikTok/Unicode и OpenAPI.
Final full run дополнительно включает source lifecycle, corrupt-source/domain cap и encoding tests.
После review gap добавлен отдельный regression57/57: direct child tag write без actual root update
denied, positive tag-only edit versioned. Первый full1513/0failures/errors/3Windows skips завершился
BUILD FAILURE на Spotless, поскольку regression sources добавлялись во время запуска. Он не считается
финальной qualification; затем выполнен новый clean verify на frozen manifest576 файлов.
Первый run без escalation не был qualification: sandbox запретил loopback/Docker. Следующие ранние
failures выявили новые fixture ошибки (manual Strategy attribution, inherited test authentication,
detached lazy access) и parent-loading race; fixtures/implementation исправлены без ослабления assertions.

Фактически выполненные команды:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostRevisionTest,PostApiIntegrationTest,PostgreSqlPostApiTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostRevisionTest,PostApiIntegrationTest,PostgreSqlPostApiTest,PostgreSqlPostMigrationTest,PostgreSqlUtcMigrationTest,PostgreSqlPersistenceTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostRevisionTest,PostApiIntegrationTest,PostgreSqlPostApiTest,PostgreSqlPostMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt14-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
```

Python10/10 passed с writable workspace temp. Исходный `python -B -m unittest discover -s scripts
-p 'test_*.py'` получил10 temp-permission errors; это failure среды, не pass. Required report gates,
Linux filesystem/renderer и packaged startup — итоговые результаты ниже.

### Итоговая qualification на frozen source

- `.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:check clean verify` — **BUILD SUCCESS**,
  10:09, завершён2026-10-16 05:42:01 BST.145 suites,1514 tests:1511 passed,0failures/errors,
  3Windows-only filesystem skips. Complete Linux filesystem35/35,0skips покрывает эти сценарии.
- New Post tests: domain9/9, H2 API18/18, PostgreSQL API29/29, populated migration1/1.
- Mandatory PostgreSQL и evidence report gates PASS: required PostgreSQL/MinIO/ClamAV/renderer/full
  provider suites действительно выполнены без skips. Existing TEXT/PDF/URL/social/Identity/Strategy,
  durable jobs/recovery/budget/security suites не регрессировали; actual JVM crash/restart4/4.
- Spotless PASS. JaCoCo lines8760/9255=94.65%, branches3885/5390=72.08%; configured gates PASS.
- Python scripts10/10; pinned renderer image suite29/29,networknone/read-only/caps dropped.
- Actual packaged JAR/local fake/worker/JMX startup: health200; six protected operations, включая
  Post GET/create, возвращают401 без authentication. Discovery401 с fixed Bearer challenge.
  Только созданный smoke process остановлен; это local startup, не production/live Clerk gate.
- Fresh OSV194 dependencies,0findings; independently validated against newly generated runtime/test
  SBOM. Первый SBOM command с `-o` отклонён plugin: requires online mode; повтор без `-o` BUILD SUCCESS.
- 576 source/build SHA256 совпали после full gate; copied Surefire/JaCoCo/SBOM/OSV/summary находятся
  в ignored `var/prompt14-verification/`. JAR не содержит test recovery launcher.
- Docs local links/OpenAPI refs/git diff checks passed. Warnings: existing Jackson2 compatibility
  deprecations, expected Hikari connections to stopped Testcontainers during suite teardown и
  expected negative SQL guard diagnostics. Непроверенные production aspects перечислены отдельно.

Дополнительные фактически выполненные команды:

```powershell
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt14-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt14-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\prompt14-startup.ps1
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt13-verification/bom.cdx.json --all-packages --format=json --output-file=var/prompt14-verification/osv-results.json
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt14-verification/osv-results.json
python -X utf8 var/prompt14-collect.py
git diff --check
```

## Acceptance matrix14

| Требование | Code/contract | Доказательство |
| --- | --- | --- |
| Exact approved Strategy, platform-specific Post identity | ContentPost/PostContent, ADR0031 | shared API, full/DRAFT/legacy/channel/foreign/version source cases |
| Draft expectedVersion, separate approval, immutable approved branch | PostRevision/PostLifecycleUseCase | HTTP lifecycle, stale/replay, concurrent requests, SQL history guards |
| Actor/time/provenance без client ownership/provider | PostDetails, dedicated request DTO | forged fields/actual role/account/membership, SQL audit actor rejection |
| Legacy migration без fabricated data | V47 | populated V46→47 all5 statuses, original fields/timestamps/tags/NULL audit |
| Independent SQL/runtime integrity | triggers/grants | positive runtime edit/approval; forbidden source/history writes, tag-only regression |
| Full gates/startup/docs/local commit | итоговая qualification ниже | actual final commands/results, source manifest и git commit |

## Security Tests

Foreign/missing/source IDs, reserved HTTP/bean roles, forged current actor, removed membership,
disabled/role-changed account, unknown fields на разных nesting levels, stale tokens, repeated
approval/branch, unsupported formats/channels, source DRAFT/legacy/LOCKED и checksum mismatch.
DB rejects status/audit omission, foreign approval actor, mutable lineage, title/source/identity
history writes/deletes, malformed/duplicate/gapped hashtags. Runtime restricted grants tested.

## Edge Cases

- Exact source remains same после новой Strategy DRAFT; branch не подставляет latest Strategy.
- Concurrent draft edit и approval: ровно один success, другой stale conflict.
- Concurrent approved branches: ровно один new revision; повтор не создаёт дубль.
- Archive transaction выигрывает parent lock: mutation ждёт и затем отказывает, revision unchanged.
- Instagram/TikTok compatibility matrix проверена для каждого PostFormat; VIDEO/Carousel TikTok
  human approval/history имеет positive API flow, Unicode hashtags сохраняются.
- Legacy все5 статусов мигрируют в LEGACY с exact original text/time/status/tags и NULL missing audit.
- Caption/title N/N+1, hashtag count overflow, revision cap1000, empty/duplicate/bad hashtags, invalid Unicode/control
  input, pagination overflow и plain malicious instructions проверены.

## Непроверенные аспекты

Live Clerk, production deployment/IAM/TLS, restore/load/pilot и реальные AI/platform adapters14
не квалифицируются. Риск semantic correctness контента остаётся у human review; schema validator
не доказывает marketing quality. PostgreSQL source ID/version/approval bindings защищены независимо;
canonical source checksum вычисляет и сверяет Java, SQL не дублирует Jackson canonical encoder.
Runtime create выполняется API suite с migration/test identity; отдельно runtime SQL доказывает
draft edit/approval и запрещённые history/source writes. Это не full production IAM qualification.

## Что мне важно понять

Post id отвечает «какой это материал», revisionId — «какая версия», lockVersion — «не изменил ли
кто-то эту версию после моего чтения». Смешивание этих идентификаторов позволило бы либо потерять
history, либо создать повторные branches. Approval подтверждает сохранённый DRAFT, не request content.
SQL guards нужны потому, что ошибка Java или прямой repository call не должны переписать approval.
Child hashtags также являются content: PostgreSQL требует actual root write в той же transaction,
сравнивая xmin текущей row с current transaction ID modulo32-bit epoch. Простого SELECT FOR UPDATE
недостаточно, поскольку он не обновляет xmin. Root guard требует version+1/fresh editedAt. Regression
проверяет standalone valid tag write denied и positive tag-only use-case edit с новым version/audit.

## Что мне стоит изучить

1. Optimistic locking — expectedVersion обнаруживает устаревшее редактирование.
2. Aggregate identity и revision — стабильный объект имеет несколько исторических состояний.
3. Pessimistic locking/lock order — сериализует конкурирующие transitions без обратного порядка locks.
4. Deferred constraint trigger — проверяет целостность root/tags в конце atomic transaction.
5. Provenance — различает creative source, human authorship и отдельную approval; не приписывает edits модели.

## Production considerations

Deployment применяет Flyway и актуальные runtime grants, останавливает legacy writers и проверяет
backup перед cutover. Полный production backup/restore/load остаётся отдельным gate. Есть bounded
counts/body/page и serialization, но общий distributed HTTP rate limiting/operational audit retention
относятся к hardening28/#87; не следует считать local feature коммерчески qualified по одним unit tests.
Source adapter/privacy decisions остаются отложенными;14 не выбирает AI provider.

## Факты, выводы, assumptions

Факты — перечисленные actual inspection/tests и final full qualification выше.
Инженерный вывод — отдельные identity/revision сохраняют историю и позволяют будущим consumers
ссылаться на exact approved content. Assumptions — будущие consumers15–18 повторят собственные
eligibility/currentness checks согласно ADR, а не будут трактовать read flags как разрешение.

## Git / issues / remaining scope

Qualified local slice сохраняется commit `feat(content): qualify platform-specific post revisions`;
его SHA: `git log -1 -- docs/prompt14-post-revisions-report.md`. Push14/новый remote CI, issue closure,
merge и deployment не выполнялись. Baseline13 уже pushed/green по отдельному explicit user request.
Next15 не начат; #115/#103 остаются open для generation/scheduling/DST/media/export acceptance,
#121/#120 остаются open по согласованному provider deferral.
