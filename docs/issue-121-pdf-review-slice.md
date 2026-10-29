# #121 — private PDF_TEXT review preview

Дата начала: 2026-10-13. Branch `mvp`, исходный HEAD `354650aad3156816163c936b590c2b4e48e3b5b1`,
clean tree. Это законченный read-only preview slice этапа non-TEXT eligibility.
Whole #121 остаётся IN PROGRESS. Local qualification завершена; результаты ниже.

## Что было сделано

Добавлен `GET /api/v1/evidence/{evidenceId}/document-review`: OWNER может получить извлечённый
текст своего PDF для human review. Backend проверяет private storage, актуальные scans и
права до и после storage read. `requiresHumanReview=true`, `admittedToAi=false`.
Review/admission не сохраняются; модель не вызывается, FILE остаётся PENDING.

## Как это работает

`PdfEvidenceReviewController` извлекает actor/scope из authenticated `CadencePrincipal`.
`PreviewPdfEvidenceUseCase` ограничивает concurrent previews двумя process-local slots.
`PdfReviewTransactions.capture` в отдельной transaction проверяет actual active OWNER/membership,
active parents и exact source/artifact/scan/ledger graph. `PdfReviewContent` проверяет provenance,
text checksum/Unicode, max16000 code units и source bytes1..10MiB. Original artifact scan остаётся
original; latest scan выбирается среди всех verdicts, так что новый ERROR не позволяет fallback
к старому CLEAN. Latest scan expiry exclusive, default24h, configuration1s–7d.

Storage read происходит без DB transaction. Exact byte limit, complete size, `%PDF-` header и
SHA-256 source checksum проверяются на owned clone. `finish` открывает новую transaction,
повторяет все checks и сравнивает immutable snapshot; deadline30s не позволяет вернуть late text.
Response содержит provenance IDs/versions/checksums и private text, но не storage key/reference,
job/lease metadata или complete PDF; successful response и safe problems имеют no-store.

## Архитектурное решение

Layers: presentation → application orchestration → transaction service → domain policy →
scoped repositories. External I/O находится между короткими transactions. Порядок locks:
Account → Workspace → Brand → Evidence → Quarantine. Новые transactions нужны для fresh
finalize state; OSIV disabled. Snapshot содержит primitive immutable values, не managed entities.
No migration/schema/grant changes; checksums V1–V35 сохраняются.

Alternative: сразу включить PDF в TEXT model consumer. Отклонена: admission selectors должны
зафиксировать конкретный artifact и scan, а generation/Identity/approval обязаны повторно
проверять eligibility. Preview позволяет human review без молчаливого расширения AI consent.
Alternative: повторно parse PDF в JVM. Не нужна: текст уже произведён isolated renderer;
повторный parse увеличил бы attack surface. Trade-off: один bounded private read на preview.

## Security Review

Threat: BOLA/IDOR и forged principal actor, VIEWER, revoked account/membership.
Protection: scoped queries, method OWNER/principal binding, actual DB account/membership checks
на capture/finalize, Account-first lock; foreign evidence404 без private read.
Verification: H2/PG HTTP/direct negative tests; PostgreSQL held Account revocation test
доказывает ожидание Account lock до захвата Workspace lock.

Threat: stale/non-CLEAN scan, wrong artifact/source/ledger, cleanup или missing private bytes.
Protection: current exact bindings, original + latest CLEAN provenance, exclusive expiry,
SUCCEEDED extraction Job, REFERENCED ledger exact server key/scope/bytes, physical checksum.
Verification: domain negatives для foreign graph, tombstone/quota/scope, stale/future/infected
scan; API latest ERROR, legacy/missing artifact, corrupt/missing storage; existing PostgreSQL
ledger guards защищают REFERENCED objects от cleanup. Preview не выполняет cleanup writes.

Threat: access/source/archive/latest scan меняются во время I/O (time-of-check/time-of-use).
Protection: fresh finalize transaction и full snapshot equality, deadline30s.
Verification: shared H2/PG tests меняют membership, sourceVersion, Workspace lifecycle и latest
scan внутри storage callback; response отвергается, текст не публикуется.

Threat: resource exhaustion, private metadata/error disclosure, injection в document text.
Protection: 10MiB exact read, max16000 validated Unicode text, two nonblocking slots, safe503,
DTO/no-store; preview не выполняет document instructions, SQL queries parameterized, shell/URL
I/O не добавлены. Frontend обязан render plain text, не raw HTML.
Verification: size/Unicode/digest negatives, parallel two reads/third rejection/reuse test,
повторные corrupt reads затем success доказывают finally slot release; OpenAPI DTO/error checks.
No dependencies, cryptography protocols, SecurityFilterChain или cookie/CSRF changes.

## Изменённые файлы

- `src/main/java/com/cadence/domain/PdfReviewContent.java`: eligibility/Unicode/digest/bytes policy.
- `src/main/java/com/cadence/domain/EvidenceArtifact.java`: внутренние getters original scan/extraction Job.
- `src/main/java/com/cadence/domain/QuarantineObject.java`: persisted optimistic lock version getter.
- `src/main/java/com/cadence/application/repositories/QuarantineObjectRepository.java`: scoped locked ledger lookup.
- `src/main/java/com/cadence/application/services/PdfReviewTransactions.java`: capture/fresh finalize и actual access.
- `src/main/java/com/cadence/application/usecases/PreviewPdfEvidenceUseCase.java`: bounded external read/slots orchestration.
- `src/main/java/com/cadence/application/results/PdfReviewPreview.java`: explicit private response contract.
- `src/main/java/com/cadence/application/exceptions/DocumentReviewUnavailableException.java`: safe generic failure.
- `src/main/java/com/cadence/presentation/PdfEvidenceReviewController.java`: OWNER GET/no-store.
- `src/main/java/com/cadence/presentation/ApiExceptionHandler.java`: safe503 Problem Details.
- `src/main/resources/openapi/cadence.json`: endpoint, bounded DTO и503 contract.
- `src/test/java/com/cadence/domain/PdfReviewContentTest.java`: graph/text/scan/bytes negative tests.
- `src/test/java/com/cadence/support/PdfReviewApiContract.java`: real registration/extraction fixture и shared HTTP/race tests.
- `src/test/java/com/cadence/security/PdfReviewApiIntegrationTest.java`: H2 suite.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java`: PostgreSQL shared suite и Account lock race.
- `src/test/java/com/cadence/presentation/OpenApiContractTest.java`: DTO schema alignment.
- `scripts/verify_postgres_report.py`: обязательный PDF PostgreSQL suite без skipped tests.
- `scripts/test_verify_postgres_report.py`: missing/skipped/failed PDF report даёт failure.
- `docs/adr/0017-pdf-review-eligibility.md`: decision/threat model/alternatives.
- `docs/pdf-review-api.md`: HTTP behavior и operational limits.
- `DEVELOPMENT_PLAN.md`, `DEVELOPMENT_PLAN_RU.md`: этап3 PARTIAL, конкретный completed slice/remaining scope.
- `docs/verification.md`, `docs/issue-sync.md`: actual qualification и external sync status.
- `docs/issue-121-pdf-review-slice.md`: этот отчёт.

## Runtime Flow

```text
GET + authenticated principal
    ↓ Controller / OWNER / actual actor scope
    ↓ nonblocking process slot
    ↓ capture transaction: Account → Workspace → Brand → Evidence → Quarantine
    ↓ domain validates artifact + original/latest CLEAN + ledger
    ↓ transaction commits / private storage read / size + header + SHA-256
    ↓ fresh finalize transaction / same graph + rights + deadline
    ↓ provenance DTO + bounded plain text / no-store
    ↓ finally releases slot
```

## Тесты

Фактически выполнены:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewContentTest,PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-verification-20261008 -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\var\pdf-review-startup.ps1
git diff --check
```

Final full run BUILD SUCCESS `2026-10-13T23:48:07+01:00`,9:53min:
112 suites/982 tests,0 failures/errors,3 Windows filesystem skips;979 executed pass.
Required PostgreSQL (включая новый suite), MinIO/ClamAV/renderer/full provider pipeline
reports PASS без skips/failures. Domain7/7,H2PDF18/18,PGPDF19/19,OpenAPI3/3.
JaCoCo LINE5696covered/321missed =94.67%,all coverage gates PASS;Spotless/compilation/JAR PASS.
Python10/10,isolated renderer29/29,Linux filesystem35/35 без skips закрывают Windows limitations.
Packaged local JAR:health200,anonymous PDF review GET401; helper остановил только свой process.
Startup helper и raw logs находятся в ignored `var/`; local profile не квалифицирует production.

Первый targeted run обнаружил renderer fixture version вне existing allowlist; исправлена
на `isolated-v1`. Второй обнаружил nested Mockito stubbing; fixture исправлен без изменения
assertions/production validators. Повторный targeted run:45 tests,0 failures/errors/skips.
Первый full run:982 tests,0 failures,1 error,3 skips; ошибка нового rescan fixture была caused by
Java Clock искусственно +1s, тогда как SQL guard запрещает scannedAt > clock_timestamp().
Fixture исправлен на actual time, assertions/DB guard сохранены. Повторный полный run выше
включает fresh CLEAN rescan и repeated failure slot release и прошёл без ошибок.
Python tests: первая sandbox попытка получила TemporaryDirectory permission errors; штатный
повтор вне sandbox прошёл10/10. Это ошибка среды, не ослабление report gates.
Warnings: JVM bootstrap classpath/CDS warning и Hikari validation/connection warnings
после остановки PostgreSQL containers в старых cached test contexts; final reports/gates PASS.
Document links проверены для новых API/ADR/report и обоих plans, missing links0;
Flyway migration diff пуст. Предыдущие slice runs не выдаются за эту qualification.

## Security Tests и Edge Cases

Anonymous401, VIEWER/forged actor403, foreign evidence404, actual membership required;
revocation/archive/source change/latest scan change during read; expired/exact expiry/future/
non-CLEAN scans; legacy/missing artifact; corrupted/missing/oversized/truncated bytes;
blank/malformed/control/oversized text; busy request; two concurrent reads and slot reuse.
Fresh CLEAN rescan сохраняет original artifact scan ID и возвращает отдельный latest scan ID.
TEXT/URL/image inputs rejected; scanner/renderer/model не вызываются во время preview.

## Непроверенные аспекты

Hosted/live storage/Clerk/renderer qualification, real providers, per-user gateway rate limits,
privacy/retention, distributed concurrency budgets и fresh OSV/SAST/container scans не проверены
этим local slice. Process semaphore не является cluster-wide quota. Deadline не прерывает
произвольный зависший adapter; production adapters должны иметь собственные transport timeouts.
Storage deletion после read/finalize не предотвращается DB locks. Future consumer обязан
recheck prerequisites. PDF scan/header/checksum не доказывают semantic truth или полную безопасность.

## Что мне важно понять

Результат проверки имеет срок и контекст: доступ может измениться во время чтения файла.
Поэтому transaction удерживает locks только при проверках, а затем backend проверяет всё снова.
Checksum доказывает неизменность bytes/text, но не истинность их содержания. CLEAN означает
конкретное наблюдение scanner, не разрешение использовать document instructions как AI commands.

## Что мне стоит изучить

1. Граница доверия (trust boundary): stored rows и private bytes проверяются до использования.
2. Происхождение данных (provenance): original scan, latest scan и artifact IDs различаются.
3. Гонка между проверкой и использованием (TOCTOU): finalize закрывает изменения во время I/O.
4. Ограничение параллелизма (bulkhead): два слота ограничивают одновременные bounded reads.
5. Порядок блокировок (lock ordering): Account-first согласуется с access revocation writers.

## Production considerations / Git

Next: persisted PDF review/admission selectors и PDF-aware consumer/approval fences; затем
URL/images/social policy и APPROVED Identity → Strategy. Real adapters#120, AI Jobs/budgets/full
Playbook остаются отдельными scopes. Whole #121/#107 остаются open, push/exact-SHA remote CI
отложены до whole issue; merge/deployment не выполняются.
Slice сохраняется отдельным local commit `feat: preview verified PDF evidence for human review (#121)`.
GitHub progress для нового slice требует отдельного exact payload approval: предыдущий ответ
«Разрешаю оба updates» относится к Identity draft, уже опубликованному в #121/#107.
Причина — ранее полученное требование automatic approval review; новый external write не пытались
выполнить или обойти. Feature commit: `53c77de4e95d48280e1d1a63dc2714aea5161f5b`.
Новый [точный progress draft](issue-121-pdf-review-sync-draft.md) содержит feature SHA и qualification;
пользователь отдельно подтвердил «Разрешаю оба PDF updates». Block добавлен в descriptions
#121/#107; readback подтвердил exact old body + approved block и неизменные title/state/labels.
Оба issues open; external sync blocker снят. Docs-only follow-up добавляет этот draft и уточняет report/issue-sync,
product source не меняется; предыдущий full run не выдаётся за новый запуск tests.

Подтверждённые факты: inspection и выполненные commands/results qualification.
Инженерный вывод: layered checks закрывают перечисленные локально проверенные attack vectors.
Assumptions: trusted runtime backend/DB identity, production transport limits и hosted policy
потребуют отдельной проверки; readiness не является permanent admission или коммерческой qualification.
