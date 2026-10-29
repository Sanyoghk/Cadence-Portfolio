# Задание22 — creator performance/recommendation report

Baseline `5f3e5068879a634b5b76f2bc0d188ebb292b9294`, branch `mvp`, initial working tree clean.
Дата2026-10-17. Actual #105/#107 и code prerequisites19–21 сверены; утверждённый пакет ADR0039
сохранён. Status: **22 — QUALIFIED LOCAL**. Required repository gates завершены.

## Что сделано / как работает

Creator получает неизменяемый performance report и может читать JSON или скачать static HTML.
Report связывает exact Strategy revision/checksum, normalized observations и original supporting
evidence выбранных рекомендаций. Исправление метрик не переписывает старый отчёт. Новая операция
может показать новые metrics рядом со старой гипотезой с explicit difference disclosure.

Контракт: [API/recovery](creator-reports-api.md), [ADR0039](adr/0039-creator-performance-reports.md).
Формат HTML+JSON,24h full result access, retained history/bytes до отдельной deletion policy,
cap100/Brand и2MiB combined snapshot/artifact. Private authenticated delivery — явное утверждённое
уточнение старого signed-link wording #105. PDF/mail/live integrations не входят в scope.

## Архитектурное решение

Reuse checked PerformanceReader/PerformanceCalculator/PerformanceMetrics; no second formula
implementation. Parent-locked scalar capture → durable CREATOR_REPORT Job → pure renderer вне
locks → current permission/source и lease/attempt/deadline rechecks → atomic result+Job SUCCEEDED.
Dedicated CreatorReportSnapshot excludes raw imports/Identity content/actors/provider payloads.
Typed rehydration повторно сверяет captured formulas, bounds и source binding/checksum.

Альтернативы: PDF добавил бы renderer attack surface/dependencies без требования; rejected.
Public signed capability сложнее отзывать при revocation; выбран authenticated read/download.
Object-store upload требует dual-write/orphan recovery; bounded PostgreSQL bytea сохраняет atomic
publication. Не обновляем report из latest observations: это потеряло бы original evidence.
Captured data может стать устаревшей — цена reproducibility; response явно historical, без
обещания current eligibility. SQL guards/FK/checks/runtime grants независимо защищают history.

Reports поддерживают DAY/PERIOD и pillar groups одного explicit approved/locked full Strategy.
Они не AI consumer: не передают evidence/model input, не делают apply/consent/approval и не
экспортируют underlying Identity/evidence text. Current advice eligibility проверяется existing
generation/apply policy, а не подменяется наличием отчёта. Source version/lock/archive/access
изменения проверяются заново; metric corrections сохраняют stable snapshot по contract.

## Security Review

Threat: BOLA/mass assignment/stale token scope.
Protection: strict DTO, OWNER/usecase/current local membership, scoped report/Job lookup,
parent-first locks и independent SQL actor/Brand/Strategy/observation/context checks.
Verification: positive workflow, anonymous/role/foreign/status/download/unknown fields,
revocation/archive/source race tests, runtime least-privilege flow и SQL tampering negatives.

Threat: forged supporting IDs, corrupted snapshots, invented values/causal claims.
Protection: original immutable RecommendationInsight capture, exact reduced DTO and checksums;
captured formulas recomputed from counters; minimum sample/coverage и UNCALIBRATED/HYPOTHESIS.
PostgreSQL validates source ownership and original recommendation support links at commit.
Verification: independent prior formula fixtures, correction/history/missing/sparse/zero tests,
direct SQL forged-ID guard checked by exact SQLSTATE/message.

Threat: XSS/injection/private payload disclosure.
Protection: fixed static markup, all variable text escaped, no scripts/URLs/tools, attachment/
CSP/nosniff/no-store; generated filename and parameterized SQL. Dedicated DTO excludes prompts,
Identity text, raw receipts/audit actors/storage keys/secrets. No new dependencies/CORS/CSRF
or SecurityFilterChain changes; existing Bearer-only contract preserved.
Verification: Unicode/markup escaping, DTO/HTML/header tests and manual data-flow review.

Threat: duplicate/late publication, crash/revocation, resource exhaustion.
Protection: immutable scoped key/request digest, one Job/result, parent/report/Job locks,
worker/attempt/lease/deadline fences,3 bounded attempts, query/output/range/selection/quota caps.
Verification: concurrent replay/claims, expired lease recovery/stale attempt, renderer failure,
publication/source/access races, exact expiry and combined payload bound tests.

## Изменённые файлы

Полный per-file manifest всех45 файлов: [изменённые файлы](prompt22-changed-files.md).

## Runtime Flow

```text
Authenticated OWNER + strict request + Idempotency-Key
 → actual Account/Workspace/Brand access → scoped accepted-key replay or fresh source
 → normalized metrics + original recommendation evidence capture
 → immutable bounded snapshot + Job in one transaction →202/Location

Worker candidate → parent/report/Job atomic claim → persisted original actor authorization
 → snapshot/source validation → release locks → escaped static HTML
 → fresh access/source/lease/attempt/deadline → atomic artifact + Job SUCCEEDED

Status/result/download → current resource authorization → source/hash/lifetime guards
 → private no-store dedicated JSON/HTML; no permanent bearer/storage URL
```

## Тесты / результаты

Implementation compile/Spotless PASS. Initial test compile found two incorrect fixture API
calls; corrected without changing assertions. First128-test run:115pass/13errors: expiry
fixture recomputed request dates after moving clock, plus PostgreSQL PL/pgSQL alias collision.
Fixed original request reuse; SQL alias renamed with guard retained. Diagnostic8tests:6pass,
1OpenAPI enum-order failure,1SQL error; enum aligned to actual PostFormat order, assertion kept.
Expanded targeted **148/148 PASS**, затем **156/156 PASS** (H2 report66, PG report73,
renderer3, migration1, historical migration1, persistence8, UTC1, OpenAPI3). Review обнаружил
missing generic queue exclusion для CREATOR_REPORT. Добавлен explicit exclusion из generic
claim/recovery и regression test; source lock перемещён перед report/Job locks. Final review
run **13/13 PASS**: generic queue6, report renderer3, combined output1, PostgreSQL revocation/
deadline/queue isolation3. Assertions/security restrictions не ослаблялись.

Фактически выполненные commands:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test-compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CreatorReportApiIntegrationTest,PostgreSqlCreatorReportTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlCreatorReportTest#emptyAndSparseDataRemainExplicitAndNeverInventAdvice,CreatorReportApiIntegrationTest#exclusiveExpiryDoesNotResurrectResultOnReplay,CreatorReportRendererTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=CreatorReportApiIntegrationTest,PostgreSqlCreatorReportTest,CreatorReportRendererTest,PostgreSqlCreatorReportMigrationTest,PostgreSqlRecommendationMigrationTest,PostgreSqlPersistenceTest,PostgreSqlUtcMigrationTest,OpenApiContractTest' test
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply '-Dtest=PostgreSqlCreatorReportTest#revocationDuringRenderingPreventsPublication+rendererRetriesAreBoundedAndDeadlineIsExclusive+genericIngestQueueCannotClaimOrRecoverReportJobs,CreatorReportApiIntegrationTest#combinedSnapshotAndArtifactCannotExceedReservedTwoMiB,CreatorReportRendererTest,DurableJobQueueTest' test
.\mvnw.cmd --batch-mode --no-transfer-progress test-compile org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=true' '-DoutputFormat=json' '-DoutputName=bom.cdx'
.\var\osv-scanner-2.5.1.exe scan source -L var/prompt22-current-bom.cdx.json --all-packages --format=json --output-file=var/prompt22-verification/osv-results.json
python -B scripts/verify_dependency_scan.py var/prompt22-current-bom.cdx.json var/prompt22-verification/osv-results.json
python -B -c "import tempfile,unittest;from pathlib import Path;p=Path('var/prompt22-temp').resolve();p.mkdir(exist_ok=True);tempfile.tempdir=str(p);r=unittest.TextTestRunner().run(unittest.defaultTestLoader.discover('scripts',pattern='test_*.py'));raise SystemExit(not r.wasSuccessful())"
.\scripts\run_linux_storage_tests.ps1
docker build --tag cadence-prompt22-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt22-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd -o --batch-mode --no-transfer-progress '-Dspring.test.context.cache.maxSize=8' spotless:check clean verify
python -B var/prompt22-collect.py
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\var\prompt22-startup.ps1
python -B var/prompt22-integrity.py
python -B var/prompt22-manifest.py
python -B var/prompt22-doc-check.py
git diff --check
```

Окончательный full run: **BUILD SUCCESS**, **179 suites, 2250 tests;
0 failures/errors, 4 Windows filesystem skips**, **2246 выполненных tests PASS**.
Завершён `2026-10-17T03:42:31+01:00`, duration `19:50 min`. Только четыре symlink scenarios
FileSystemEvidenceObjectStorageTest skipped на Windows; Linux37/37 выполнил их без skips.
Report H2**66/66**, PostgreSQL**76/76**, renderer unit3/3, V54→V55 migration1/1.
Existing AI child-JVM crash/restart8/8 также PASS; это foundation qualification, не отдельный
kill/restart report JVM test. Новый report recovery проверен через persisted lease/new attempt.

Python**10/10**, Linux filesystem**37/37**, isolated renderer**29/29**. Required PostgreSQL/
MinIO/ClamAV/renderer/full evidence report gates PASS, ни один required provider suite не skipped.
Spotless/JaCoCo thresholds PASS; actual line**94.15%**, branch**71.08%**.
Fresh SBOM**194 Maven dependencies**, OSV verifier**0 reported vulnerabilities**, scanner2.5.1
SHA256 `25e42f5ef6711fd8c0fb45390972205891dd44c6bd02ac93f0f63e8e98d9bfb6`.

Packaged startup: health200, **38 защищённых операций401** для anonymous requests, discovery401
с fixed Bearer challenge; own process stopped, port18100 released. Report scheduling явно включён
при startup probe. Все **743 source/build/config hashes** сохранены на протяжении full/startup.
Все **642 compiled classes/resources** совпали с BOOT-INF/classes packaged JAR;
test-only AiRecoveryProcess launcher отсутствует. JAR SHA256:
`35253351c2bf40643cc4fbc3cd4511b49b7c9dde19c98911634a25844b04bc5b`. Receipts/SBOM/OSV/Surefire/JaCoCo сохранены в ignored `var/prompt22-verification`.
Прежние OpenAPI schemas/operations семантически сохранены; изменён только approved Job variant.
Migrations V1–V54 unchanged; V55 additive. No dependencies/security suppressions added.

Warnings: прежние JSON2/S3 deprecations/unchecked test fixtures, JVM/Mockito/Hibernate
informational messages, Hikari retries после остановки соответствующих Testcontainers и
ожидаемые SQL/security negatives. CycloneDX schema keywords meta:enum/deprecated warnings;
SBOM BUILD SUCCESS и независимый inventory verifier PASS. Эти warnings не test failures.

## Security Tests / Edge Cases

Сценарии qualification (actual results выше): no-data/sparse/zero/null, exact DAY/PERIOD/timezone,
foreign IDs/roles/unknown fields/Range, original support after correction, duplicate apply-free
report generation, lost lease/retry/timeout/source changes, SQL/runtime immutable guards,
cap100 retained pending/expired, snapshot/artifact2MiB, XSS/Unicode,24h exclusive expiry.

## Actual issue mapping

Recommendations subset #105 (scope/provenance/accept/dismiss/new DRAFT/pagination) qualified21.
Report22 adds durable Job/scoped result/download; old signed-link wording explicitly refined
by user-approved ADR0039. DTO-only/error/security tests map remaining #105 AC. External issue
state/checkboxes are not completion evidence; no GitHub writes/push/new CI/closure performed.

## Непроверенные аспекты / Production considerations

Live Clerk/provider truth, production multi-instance load/p95, IAM/TLS, backup/restore и
physical deletion/retention cleanup remain separate qualifications. Pure report performs no
AI inference/cost/moderation operation. Persisted lease/new-attempt recovery tests do not replace an
operational multi-host failure drill. Actual packaged startup/full gates выполнены выше.
Existing AI child-JVM tests qualify their foundation, not live report/provider quality.

## Что важно понять / Что изучить

1. Immutable snapshot — сохранение конкретных IDs/версий/значений, чтобы поздняя correction
   не меняла объяснение уже принятого решения.
2. Idempotency — одинаковый принятый запрос возвращает ту же operation; ключ не снимает
   текущую authorization и не продлевает download lifetime.
3. Lease fencing — владение конкретной попыткой worker проверяется при записи; поздний
   процесс может вычислить bytes, но не получает права publication.
4. Descriptive vs causal — observed rate и достаточная выборка не доказывают рост из-за
   proposed patch; report сохраняет confidence/uncertainty честно.
5. Defense in depth — DTO/usecase/domain/SQL/runtime privileges независимо ограничивают
   возможность записать чужое evidence или переписать историю.

## Facts / инженерные выводы / assumptions

Факты — actual inspection/commands/results, которые перечислены здесь; старые runs21 не
выдаются за новые. Инженерный вывод — captured normalized input + atomic leased publication
делают retry reproducible и исключают silent history rewrite. Assumptions — human metrics
не platform-verified truth, live deployment/cleanup policies требуют отдельной qualification.
Slice сохранён local commit `feat(reports): qualify private performance report jobs`; actual SHA в итоговом ответе/`git log -1`. Push/remote CI/closure/merge/deployment не выполнялись;23 не начинался.
