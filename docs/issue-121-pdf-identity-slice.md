# Задание 01 — PDF Identity copy, provenance и approval

Дата: 2026-10-14. Baseline: `71cafd6b93ac5d097984e39390c12e78f2d3fcd4`, ветка `mvp`,
чистое дерево, 24 local commits ahead of tracking origin/mvp. Задания 02+ не выполнялись.
Prerequisites сверены: ADR0019, V37, PDF analysis source `d43af8c`, current #121/#107.
Исторические 1053 tests не используются как qualification этого изменения.

## Что было сделано

PDF evidence analysis можно скопировать в Identity DRAFT, прочитать точные исходные
цитаты и утвердить. Изменение APPROVED создаёт новый DRAFT, который наследует original
provenance и не может обойти withdrawal. Whole #121 остаётся IN PROGRESS.

## Как это работает

`IdentityEvidenceUseCase.createFromAnalysis` использует тот же explicit API и authenticated
actor. `EvidenceIdentitySourcePolicy` проверяет original receipt, source graph и citations.
`PdfAnalysisSourcePolicy` собирает immutable physical descriptors из exact admission,
artifact, original/latest scan и quarantine ledger. `IdentitySourceVerification` выполняет
physical reads вне transactions и повторяет capture в fresh locked transaction до mutation.
`BrandIdentityUseCase.approve` использует этот путь и для inherited origin.

Brand.lockVersion должна равняться receipt.brandVersion+1: плюс один — результат успешной
analysis publication. Admission проверяется в исходном receipt context, а не против новой
Brand version. Последующий rename/reanalysis не разрешает использовать старый receipt.
Copy не вызывает model и не меняет original generation attribution.

## Архитектурное решение

[ADR0020](adr/0020-pdf-identity-consumption.md). Application orchestration отделена от
transactional metadata policy и physical storage port. `TransactionTemplate` задаёт две
REQUIRES_NEW/READ_COMMITTED фазы; внешние use cases используют NOT_SUPPORTED, чтобы
suspend возможную caller transaction. Existing method-security annotations сохраняются.
Callbacks private и выполняют scoped access/locks, сначала Account, затем parents/revision.
Final validation и запись выполняются в одной transaction под удерживаемыми locks.

Альтернативы: I/O под DB locks увеличивает блокировки при медленном storage; снятие version
check разрешает stale copy; новый analysis меняет происхождение existing Identity. Выбран
bounded physical witness с повторной validation, цена — дополнительные DB reads/locks.
Новых dependencies и внешних writable полей нет. V38 заменяет только SQL source guard,
не переписывает history. V1–V37 сохранены. Existing runtime grants подходят без расширения.

## Security Review

Threat: BOLA/IDOR, forged actor, устаревший principal после revoke.
Protection: scoped repositories, authenticated actor binding, actual active OWNER/membership
под Account lock в обеих фазах. Frontend не считается границей доверия (trust boundary).
Verification: existing HTTP/direct-bean negatives, PDF foreign receipt и revoke during read.

Threat: corrupt/missing bytes, stale/ERROR scan, expiry, заменённый ledger.
Protection: exact original/latest CLEAN, artifact/job/source checks, immutable receipt expiry,
scoped REFERENCED ledger, size/header/SHA-256 и equality physical descriptors перед commit.
Verification: PDF copy/approval negatives, ledger change during read, PostgreSQL SQL guards.

Threat: withdrawal/source/access race и inherited branch laundering.
Protection: fresh all-source validation в final transaction, same-Brand bounded origin chain,
Account→Workspace→Brand→Identity→Evidence→Quarantine locks; V38 inherited SQL approval guard.
Verification: withdrawal during copy/approval I/O, mixed TEXT/PDF withdrawal, inherited denial,
held PostgreSQL withdrawal против concurrent approval; existing opposite-order races.

Threat: exhaustion, sensitive exposure, injection.
Protection: max5 sources/20000 UTF-8 input, max10MiB PDF read, concurrency2/process, batch30s;
DTO выдаёт только IDs/quote без key/raw PDF, safe exceptions без provider cause, no-store.
Parameterized queries; новые URL/shell/HTML execution paths отсутствуют, model не вызывается.
Verification: capacity release/third-call rejection/deadline tests, API schema/negative tests.
Deadline ограничивает принятие результата; hard interruption зависшего storage adapter не
добавлено. Adapter timeouts и production storage qualification остаются отдельными.

Authentication/CSRF/CORS: existing Bearer JWT resource server, SecurityFilterChain не менялся.
Secrets/logging: новые secrets или logging payloads отсутствуют. Supply chain: dependencies
не добавлены. DB integrity: immutable receipt/quote/history guards и runtime least privilege.

## Runtime Flow

```text
Bearer + DTO → Controller → OWNER/scoped use case
  → transaction 1: actual account/member + parent/revision locks
    → original receipt/source/citation/PDF metadata capture → commit
  → bounded private storage read + size/header/SHA-256 вне DB transaction
  → transaction 2: fresh access + versions/withdrawal/scan/expiry/ledger + exact equality
    → copy DRAFT / approve committed DRAFT → V38 SQL guards → commit → DTO
```

Historical provenance read: actual OWNER/member → bounded original lineage → immutable
admission/quote/artifact/scan IDs + withdrawal flags. Это история, а не текущая eligibility;
expiry/withdrawal не удаляют ранее APPROVED историю. Storage bytes при таком read не выдаются.

## Тесты

Фактически выполненные команды (в основном workspace, кроме явно указанного snapshot):

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply -DskipTests compile
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,IdentityEvidenceApiIntegrationTest,PostgreSqlIdentityEvidenceTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,IdentityEvidenceApiIntegrationTest,PostgreSqlIdentityEvidenceTest,PostgreSqlPdfIdentityMigrationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply test '-Dtest=PdfReviewApiIntegrationTest,PostgreSqlPdfReviewTest,IdentityEvidenceApiIntegrationTest,PostgreSqlIdentityEvidenceTest,PostgreSqlPdfIdentityMigrationTest,IdentitySourceVerificationTest,OpenApiContractTest'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
python -B -m unittest discover -s scripts -p 'test_*.py'
docker build --tag cadence-prompt01-renderer docker/evidence-renderer
docker run --rm --network none --cap-drop ALL --security-opt no-new-privileges --read-only --tmpfs /tmp:rw,noexec,nosuid,nodev,size=128m --tmpfs /dev/mqueue:ro,noexec,nosuid,nodev,size=4k,mode=000 --entrypoint python3 cadence-prompt01-renderer -I -m unittest discover -s /opt/evidence-renderer -p 'test_*.py'
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply
```

`-o` использован после подтверждения доступных Maven artifacts; dependencies не скачивались
и не менялись. Compilation passed. Exploratory focused runs выявили старый PDF-denial test
и nullable OpenAPI representation, затем corrected focused run:172 tests/0 failures/errors/skips.
Nullable schema переведена на существующий anyOf pattern, assertion не ослаблена.
Первый full run:1079 tests/0 failures/errors/3 Windows skips, но **BUILD FAILURE** на JaCoCo:
execution data не совпали с class IDs, whole-bundle coverage0%. `.class` timestamps показывают
перезапись во время tests; конкретный внешний writer не установлен. `.classpath` использует
ту же target/classes directory. Это не считается успешным full gate.

После review добавлены OpenAPI503 и HTTP503 assertions. Повторный full gate выполняется
в `var/prompt01-verification`: копия tracked/new source без IDE project metadata, `.env`,
секретов и прежнего target. POM/build/source неизменны:464 SHA-256 hashes сверяются до/после,
302 compiled class hashes проверяют отсутствие перезаписи. Пользовательские IDE settings
и workspace branch не менялись. Команда из cwd snapshot та же:

```powershell
.\mvnw.cmd -o --batch-mode --no-transfer-progress spotless:apply clean verify
```

Final isolated gate: **BUILD SUCCESS**, `2026-10-14T04:01:26+01:00`, 9:32min.
118 suites /1079 tests,0 failures/errors,3 Windows filesystem skips;1076 executed pass.
Все3 skipped symlink cases FileSystemEvidenceObjectStorageTest покрыты отдельным Linux35/35.
PDF H2 API57/57, PostgreSQL68/68, V37→V38 migrations2/2, verification capacity/deadline2/2,
OpenAPI3/3, existing TEXT Identity H2API17/17 и PostgreSQL24/24 passed.
JaCoCo LINE5984 covered/325 missed=94.85%; compilation/Spotless/JAR/coverage gates passed.
Все464 source/build SHA-256 совпали между workspace и snapshot после run;302 compiled
class hashes неизменны на протяжении tests. Root target с невалидным coverage не использован
для итоговой qualification или packaged startup.

После/в ходе завершающей verification из cwd `var/prompt01-verification` выполнены:

```powershell
python -B scripts/verify_postgres_report.py
python -B scripts/verify_evidence_reports.py
.\scripts\run_linux_storage_tests.ps1
& 'C:\Users\<user>\eclipse-workspace\cadence\var\prompt01-startup.ps1'
```

Mandatory PostgreSQL/MinIO/ClamAV/renderer/full evidence pipeline reports PASS без required
skips/failures. Packaged isolated JAR: local health200, anonymous PDF preview GET401,
Identity copyPOST/approvalPOST/provenanceGET401; тестовый процесс остановлен. PostgreSQL
V38/DI startup проверен integration contexts; packaged startup использует isolated local H2,
не выдаётся за production deployment.

Warnings: существующие deprecated API в storage/OpenAPI tests, unchecked test operations,
JVM CDS/Mockito bootstrap warning и Hikari closed Testcontainers connections во время
завершения test contexts. Final JaCoCo class mismatch отсутствует. Assertions, required suites
и coverage thresholds не ослаблялись. Local logs/report artifacts остаются в ignored var.

Python report tests10/10; freshly built renderer29/29. Linux filesystem35/35 без skips
выполнен из snapshot через `.\scripts\run_linux_storage_tests.ps1` на тех же class files.


## Security Tests

Positive PDF copy/read/direct/inherited approval; exact IDs/quotes; anonymous/reserved roles,
foreign/missing scope/actor, revoked account/member; missing/corrupt bytes; expired/ERROR scan;
withdrawal во время copy и approval; mixed-source withdrawal; inherited withdrawal denial;
changed ledger during read; SQL bypass denial и runtime approval; bounded capacity/deadline.

## Edge Cases

Brand+1 publication context разрешён, дальнейший Brand change запрещён. Manual/declared/TEXT
история не получает PDF IDs: поля nullable. APPROVED edit сохраняет origin через lineage,
не создавая новый receipt. Historical read после withdrawal разрешён, approval нового branch
отклонён. External I/O проверяется test spy на отсутствие actual transaction.

## Непроверенные аспекты

Live AI/Clerk credentials, production IAM/TLS/sandbox/retention, distributed quotas, свежий
OSV/SAST/container scan, load и rollout/recovery на production объёме не квалифицированы.
Physical witness не блокирует rogue storage deletion после read; нужны storage retention/IAM
и operational validation. Это не distributed transaction между PostgreSQL и object storage.
Model output quality не измерялась, fixture generation явно не live AI. Remote CI не запускался.

## Что мне важно понять

Проверка до I/O недостаточна: пока storage читался, могли отозвать доступ или admission.
Повторная проверка под locks не позволяет записать результат на основании такого stale
состояния. Immutable provenance объясняет происхождение; она не гарантирует актуальность
источника и не превращает manual edits в model output.

## Что мне стоит изучить

1. Проверка и использование в разные моменты (TOCTOU): consumer повторяет metadata guards
   после physical I/O, чтобы source change не прошёл незамеченным.
2. Оптимистическая версия (optimistic locking): expectedVersion не даёт подтвердить чужое
   параллельное изменение; publication lineage дополнительно связывает analysis с Brand.
3. Неизменяемое происхождение (immutable provenance): admission/scan/quote links сохраняют
   исходное доказательство, branch не переписывает его.
4. Ограничение параллелизма (bulkhead): semaphore ограничивает дорогие reads и освобождается
   в finally даже после failure; это process limit, не глобальная пользовательская quota.
5. Эшелонированная защита (defense in depth): application проверяет bytes/access, SQL защищает
   metadata/history даже при обходе обычного application path.

## Production considerations

#120/live providers, privacy/retention, hosting IAM/TLS и production qualification отдельно.
Approved Identity→Strategy и other source policy остаются future scope; этот commit не
завершает #121 и не разрешает push/merge/deployment. Нет production-readiness claim.

## Факты, выводы и assumptions

Подтверждённые факты: actual implementation/inspection и перечисленные ниже final runs.
Инженерный вывод: повторная locked validation закрывает протестированные source/access races,
а immutable lineage не позволяет обойти withdrawal через approved branch.
Assumptions: production storage выполняет bounded read/timeout contract, IAM защищает objects;
реальная инфраструктура и live providers требуют отдельного подтверждения.

## Изменённые файлы

- `DEVELOPMENT_PLAN.md`: актуальный completed/remaining scope задания 01.
- `DEVELOPMENT_PLAN_RU.md`: синхронное русское состояние плана.
- `docs/adr/0016-evidence-identity-provenance.md`: ссылка на superseding PDF consumer decision.
- `docs/adr/0019-pdf-evidence-analysis.md`: явное обновление исторического ограничения TEXT-only.
- `docs/adr/0020-pdf-identity-consumption.md`: publication lineage, physical/fresh transaction contract, threat model и trade-offs.
- `docs/brand-identity-api.md`: PDF copy/approval/read semantics, nullable source IDs и errors.
- `docs/evidence-analysis-api.md`: actual PDF Identity consumer support.
- `docs/issue-121-pdf-identity-slice.md`: этот qualification/technical report.
- `docs/mvp-prompt-progress.md`: ledger только задания 01.
- `docs/issue-sync.md`: local qualification и remaining #121 без external updates.
- `docs/verification.md`: actual isolated gate, source equivalence и startup evidence.
- `docs/pdf-review-api.md`: связь PDF receipt с Identity consumer.
- `scripts/test_verify_postgres_report.py`: обязательный migration report в gate fixtures.
- `scripts/verify_postgres_report.py`: новый PostgreSqlPdfIdentityMigrationTest обязателен без skip.
- `src/main/java/com/cadence/application/results/IdentityEvidenceProvenance.java`: nullable immutable artifact/original/latest scan IDs в Citation DTO.
- `src/main/java/com/cadence/application/services/EvidenceIdentitySourcePolicy.java`: PDF source capture и inherited approval validation; exact provenance read.
- `src/main/java/com/cadence/application/services/IdentitySourceVerification.java`: две transactional фазы, bounded private read, equality/deadline/capacity guard.
- `src/main/java/com/cadence/application/services/PdfAnalysisSourcePolicy.java`: physical descriptor с ledger version; original receipt Brand context.
- `src/main/java/com/cadence/application/usecases/BrandIdentityUseCase.java`: approve через physical verification и повторную locked authorization.
- `src/main/java/com/cadence/application/usecases/IdentityEvidenceUseCase.java`: copy через capture/verify/commit без нового analysis.
- `src/main/resources/db/migration/V38__pdf_identity_sources.sql`: PDF metadata branch существующего SQL copy/inherited-approval guard.
- `src/main/resources/openapi/cadence.json`: PDF consumer descriptions и nullable provenance schema.
- `src/test/java/com/cadence/application/IdentitySourceVerificationTest.java`: capacity release/overload и deadline denial.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfIdentityMigrationTest.java`: populated V37 receipts/quotes и APPROVED history preservation.
- `src/test/java/com/cadence/persistence/PostgreSqlPdfReviewTest.java`: restricted runtime approval, inherited SQL denial и actual concurrent withdrawal.
- `src/test/java/com/cadence/persistence/PostgreSqlPersistenceTest.java`: точный migration inventory до V38.
- `src/test/java/com/cadence/support/PdfReviewApiContract.java`: shared H2/PostgreSQL PDF Identity positives и source/access/I/O negatives.

## Git / issues

Qualified scope01 сохраняется одним local commit `feat(identity): qualify PDF evidence copy and approval`.
Commit определяется через `git log -1 -- docs/mvp-prompt-progress.md`; SHA приведён в финальном
ответе. Push/remote CI/issue closure/merge/deployment не выполнялись. #121/#107 IN PROGRESS;
local issue-sync и оба плана отражают оставшийся scope. Задания02+ не начаты.

## Final staged-diff hygiene

Staged diff check обнаружил одну лишнюю пустую строку EOF новой V38, которую unstaged
check ещё не видел как untracked file. После full gate удалена только эта пустая строка;
SQL tokens/statement order не менялись. PostgreSqlPdfIdentityMigrationTest повторно запущен
на окончательном файле в snapshot (`mvnw.cmd -o --batch-mode --no-transfer-progress test
-Dtest=PostgreSqlPdfIdentityMigrationTest`). Итог:2 tests,0 failures/errors/skips, BUILD SUCCESS `2026-10-14T04:05:14+01:00`.
Full qualification выше относится к тому же executable source; отличие final source
manifest — только указанная whitespace normalization V38. V1–V37 неизменны.
