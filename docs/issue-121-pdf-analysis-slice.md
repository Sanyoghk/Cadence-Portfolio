# #121 — reviewed PDF text analysis и fenced signature publication

Дата: 2026-10-14. Ветка `mvp`, исходный HEAD `3f76dd2b545b72a33f0526403646d54766bcdfa5`.
Статус qualification: local full verification PASS; remote exact-SHA CI не выполнялась.

## Что было сделано

Явное действие `analyze-evidence` теперь использует подтверждённый человеком текст PDF
вместе с TEXT. Backend повторно проверяет PDF bytes перед моделью и перед записью результата.
Signature, source links, точные цитаты и SUCCEEDED receipt публикуются с сохранением
существующих проверок доступа и версий. PDF Identity copy/approval остаётся запрещённым.
Real provider не выбран; production adapter остаётся fail-closed.

## Как это работает

Server выбирает latest reviewedAt/id admission каждого evidence в current
source/Brand/Workspace context, детерминированно сортирует по evidence UUID.
Client не может назначить source IDs, actor или text. Допускаются1–5 sources,
в сумме не более20000 UTF-8 bytes; превышение отклоняется целиком, без truncation.
Latest consent обязан быть актуальным; backend не возвращается к старому scan receipt.

Для PDF нужны FILE/application/pdf/PENDING, exact immutable PDF_TEXT artifact,
SUCCEEDED extraction Job, original и latest CLEAN scan, current expiry и policy,
tenant-bound REFERENCED quarantine ledger и retained bytes. Затем private read вне
DB transaction проверяет size/header/SHA-256 и свежий finalize повторяет metadata/access.
Модель получает только reviewed text, admission/evidence IDs, source version и checksum.
Private storage reference и raw PDF не передаются модели или response.

После физического batch весь selected набор снова проверяется. Это необходимо, потому что
пока backend проверял PDF, companion TEXT мог быть отозван. После модели ранний metadata
preflight не допускает ненужного повторного storage read при revoked access/stale sources.
Далее повторяются physical batch и конечная publication transaction.

При отказе попытка становится FAILED либо SUPERSEDED; исходная signature не заменяется.
После успешной публикации Brand version увеличивается: старый consent не становится
бессрочным разрешением для нового parent context. Source остаётся FILE/PENDING.
`aiConsumptionEnabled=true` означает поддержку отдельного backend consumer,
а не наличие live provider. Admission/snapshot сами модель не вызывают.

## Архитектурное решение

[ADR0019](adr/0019-pdf-evidence-analysis.md) расширяет существующий synchronous workflow:
`AnalyzeEvidenceUseCase` управляет I/O, `BrandAnalysisTransactions` — короткими
transactions, `PdfAnalysisSourcePolicy` — metadata eligibility. Existing
`ReviewPdfEvidenceUseCase.snapshot` переиспользуется для полной physical verification.
Это исключает второй, отличающийся алгоритм private PDF read.

`AnalysisTextSource` и digest format `text-evidence-v1` сохранены: format описывает textual
snapshot, а source kind/provenance задаются immutable admission. Legacy receipt не получает
выдуманные PDF metadata. Snapshot содержит immutable exact PDF admission ID set;
preflight/publication сравнивают его с persisted source links, исключая пропуск PDF проверок.

V37 заменяет только SQL analysis admission guard, добавляет PDF metadata helper.
V1–V36 не редактируются. Existing input seal, source/citation FK, exact quotation guard,
runtime grants и Identity TEXT-only V35 сохранены. Publication guard учитывает Brand+1.
Populated V36→V37 fixture сравнивает полные TEXT attempt/source/citation rows.

Альтернатива сразу разрешить PDF Identity отклонена для этого slice: отдельные copy/approval
и inherited lineage требуют собственного workflow physical/access revalidation после
signature publication. Сохранение прежнего digest устраняет несовместимый формат старых
receipts; trade-off — название text-evidence-v1 означает текстовый input, а не только
EvidenceType.TEXT. Client-selected inputs/raw PDF/новые dependencies не добавлялись.

## Security Review

Защищаем private reviewed text, tenant resources, immutable consent/provenance и signature.
Caller — authenticated OWNER; границы доверия (trust boundaries): HTTP principal/input,
DB user content, storage bytes и model output. Backend/runtime DB identity остаются trusted.

Threat: BOLA/IDOR, forged actor и revoked membership.
Protection: principal actor, scoped lookup, actual active account/OWNER/membership под locks
в capture/preflight/publication; SQL также проверяет account/membership.
Verification: existing tenant/role negatives и новый revoke-during-model regression.

Threat: stale/withdrawn consent, latest ERROR scan, expired/mutated PDF.
Protection: exact source/parent versions, whole-sourceVersion withdrawal, original/latest
scan IDs/checksums/expiry, current artifact/ledger; bytes проверяются до egress и publication.
Verification: missing bytes до model, mutated bytes/scan/withdrawal during model;
SQL guard отвергает latest ERROR при прямом вызове в обход application.

Threat: изменение companion TEXT во время PDF I/O; forged internal source classification.
Protection: fresh all-source preflight, equality persisted source links и exact PDF set.
Verification: TEXT withdrawal during PDF read → model не вызван; forged empty PDF set →409.

Threat: forged citations/provenance и изменение immutable history.
Protection: selected-admission exact substring contract, existing SQL source/citation FK,
input digest seal и runtime no UPDATE/DELETE. PDF Identity Java/V35 остаются deny-by-default.
Verification: inherited TEXT citation/runtime negatives; PDF exact source/quote publication
и PDF Identity copy rejection, V36→V37 row preservation.

Threat: resource exhaustion.
Protection:1–5 sources,20000 UTF-8 bytes, PDF≤10MiB, shared two-slot PDF reads и two-slot
analysis capacity, physical batch30s и attempt60s. Busy503 до создания attempt.
Verification: mixed UTF-8 budget,6 sources, two blocked models/third rejected, slots released.
Deadline проверяет просрочку, но не отменяет произвольный hung adapter; transport timeouts
и distributed quotas обязательны при qualification real provider.

Injection/excessive exposure: parameterized JPQL/JDBC; новый HTTP input/URL/shell не добавлен.
Model text остаётся недоверенными data, а не executable instructions/tools. Нет новых
secrets/logging/dependencies; сохраняются safe errors/no-store. Exact quote не доказывает
истинность текста или устойчивость future live model к prompt injection.
CSRF architecture не менялась: existing JWT Authorization header resource server.

## Изменённые файлы

- `src/main/java/com/cadence/application/repositories/EvidenceAdmissionRepository.java`: bounded latest per-evidence current TEXT/PDF selection.
- `src/main/java/com/cadence/application/services/PdfAnalysisSourcePolicy.java`: transactional PDF metadata guard без external I/O.
- `src/main/java/com/cadence/application/services/BrandAnalysisTransactions.java`: exact PDF set, current source validation и all-source preflight.
- `src/main/java/com/cadence/application/usecases/AnalyzeEvidenceUseCase.java`: physical checks до/после модели, capacity, cleanup.
- `src/main/java/com/cadence/application/results/PdfAdmissionDetails.java`: enabled separate consumer capability flag.
- `src/main/resources/db/migration/V37__pdf_evidence_analysis.sql`: independent PostgreSQL PDF analysis guard, сохранение TEXT branch.
- `src/main/resources/openapi/cadence.json`: actual mixed-analysis и receipt capability contract.
- `src/test/java/com/cadence/support/PdfReviewApiContract.java`: shared H2/PostgreSQL positive и negative generation scenarios.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java`: bypass-application latest ERROR SQL rejection.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfAnalysisMigrationTest.java`: populated V36→V37 immutable row preservation.
- `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java`:37 migration inventory.
- `src/test/java/com/cadence/application/AnalyzeEvidenceCapacityTest.java`: concurrent overload и release-on-failure.
- `scripts/verify_postgres_report.py`, `scripts/test_verify_postgres_report.py`: новый migration suite обязателен, missing/skipped/failed report отвергается.
- `docs/adr/0019-pdf-evidence-analysis.md`: decision, invariants, alternatives, threat model.
- `docs/evidence-analysis-api.md`, `docs/pdf-review-api.md`: актуальное поведение consumers/flags/limits.
- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`: совпадающие completed/remaining scopes.
- `docs/verification.md`, `docs/issue-sync.md`: qualification и external sync status.
- Этот report: actual commands/results, ограничения и объяснение решения.

## Runtime Flow

```text
POST analyze-evidence → Security/Controller (actor + scope)
  → capture: actual OWNER + Account→Workspace→Brand→Evidence→Quarantine locks
  → RUNNING attempt + immutable selected source links → commit
  → each PDF snapshot: capture → physical read/hash outside DB → fresh finalize
  → all-source/access/version/deadline preflight → commit
  → validated model port outside DB
  → metadata preflight → repeated PDF physical snapshots
  → final actual access/current sources/deadline + exact citations
  → atomic signature + Brand version + citations + SUCCEEDED → response
```

## Тесты

Фактически выполнены:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply -DskipTests compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,EvidenceAnalysisApiIntegrationTest,PostgreSqlEvidenceAnalysisTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PostgreSqlPdfAnalysisMigrationTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PostgreSqlPdfAnalysisMigrationTest,PostgreSqlPdfReviewTest,PdfReviewApiIntegrationTest,AnalyzeEvidenceCapacityTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PostgreSqlPdfReviewTest#textWithdrawalDuringPdfReadBlocksEntireBatchBeforeModel,PdfReviewApiIntegrationTest#textWithdrawalDuringPdfReadBlocksEntireBatchBeforeModel,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\pdf-analysis-startup.ps1
git diff --check
```

Final clean verify BUILD SUCCESS `2026-10-14T02:41:05+01:00`,8:34min:
116 suites/1053 tests,0 failures/errors,3 Windows filesystem skips;1050 executed pass.
H2PDF47/47,PGPDF56/56,populated V36 migration1/1,capacity1/1,OpenAPI3/3;
existing TEXT analysis H2API15/15,PG21/21 passed. JaCoCo LINE5931covered/324missed=94.82%;
Spotless/compilation/JAR/all coverage gates PASS. All mandatory PostgreSQL/MinIO/ClamAV/
renderer/full pipeline report gates PASS, без skipped required suites. Full provider10/10,
MinIO7/7,ClamAV4/4. Python10/10,renderer29/29,Linux35/35 без skips.
Linux suite закрывает3 Windows platform cases; filesystem/renderer source не изменялся.
Packaged local JAR health200,anonymous analyze-evidencePOST/PDF previewGET/admissionPOST/
snapshotGET401; helper stopped only own process. Это local H2/fake startup, не live deployment.
Ignored logs/helpers находятся в `var/`; XML/JaCoCo counters проверены отдельно.

Первый targeted139 завершился16 assertion failures: новый test ошибочно предполагал
null signature у initial Brand, хотя hydrated embeddable существует. Проверка заменена на
строгое equality всей исходной SQL brand row; assertions не ослаблены. Повтор139/139 passed.
Migration отдельно1/1 passed. Следующий targeted105 имел2 errors при Mockito restubbing:
`when` вызвал прежний storage Answer с matcher empty key. `doAnswer` исправляет setup,
regression assertions сохранены; повтор5/5 passed, final full включает все новые tests.
Первый Python запуск ограничен sandbox Temp permissions (10 tests, errors10); тот же
command с approved local execution прошёл10/10. Это environment restriction, не code fix.
Warnings: existing deprecated S3 API, test unchecked/deprecation, JVM bootstrap/CDS,
Commons Logging discovery, Hikari closed-container connection warnings; final reports PASS.
Tracked V1–V36 diff пуст; новая migration только V37. OpenAPI JSON parsed.

## Security Tests / Edge Cases

Новые сценарии: PDF generation200 с двумя reads, mixed TEXT/PDF exact citations,
unadmitted PDF no model, latest consent без duplicates, missing bytes no model и retry
после repair, bytes/scan/withdrawal/access changes during model no publication,
companion TEXT withdrawal during PDF read no model, forged empty PDF set rejection,
UTF-8 total>20000,6 sources reject whole batch, process overload/release, SQL latest ERROR.
Существующие authorization, expiry/exact expiry, malformed content, runtime history,
archive/version/retention, extraction/provenance и TEXT model negatives остаются в full suite.
Negative publication tests сравнивают всю исходную brand row, а не только signature null.

## Непроверенные аспекты

Remote exact-SHA CI/push, live provider/Clerk/hosted storage/renderer, свежие OSV/SAST/image
scans, distributed cost/rate quotas, semantic citations/relevance и retention qualification
не выполнялись. Проверять после выбора provider/infrastructure отдельными gates.
DB locks не блокируют external storage deletion после physical witness; trusted writer,
immutable object policy/IAM и production operations остаются assumptions.
Синхронный deadline не заменяет transport cancellation; process slots не cluster-wide budget.
Полный #121 не завершён: PDF Identity copy/approval, URL/images/social eligibility и
APPROVED Identity→Strategy остаются. Durable AI Jobs/budgets/full Playbook отдельно.

## Что мне важно понять

Подтверждение пользователя относится к конкретному тексту, scan и version context.
Одна проверка перед долгим I/O устаревает: после I/O проверяются и PDF, и все companion
sources. Короткие DB transactions защищают согласованность metadata, а physical read
защищает связь text/artifact с текущими bytes. Ни один из этих слоёв не заменяет другой.

## Что мне стоит изучить

1. Гонка проверки/использования (TOCTOU): между review и publication меняются access/bytes/versions; fresh fences блокируют stale результат.
2. Происхождение данных (provenance): immutable admission→artifact→scan объясняет, откуда взят текст и quote.
3. Изоляция перегрузки (bulkhead): semaphore ограничивает одновременно выполняемые analyses и освобождается в finally.
4. Отложенное ограничение (deferred constraint): PostgreSQL проверяет целостность sealed source batch при commit, а не только Java validation.
5. Независимый неизменяемый снимок (immutable snapshot): persisted sources и digest связывают результат с точным input, не с текущим mutable UI.

## Production considerations / Git

V37 должен быть развёрнут до нового backend: прежний SQL guard PDF отвергает.
Migration только functions, без data backfill; нужен reviewed backup/rollout/recovery на
реальном объёме. Recovery — forward fix, не редактирование V1–V36/history.
Real provider должен квалифицировать privacy/retention, prompt-injection containment,
timeouts/cancellation и distributed budgets; local fake этого не доказывает.
Сохранение local commit после qualification. Push после всего #121; merge/deployment отдельно.
Feature commit: `d43af8ca01c81097b52e5c041bc0560f5b080f08`.
[Точный progress draft](issue-121-pdf-analysis-sync-draft.md) опубликован в обоих descriptions.
Docs-only follow-up добавляет draft/SHA/status; product source/tests не меняются, full run выше
не выдаётся за новый запуск follow-up SHA. Draft — дополнительный изменённый documentation file.
Пользователь отдельно подтвердил «Разрешаю оба analysis updates» для нового payload и
destinations #121/#107 по прежнему требованию automatic approval review. Readback подтвердил
exact old body + approved block, title/state/labels unchanged; оба issues open. Sync завершён.
Последующий docs-only commit фиксирует результат external sync без изменения product source.

Подтверждённые факты: inspection и actual commands/results выше, local qualification.
Инженерный вывод: layers/locks/physical checks закрывают проверенные stale/publication races.
Assumptions: trusted backend/DB identity и operational immutability private storage;
commercial readiness не утверждается.
