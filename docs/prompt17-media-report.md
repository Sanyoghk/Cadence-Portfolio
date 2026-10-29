# Задание17 — private demo media lifecycle

## Что было сделано

Реализованы private assets и ссылки на конкретные Post revisions: создание из явно demo catalog,
проверка bytes/scan, отдельный human review, attachment/replacement, approval и bounded delivery.
Quarantine object не считается approved asset. Новый media namespace не доступен quarantine cleanup.
APPROVED history не переписывается; replacement создаёт новый DRAFT. Archive отзывает delivery и
новое использование, сохраняя исходный audit, history и retained quota.

Baseline: clean `mvp`, HEAD `f0295311c8bf61da135de17af6761534813042cd`; prerequisites14–15
подтверждены commits/code/ADR,16 сохранено. Актуальные GitHub #98/#107 прочитаны перед implementation.
#98 core generation criteria уже покрыты15; task17 добавляет media slice из текущего user request и
плана. Live adapters/privacy/cost decisions не выбирались. Задание18 не начиналось.

## Как это работает

Fixture selection — server enum, не filename/URL/upload. MediaAsset STAGING сначала резервирует
tenant-bound bytes/count. Private PUT/read-back/hash/catalog/scan выполняются вне transaction.
Completion заново проверяет actor/membership/parents/version/deadline; CLEAN даёт AVAILABLE/PENDING,
не permission на Post reference. Owner preview разрешён только после verification. Explicit review
записывает APPROVED_DEMO или REJECTED с actual human actor/time. Provider moderation не выдумывается.

Attachment требует same Brand, актуальный reviewed asset и physical read/hash повторно. DRAFT
selection меняет revision version/human edit audit; approved replacement создаёт новый DRAFT.
Manual text edit сохраняет compatible references и проверяет их; generation оставляет media только
в прежней history, новый generated DRAFT требует отдельного media review. Asset IDs omitted в старом
empty-media Post JSON; old generation snapshots/receipts не переписываются.

Private delivery делает capture→I/O→recapture; если actor/source context/asset/selection изменились,
stale результат не возвращается/не approves. Все bytes ограничены; no-store/private, nosniff, sandbox,
attachment server UUID filename и DEMO/moderation headers. Range/URL/presigned delivery отсутствуют.

## Архитектурное решение

[ADR0034](adr/0034-private-demo-media.md), [API](media-assets-api.md).
`PrivateMediaStorage` — отдельная capability, adapter делегирует проверенным filesystem/S3 primitives
с отдельным typed MediaObjectKey. Старые quarantine methods сохраняют прежнюю prefix allowlist.
S3 общий private/immutable/checksum/deadline/concurrency механизм, FS общий path/symlink/bounded
read/atomic-write механизм. IAM: media Put/Get, без Delete; referenced bytes не исчезают от cleanup.

`MediaAsset` — versioned lifecycle с immutable origin/catalog/checksum/format/size/receipt; ordered
`post_revision_media` принадлежит exact revision. `PostMediaPolicy` проверяет compatibility и asset
readiness; `MediaVerification` выполняет physical I/O вне DB locks. Locks:
Account→Workspace→Brand→Strategy→Post→Asset; asset-only Account→Workspace→Brand→Asset. Snapshot
recapture проверяет versions/audit/access и deadline. SQL независимо защищает Brand links, metadata,
actor/state/audit/quotas, current draft witness, approved history и final compatibility/uniqueness.

Alternatives: public/static URLs позволяют обходить authorization/revocation; quarantine references
смешивают scan и human permission; новая реализация blob storage дублирует security boundary.
Выбран reuse с отдельной capability/namespace/admission. Цена — дополнительные capture transactions,
conservative retained quota и explicit review. Valid carousel reorder требует deferred uniqueness:
Hibernate обновляет child rows поочерёдно, но итоговый набор должен оставаться unique.

Demo catalog содержит реальные authored PNG и MP4, visibly DEMO / NOT AI GENERATED. PNG512×512,
MP4/H.264360×640/2seconds; actual codec/dimensions/duration проверены ffprobe, PNG и кадр actual encoded MP4 осмотрены визуально.
FFmpeg6.1.1-3ubuntu5 использован как одноразовый official Ubuntu dev tool в network-disabled Docker,
не как backend dependency. [Golden fixture provenance](../src/main/resources/demo-media/README.md).
Admission только exact allowlisted bytes — не заявление о наличии general video decoder.

## Acceptance matrix

| Требование | Реализация / доказательство |
| --- | --- |
| tenant-bound private assets | typed Brand/asset/checksum key, actual OWNER scope, private namespace, FK/SQL guards |
| explicit provenance/format/size | immutable fixture descriptors/hash/origin/size/width/height/duration, scan/human audit |
| platform validation | IMAGE PNG1, CAROUSEL PNG2–10, REEL/VIDEO MP4, STORY PNG/MP4; IG/TikTok positives |
| scan != admission | AVAILABLE/PENDING preview only; APPROVED_DEMO required for reference/approved delivery |
| bounded private delivery | capped whole-object read/hash, no paths/URLs/Range/cache bypass, final access/asset recheck |
| immutable history / replacement | DRAFT edit token, approved→new DRAFT, original refs/provenance retained |
| archive / retention races | archive denies bytes/new use; quota retained, late store/approval/delivery rejected |
| forbidden inputs | foreign/unreviewed/missing/corrupt/oversized/wrong-format/duplicate/unknown fields tests |
| independent DB/runtime | V50 FKs/checks/guards/deferred seals, real runtime role and MinIO Delete denial |
| full qualification | exact commands/results below; no skipped required integration suite accepted as pass |

## Security Review

- **Threat:** BOLA/foreign asset/forged role. **Protection:** scope resolution + actual active OWNER/
  membership and parent-first locks. **Verification:** HTTP anonymous/reserved/foreign negatives,
  cross-Brand refs, real PostgreSQL account/membership/Brand/Workspace revocation during I/O.
- **Threat:** forged provenance/status/actor or arbitrary URL/path/filename. **Protection:** strict
  dedicated DTO, server enum/key/clock/actor, immutable DB metadata. **Verification:** unknown-field
  tests, SQL metadata mutation denials, no private storage references in responses.
- **Threat:** CLEAN incorrectly treated as approval; fake moderation/output. **Protection:** separate
  explicit APPROVED_DEMO human decision, demo origin/warnings/watermarks, production fail-closed.
  **Verification:** pending preview vs approved delivery/reference, rejection/replay, prod+local/test
  disabled tests, exact fixture byte/hash tests and real ClamAV scans without moderation claim.
- **Threat:** substituted/missing/oversized bytes, adapter mutation or uncertain PUT. **Protection:**
  exact catalog+checksum+size before/after scan, private read-back, bounded I/O, persistent staging
  quota before PUT, no silent release on crash/failure. **Verification:** failure injection/mutation/
  oversize/corruption/missing/crash/late-stage-archive tests; real MinIO immutable/bounded contracts.
- **Threat:** stale approval/replacement/archive/delivery. **Protection:** revision+asset versions,
  capture/I/O/recapture, same parent mutex and SQL witnesses. **Verification:** blocked private read
  allows concurrent replacement/archive to commit, then late operation refuses; no I/O transaction.
- **Threat:** history tamper/deletion/collision. **Protection:** immutable approved root+child guards,
  runtime column grants, no media delete capability, retained FK links, deferred final uniqueness.
  **Verification:** direct SQL/runtime denials, carousel reorder red→green, unchanged original refs.
- **Threat:** abuse/resource exhaustion. **Protection:**10MiB/object,1000 retained intents/100MiB per
  Brand, bounded pages,2 creates+2 physical verifications per instance, validity deadlines and adapter timeouts.
  Empty-media operations do not consume I/O slots. **Verification:** quota/overflow/deadline/bounds,
  overcapacity refusal + no-media approval regression, SQL count reservation and no archive refund.

Bearer authentication/SecurityFilterChain/CSRF rationale unchanged; backend does not use cookie
session authentication. No new logs with bytes/prompts/secrets. New error503 has fixed safe details.
No runtime dependency added. Database checks do not replace request authorization; they do not claim
to physically inspect external objects. Physical verification is application/adapter responsibility.

## Runtime Flow

```text
Bearer Request → MediaController / strict DTO → MediaAssetUseCase
  → authorized locked STAGING intent + retained reservation → commit
  → DemoMediaSource → PrivateMediaStorageAdapter → FS/S3 media namespace
  → bounded read-back / exact hash / EvidenceMalwareScanner (no DB transaction)
  → actual access/parents/version/deadline recheck → AVAILABLE/PENDING receipt commit
  → owner preview → explicit review → APPROVED_DEMO
  → PostLifecycleUseCase.replaceMedia / approve
  → MediaVerification capture → private I/O → recapture → Post/asset SQL seals → commit
  → PostDetails exact refs / MediaDetails / protected bounded bytes
```

## Тесты

Final full gate завершился2026-10-16T09:23:12+01:00,15:32min: **BUILD SUCCESS**.
155 suites/1731 tests,1727 executed passed,0 failures/errors,4 Windows filesystem skips;
complete Linux37/37 without skips covered all four. Media PostgreSQL49/49, H2media30/30,
previous PostPG29/29,calendar45/45,ContentPG25/25,child-JVM recovery8/8. Python10/10,
renderer29/29; mandatory PostgreSQL/MinIO/ClamAV/renderer/full-pipeline reports PASS.
Spotless PASS; JaCoCo94.55% lines/71.70% branches, all configured checks met.
632 source/build hashes unchanged through full/SBOM/startup; no required integration skips.

All Maven commands use `.\mvnw.cmd -o --batch-mode --no-transfer-progress` unless stated otherwise.

| Команда / selector | Фактический результат |
| --- | --- |
| `spotless:apply test-compile` | BUILD SUCCESS |
| `spotless:apply '-Dtest=MediaApiIntegrationTest,PostgreSqlMediaTest,OpenApiContractTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest' test` | initial test compile failure: inherited content(String) shadowed MockMvc content(); no test pass claimed |
| `spotless:apply '-Dtest=MediaAssetTest,DemoMediaConfigurationTest,MediaApiIntegrationTest,PostgreSqlMediaTest,OpenApiContractTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest' test` |95 tests,0 failures/2errors: new approved branch audit invariant caught |
| `spotless:apply '-Dtest=MediaAssetTest,DemoMediaConfigurationTest,MediaApiIntegrationTest,PostgreSqlMediaTest,OpenApiContractTest,FileSystemEvidenceObjectStorageTest,S3EvidenceObjectStorageTest,MinioEvidenceObjectStorageTest,ClamdContainerIntegrationTest' test` |147 tests,0 failures/errors,4 Windows filesystem skips |
| `spotless:apply '-Dtest=PostgreSqlMediaTest,PostgreSqlPostApiTest,PostgreSqlCalendarTest,PostgreSqlContentGenerationTest,PostgreSqlBrandAnalysisTest,PostgreSqlPostMigrationTest,OpenApiContractTest' test` |163/163 PASS |
| `spotless:apply '-Dtest=MediaApiIntegrationTest#tiktokVideoUsesSameVerifiedPrivateAssetContract,PostgreSqlMediaTest#boundedDeliveryCapacityDoesNotBlockPostsWithoutMedia+tiktokVideoUsesSameVerifiedPrivateAssetContract,MediaAssetTest,OpenApiContractTest' test` |9/9 PASS |
| `'-Dtest=PostgreSqlMediaTest#formatRulesImagesCarouselStoryAndVideoAreExplicit' test` | regression RED:1 error, SQL23505 immediate duplicate during valid reorder |
| `spotless:apply '-Dtest=PostgreSqlMediaTest#formatRulesImagesCarouselStoryAndVideoAreExplicit,MediaApiIntegrationTest#formatRulesImagesCarouselStoryAndVideoAreExplicit,PostRevisionTest,FileSystemEvidenceObjectStorageTest' test` | GREEN49tests,0 failures/errors,4 Windows skips |
| `'-Dspring.test.context.cache.maxSize=8' spotless:check clean verify` |1731/0failures/errors,4Windows skips covered Linux37/37; BUILD SUCCESS |


Дополнительно фактически выполнены:

```powershell
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt17-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt17-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt17-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
python -B var/prompt17-collect.py
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt17-verification/bom-baseline.cdx.json --all-packages --format=json --output-file=var/prompt17-verification/osv-results.json
python -B scripts/verify_dependency_scan.py target/bom.cdx.json var/prompt17-verification/osv-results.json
.\var\prompt17-startup.ps1
git diff --check
```

Fresh current SBOM194 matched to newly executed OSV scan of unchanged baseline graph:0 findings.
No new runtime dependency. Cached scanner2.5.1 Windows artifact unchanged from verified prior
SHA25625e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6.
Packaged local-isolated fake configuration: health200;20 protected anonymous probes401 including
all8 media routes; discovery401/fixed Bearer. Own process stopped, port18100 released. Actual
PostgreSQL migration/DI/runtime startup was exercised by the real Spring integration suites.
Exact golden PNG/MP4 bytes present in JAR, no FFmpeg binary or test recovery launcher packaged.
JAR SHA256 `cab75bc18b97ea6b781a9acb444bd4e1f814f75ad62589f8bcf1b58f1407f18c`.
Logs/reports/SBOM/manifests saved under ignored var/prompt17-*; no secrets added to Git.

Warnings: existing deprecated Jackson2 compatibility/Testcontainers/S3 APIs, closed-test-container
Hikari cleanup warnings and CycloneDX metadata-keyword warnings. Gates passed; no security finding
suppressed and no required integration skip accepted. Source stayed frozen through qualification.

Branch fix uses one human operation timestamp for authored/edited audit; old SQL invariant and old
migration checksums preserved. Reorder fix defers final uniqueness rather than accepting duplicates.
Capacity fix preserves empty-media behavior. Assertions/security tests not weakened or disabled.

## Security Tests / Edge Cases

Positive IG IMAGE/CAROUSEL/STORY/REEL and TikTok VIDEO; fixed catalog byte/provenance; preview vs
review; approved asset delivery; DRAFT/APPROVED replacement and compatible manual branch; regenerated
DRAFT does not inherit old media; carousel reorder; explicit removal after asset archive; unverified/
foreign/wrong-format/missing/oversized/corrupt/infected/unknown/mutated source; stage crash with held
quota; stage archive fences late store; actual account/Workspace/Brand/membership/asset revocation
during private delivery; replacement during approval; resource cap, overcapacity without breaking
no-media approval; deadline equality; immutable SQL/runtime history; MinIO denied anonymous/Delete
and cross-namespace quarantine access; filesystem link guards, real scanner fixture bytes.

## Непроверенные аспекты / Production considerations

Live media API/model, processor/region/privacy/cost/retention/moderation policy, arbitrary uploaded
media decoder/normalizer and their qualification are separate. This demo-only producer is not a
claim that all live media backend/adapter requirements are finished. Existing approved MVP inputs
and source evidence ingest remain supported under their own contracts; no fake credential/live
OAuth qualification attempted. JPEG/arbitrary PNG/MP4 and remote URLs are not admitted by this demo API.

All retained objects/failed intents/history remain until separately approved deletion policy; quota
does not reset on archive. Operational reconciliation of abandoned staging records currently means
explicit archive, not automatic storage deletion or fake success. S3 deployment must apply media
Put/Get grant without Delete; current production demo actions stay disabled. Real load/pilot,
live Clerk, cloud IAM/TLS/backups and release gates are not proved by local fixtures/tests. Known demo
bytes have no invented scan TTL; future arbitrary/live media needs its own current scan/expiry rules.
Task18 must define/export its own exact revision/media capture/currentness contract; not implemented17.

## Что мне важно понять

Bytes существующие в storage ещё не означают разрешённый asset. Целостность bytes (integrity/checksum), malware scan,
human review и разрешение использовать asset в конкретном Post — разные проверки. Approval относится
к exact revision и её media selection; approved replacement сохраняет прежнее решение в истории.
Capture/read/recapture нужен потому, что нельзя удерживать DB locks во время storage I/O, но после
I/O необходимо повторно убедиться, что доступ и asset всё ещё актуальны.

## Что мне стоит изучить

1. **Разделение полномочий (capability separation):** отдельные private media/quarantine interfaces дают разные полномочия;
   CLEAN не превращается автоматически в permission.
2. **Оптимистическая конкуренция (optimistic concurrency):** expectedVersion защищает asset review и Post selection от stale writes.
3. **Неизменяемое происхождение данных (immutable provenance):** origin/checksum/scan/human audit сохраняют исходные факты даже после archive.
4. **Гонка между проверкой и использованием (TOCTOU):** состояние может поменяться между проверкой и использованием; recapture закрывает tested races.
5. **Отложенные ограничения (deferred constraints):** SQL проверяет итог transaction, разрешая корректную перестановку ordered refs.

## Изменённые файлы

[Полный manifest всех57 файлов](prompt17-changed-files.md) с назначением каждого изменения.

## Факты / инженерные выводы / assumptions

Факты: конкретные executed commands/results/inspection и unchanged632 hashes выше; full local
qualification подтверждена перечисленными gates. Инженерный вывод: отдельная capability/namespace/admission и exact recapture
сохраняют tested tenant/history/lifecycle invariants. Assumptions: production infrastructure, load,
live provider/decoder/moderation/deletion policies требуют собственной проверки и не подменены demo.

## Git / issue boundary

Finished slice сохраняется local commit `feat(content): qualify private demo media lifecycle`;
parent f0295311c8bf61da135de17af6761534813042cd; no push/new remote CI/external issue edits/closure/merge/
deployment. #98 остаётся open до whole-issue qualification и permitted push/CI policy.18 не начат.
